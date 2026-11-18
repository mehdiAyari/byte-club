package com.codingame.game.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A match: what the commands do, the helpers of the held poses, stamina, falls, and the input given to the bots.
 * Everything a turn depends on is in that input, except the opponent's command: a bot that copies this class and
 * Physics simulates the game exactly.
 */
public final class Game {
    public static final int MAX_TURNS = 250;
    /** The fighters start between 1.6 m and 2.4 m apart, an even number of millimetres: the two sides are exact mirror images. */
    public static final int DISTANCE_MIN = 1600, DISTANCE_MAX = 2400;
    public static final int FALL = 20;                 // HP lost by a fall or a ring-out
    public static final int PUSH_FALL = 10;            // HP lost by a fall or a ring-out after a push
    /** A fall costs PUSH_FALL instead of FALL when the fighters touched and no strike scored in the last WINDOW turns: a push, not a knockdown. */
    public static final int WINDOW = 20;
    /** A match where the fighters have not touched for STALE turns stops: the fighter with more HP wins (sinceContact counts these turns). */
    public static final int STALE = 150;
    public static final int FULL = 100000;             // stamina is counted in thousandths of a percent
    public static final int REGEN = 4840, HIT_DRAIN = 1000;
    /** Stamina spent per turn and per percent of strength, for each joint. */
    public static final int[] EFFORT = {4, 4, 4, 6, 8, 8, 6, 8, 8, 4, 4, 4, 4, 4, 4};
    /**
     * Ankle reflex of the held poses: degrees per metre of lean, per metre per second, and the most it adds. The lean is counted from where
     * the weight of a standing fighter rests, up to RREST in front of the middle of its feet (0 once fully ducked).
     */
    public static final double RKP = 60, RKD = 40, RMAX = 15, RREST = 0.035;
    /** Legs that stand: a catching foot already LAND_PAST past the weight point goes no farther, and comes back in only with the feet more than COME_IN apart; a foot higher than FIRM is leaving the floor and carries nothing. */
    public static final double LAND_PAST = 0.06, COME_IN = 0.7, SPLIT = 0.8, SPLIT_V = 0.15, FIRM = 0.035;
    /**
     * Feet that a step leaves more than SPACING + WIDE apart: the second foot is brought in as soon as the weight is past the middle of the
     * feet and the pelvis no longer advances, as in a split. A second foot that hangs in the air while neither the pelvis nor that foot
     * moves faster than REST_V cannot reach its place: it is put down where it is.
     */
    public static final double WIDE = 0.28, REST_V = 0.05, ONTO = 0.02;
    static final int NP = Physics.NP, NJ = Physics.NJ, NS = Physics.NS;

    // ---------- the frame of the input
    /**
     * Each player reads the match as the fighter on the left, facing right: the fighter on the right reads the scene mirrored.
     * The origin is at the top left of the scene: x grows to the right and y downward, in millimetres. The centre of the ring
     * is at x = XOFF and the floor at y = YTOP.
     */
    public static final int XOFF = 5000, YTOP = 3000;
    /** The lines of the input of a turn: one per fighter, one with the points of both fighters, then the two counters. */
    public static final int INPUT_LINES = 4;
    /** The values given for each point on the line of the points: x y vx vy apart angle target strength. */
    public static final int PER_POINT = 8;
    /** For each point, the joint that moves it (-1: none). The input gives that joint's angle and motor with the point. */
    public static final int[] JOINT_AT = new int[NP];
    /** For each point, the striking part at it, as an index in Physics.STRIKERS (-1: none). The input gives its contact counter with the point. */
    public static final int[] STRIKER_AT = new int[NP];
    static {
        Arrays.fill(JOINT_AT, -1);
        Arrays.fill(STRIKER_AT, -1);
        for (int j = 0; j < NJ; j++) JOINT_AT[Physics.JC[j]] = j;
        for (int k = 0; k < Physics.STRIKERS.length; k++) STRIKER_AT[Physics.SH[Physics.STRIKERS[k]][1]] = k;
    }

    public final Physics w = new Physics();
    public final boolean staminaOn, jointsOn;
    public final int startDistance;                    // millimetres between the two pelvises at the start
    public final int[] startX = new int[2];
    public final int[] move = {Moves.STANCE, Moves.STANCE}, frame = {0, 0}, rise = {0, 0}, stamina = {FULL, FULL};
    /** The motors: targets in hundredths of a degree, strengths in percent. */
    public final int[][] target = new int[2][NJ], strength = new int[2][NJ];
    public int turn, sinceContact = WINDOW + 1, sinceHit = WINDOW + 1;
    /** The round kick asked by each fighter for the turn being prepared (cleared by simulate). */
    public final boolean[] roundAsked = new boolean[2];

    // what the last turn did, for the referee and the viewer
    public final boolean[] down = new boolean[2], out = new boolean[2], pushFall = new boolean[2];
    public final int[] hitLoss = new int[2], fallStep = {-1, -1};
    /** The fighter went down this turn because of a split too deep, not because a part of its body touched the floor. */
    public final boolean[] split = new boolean[2];
    public static final double SPLIT_LOW = 0.45;                    // m: the height of the pelvis under which a split is a fall
    public boolean stoodUp;
    public final int[][] look = {{2, 1}, {5, 0}};
    public final String[] message = {"", ""};
    /** What the referee tells about the line of each fighter this turn: what was not played as written, or null. */
    public final String[] warning = new String[2];
    /** When not null, simulate() stores here the points after steps 10, 20, 30 and 40, in millimetres: [4][2 * 38]. */
    public int[][] film;

    public Game(int startDistance, boolean staminaOn, boolean jointsOn) {
        this.startDistance = startDistance; this.staminaOn = staminaOn; this.jointsOn = jointsOn;
        startX[0] = -(startDistance / 2); startX[1] = startDistance - startDistance / 2;
        standUp();
    }

    /** Both fighters stand at their starting places, in STANCE. */
    void standUp() {
        for (int f = 0; f < 2; f++) {
            w.stand(f, startX[f]);
            System.arraycopy(Moves.T[Moves.STANCE][0], 0, target[f], 0, NJ); System.arraycopy(Moves.S[Moves.STANCE][0], 0, strength[f], 0, NJ);
            w.setMotors(f, target[f], strength[f]);
            move[f] = Moves.STANCE; frame[f] = 0; rise[f] = 0; w.roundLeg[f] = false; catching[f] = false;
        }
        Arrays.fill(w.apart, Physics.SEP);
        sinceContact = sinceHit = WINDOW + 1;
    }

    // ---------- commands
    /**
     * Gives the line written by fighter f for this turn. Returns null, or why the line is refused (the player is eliminated).
     * warning[f] then tells what was not played as written, or is null.
     */
    public String apply(int f, String line) {
        Command c = Command.parse(line, turn == 0, jointsOn);
        warning[f] = c.warning;
        if (c.error != null) return c.error;
        if (c.skin >= 0) { look[f][0] = c.skin; look[f][1] = c.hair; }
        message[f] = c.message;
        command(f, c);
        return null;
    }

    /** Sets the motors of fighter f directly, as a JOINTS command does (targets in hundredths of a degree, strengths in percent). */
    public void setRaw(int f, int[] t, int[] s, boolean round) {
        System.arraycopy(t, 0, target[f], 0, NJ); System.arraycopy(s, 0, strength[f], 0, NJ);
        move[f] = Moves.NONE; frame[f] = 0; rise[f] = 0; roundAsked[f] = round; walking[f] = 0;
    }

    public void command(int f, Command c) {
        // read before the move changes: a step still at its first turn is a step that held its place
        int was = move[f];
        afterHold[f] = was != Moves.NONE && was != Moves.STANCE
            && (Moves.HELD[was] || Moves.LUNGE[was] != null || (was == Moves.STEP_FORWARD || was == Moves.STEP_BACK) && frame[f] == 0);
        walking[f] = 0;
        if (c.type == Command.JOINTS) {
            for (int j = 0; j < NJ; j++) {
                if (!c.keepTarget[j]) target[f][j] = c.target[j];
                if (!c.keepStrength[j]) strength[f][j] = c.strength[j];
            }
            move[f] = Moves.NONE; frame[f] = 0; rise[f] = 0; roundAsked[f] = c.round;
            return;
        }
        boolean wait = c.type == Command.WAIT;
        if (wait && move[f] == Moves.NONE) { roundAsked[f] = false; return; }               // the motors stay as they are
        int m = wait ? move[f] : c.move;
        if (Moves.HELD[m]) {
            move[f] = m; frame[f] = 0;
            System.arraycopy(Moves.T[m][0], 0, target[f], 0, NJ); System.arraycopy(Moves.S[m][0], 0, strength[f], 0, NJ);
            // DUCK takes the fighter a quarter of the way down per turn, any other command a quarter of the way back up: rise counts the quarters
            rise[f] = m == Moves.DUCK ? Math.min(Moves.RISE, rise[f] + 1) : Math.max(0, rise[f] - 1);
            double up = 1 - rise[f] / (double) Moves.RISE;
            // ducked, the hips go back as the trunk leans forward: the weight stays over the feet
            stand(f, DUCK_X * (1 - up), HIGH[0] + up * (HIGH[1] - HIGH[0]), LEAN[0] + up * (LEAN[1] - LEAN[0]));
            roundAsked[f] = false;
            return;
        }
        if (m != move[f]) { move[f] = m; frame[f] = 0; }
        System.arraycopy(Moves.T[m][frame[f]], 0, target[f], 0, NJ); System.arraycopy(Moves.S[m][frame[f]], 0, strength[f], 0, NJ);
        if (m == Moves.STEP_FORWARD || m == Moves.STEP_BACK) {
            walking[f] = m == Moves.STEP_FORWARD ? 1 : -1;
            poseX[f] = 0; poseH[f] = HIGH[1]; poseLean[f] = LEAN[1];
            strength(f, Physics.LH, STEP_S); strength(f, Physics.RH, STEP_S);
        } else if (Moves.LUNGE[m] != null) {
            // a punch: the feet stay where they are, the legs carry the pelvis and the trunk into the punch; thrown from a duck, the
            // punch stays as low as the fighter is, which rises a quarter of the way per turn
            double[] l = Moves.LUNGE[m][frame[f]];
            double up = 1 - Math.max(0, rise[f] - 1) / (double) Moves.RISE;
            if (up == 1) stand(f, l[0], l[1], l[2]);
            else stand(f, DUCK_X + up * (l[0] - DUCK_X), HIGH[0] + up * (l[1] - HIGH[0]), LEAN[0] + up * (l[2] - LEAN[0]));
        }
        // a punch rises a quarter of the way per turn from a duck; a step, a kick or a jump stands the fighter up
        if (Moves.LUNGE[m] == null) rise[f] = 0;
        else if (rise[f] > 0) rise[f]--;
        roundAsked[f] = Moves.ROUND[m];
    }

    /** The legs of a fighter that stands: they hold the pelvis x in front of its place between the feet and h above them, the trunk leaning `lean` degrees. */
    void stand(int f, double x, double h, double lean) {
        poseX[f] = x; poseH[f] = h; poseLean[f] = lean;
        walking[f] = 2;
        aim(f);
    }

    /** Aims the legs at the pose asked, with the ankle reflex: both ankles lean against where the weight is going. The legs then hold. */
    void aim(int f) {
        stance(f);
        strength(f, Physics.LH, STEP_S); strength(f, Physics.RH, STEP_S);
        double r = reflex(f);
        target[f][Physics.LA] = clampC(target[f][Physics.LA] / 100.0 + r, Physics.LA); target[f][Physics.RA] = clampC(target[f][Physics.RA] / 100.0 + r, Physics.RA);
    }

    /** The pose the legs hold while the fighter stands: the pelvis poseX in front of its place between the feet, poseH above them, the trunk leaning poseLean. */
    final double[] poseX = new double[2], poseH = {HIGH[1], HIGH[1]}, poseLean = {LEAN[1], LEAN[1]};
    /** What the last turn did: a fighter that stands and was about to tip over began a step to stay on its feet (-1 a step back, 1 a step forward). */
    public final int[] stepDir = new int[2];
    final boolean[] catching = new boolean[2];
    final int[] lastCatch = {-99, -99};                // the last turn a step to stay on its feet was under way (only for reporting the steps)         // a step to stay on its feet is under way

    // ---------- the legs
    // A leg is driven by where its foot should be under the pelvis: the three angles follow (two bones of LEG metres, foot flat).
    // Positions are along the floor, in the fighter's own direction (forward is positive); "ankle" is where the ankle is when the foot is flat.
    static final double LEG = 0.45, FOOT = 0.17, REACH = 0.896;
    /** The stance: the lead ankle is LEAD_X in front of the pelvis, the rear ankle SPACING behind it; the pelvis HIGH above the ankles, the torso leaning LEAN degrees. [0] ducked, [1] standing. */
    public static final double SPACING = 0.42, LEAD_X = 0.20;
    static final double[] HIGH = {0.727, 0.850}, LEAN = {30.5, 5.4};
    /** Ducked, the pelvis is DUCK_X in front of its place (behind it); the feet are put back SPACING apart when they are more than TOL from it, DUCK_TOL while ducked. */
    public static final double DUCK_X = -0.08, DUCK_TOL = 0.15;
    // the numbers of a step: its length, how far ahead of itself the pelvis is asked to go at most, how close to the first foot the weight must be going
    // for the second foot to leave, how high a foot is lifted (at most, and per metre left to travel), how hard the body is pushed and brought home,
    // and how far past the edge of the first foot the push aims (a step back aims at the edge itself: aimed short of it, a long walk back runs away)
    static final int D = 0, PUSH = 1, EDGE = 2, LIFT = 3, LIFT_K = 4, G_PUSH = 5, G_HOME = 6, AIM = 7, LAND = 8, REACH_X = 9, SLOW = 10, BRAKE = 11, BOOST = 12, RISE_V = 13, AHEAD = 14;
    public static final double[] FWD = {0.25, 0.08, 0.06, 0.09, 1.0, 1.5, 1.0, -0.03, 0.01, 0.08, 1.0, 0.04, 3.0, 0.15, 0.07}, BACK = {0.25, 0.08, 0.06, 0.09, 1.0, 1.5, 1.0, 0, 0.01, 0.08, 1.0, 0.04, 3.0, 0.15, 0.07}, HOLD = {0, 0.08, 0.06, 0.09, 1.0, 1.5, 1.0, -0.03, 0.01, 0.08, 1.0, 0.04, 3.0, 0.15, 0.07};
    static final int TICK = 4;                         // the legs the engine runs are aimed again every TICK physics steps
    /** What the legs do this turn: 1 a step forward, -1 a step back, 2 standing (the poses and the punches), 0 when they follow the targets of the turn (the kicks, JUMP, JOINTS). */
    public final int[] walking = new int[2];
    /** What the legs did last: 0 settle, 1 the first foot travels, 2 the second foot travels, 3 push. */
    public final int[] stage = new int[2];
    static final int STEP_S = 100;                     // strength of the legs when the engine runs them
    static final int STEP_START = 80, STEP_FIRST = 140;       // the first foot may leave during the first STEP_START physics steps of a step, and travel during the first STEP_FIRST
    static final double HEEL = 0.05, GROUND = 0.045, ANKLE_Y = 0.09, COM_X = 0.06, TOL = 0.06, DOWN = 0.03, STEP_RISE = 0.03, FLOOR = 0.03, CLEAR = 0.02, START_MIN = 0.08, NEAR = 0.04, LATE = 0.10;
    /** The pelvis is never asked more than RISE higher or DROP lower than it is now. */
    static final double RISE = 0.06, DROP = 0.02;
    /**
     * A fighter that stands is about to tip over when its weight is going more than CAP_M past its feet (past the foot on the floor when
     * the other one is in the air): a foot is then put CAP_AHEAD beyond where the weight is going. A foot that travels during a step is
     * put at least CATCH beyond where the weight is going.
     */
    public static final double CAP_M = 0.02, CAP_AHEAD = 0.08, CATCH = 0.08;
    /**
     * A fighter that walks first keeps its balance: for the turn its legs hold its place, as they do in STANCE, and its step starts again
     * from its first turn after. Decided at the start of every turn, from what the input of the turn tells:
     * - where the weight is going (weightX) against the feet: on the first turn of a step, it is less than WALK_INSIDE inside the heel of
     *   the rear foot, or, for STEP_BACK, less than WALK_INSIDE inside the toes of the front foot (a step back lifts the rear foot first:
     *   the weight must be on neither edge); on a later turn, it is more than WALK_PAST beyond the feet on the side opposite to the step
     *   (behind the heel of the rear foot for STEP_FORWARD, ahead of the toes of the front foot for STEP_BACK);
     * - the fighter goes the way it walks faster than WALK_FAST (forward) or WALK_FAST_BACK (backwards), in m/s;
     * - the fighter still goes the other way faster than WALK_AGAINST while its weight is going beyond the foot that stays (a step forward
     *   lifts the foot of the lead leg, a step back the foot of the rear leg, wherever the feet are): behind the heel of the foot of the rear
     *   leg for STEP_FORWARD, ahead of the toes of the foot of the lead leg for STEP_BACK. That step would take away the foot that carries it;
     * - the step follows GUARD, RELAX, DUCK, a punch or a step that held its place, and the legs of a pose now take a step to catch the
     *   balance or a foot is in the air; or, for STEP_FORWARD, the trunk leans more than WALK_LEAN degrees forward. Feet that are only out
     *   of place do not hold the walker: its step puts them back.
     */
    public static final double WALK_PAST = 0.10, WALK_INSIDE = 0.13, WALK_FAST = 1.3, WALK_FAST_BACK = 0.7, WALK_LEAN = 30, WALK_AGAINST = 0.3;
    final boolean[] braked = new boolean[2];           // decided at the start of each turn
    final boolean[] afterHold = new boolean[2];        // the legs held on the turn before (read from the move and its turn when the command is given)
    boolean brakes(int f) {
        int d = walking[f];
        if (d != 1 && d != -1) return false;
        if (d * Physics.FACING[f] * w.comVX(f) > (d > 0 ? WALK_FAST : WALK_FAST_BACK)) return true;
        double xl = ankleX(f, true), xr = ankleX(f, false), xw = weightX(f);
        double behind = Math.min(xl, xr) - HEEL - xw, ahead = xw - Math.max(xl, xr) - FOOT;   // the weight beyond the heel of the rear foot, the toes of the front foot
        if (frame[f] == 0 ? behind > -WALK_INSIDE || d < 0 && ahead > -WALK_INSIDE : (d > 0 ? behind : ahead) > WALK_PAST) return true;
        if (-d * Physics.FACING[f] * w.comVX(f) > WALK_AGAINST && (d > 0 ? xr - HEEL - xw : xw - xl - FOOT) > 0) return true;
        if (!afterHold[f]) return false;
        hold(f);                                       // what the legs of a pose do now
        if (catching[f] || footY(f, true) > GROUND || footY(f, false) > GROUND || d > 0 && leanNow(f) > WALK_LEAN) return true;
        calm[f] = false;
        return false;
    }
    final boolean[] calm = new boolean[2];             // inside a turn: the legs of a settled fighter have been aimed

    double ankleX(int f, boolean lead) { return Physics.FACING[f] * w.px[f * NP + (lead ? Physics.LTOE : Physics.RTOE)] - FOOT; }
    double pelvisX(int f) { return Physics.FACING[f] * w.px[f * NP + Physics.PELVIS]; }
    double pelvisV(int f) { return Physics.FACING[f] * w.vx[f * NP + Physics.PELVIS]; }
    /** Where the weight is going (the capture point), along the floor in the fighter's own direction. */
    public double weightX(int f) { return Physics.FACING[f] * (w.comX(f) + w.comVX(f) * Math.sqrt(w.comY(f) / Physics.G)); }

    /** Sets the three joints of one leg: the pelvis dx in front of that ankle and dy above it, the torso leaning `lean` degrees forward. */
    void leg(int f, boolean lead, double dx, double dy, double lean, int strength) {
        if (dy < 0.3) dy = 0.3;
        double len = Math.sqrt(dx * dx + dy * dy);
        double c = Math.min(len, REACH) / (2 * LEG);
        double half = Physics.atan2(Math.sqrt(1 - c * c), c) * Physics.RAD2DEG, a = Physics.atan2(dx, dy) * Physics.RAD2DEG;
        int j = lead ? Physics.LH : Physics.RH;
        target[f][j] = clampC(lean - (a - half), j);
        target[f][j + 1] = clampC(2 * half, j + 1);
        target[f][j + 2] = clampC(-(a + half), j + 2);
        if (strength > 0) strength(f, j, strength);
    }
    void strength(int f, int j, int v) { strength[f][j] = strength[f][j + 1] = strength[f][j + 2] = v; }
    static int clampC(double deg, int j) { int c = (int) Math.rint(deg * 100); return c < Physics.JMINC[j] ? Physics.JMINC[j] : c > Physics.JMAXC[j] ? Physics.JMAXC[j] : c; }

    /** The legs of a fighter that stands: both feet stay where they are, the pelvis goes to its place between them. */
    void stance(int f) {
        double xl = ankleX(f, true), xr = ankleX(f, false), ln = leanNow(f);
        double half = (xl - xr) / 2, mid = SPACING / 2 - LEAD_X + poseX[f], far = Math.abs(half) + Math.abs(mid), h = poseH[f];
        if (far * far + h * h > REACH * REACH) h = far < REACH ? Math.sqrt(REACH * REACH - far * far) : 0;
        h = Math.min(h, w.py[f * NP + Physics.PELVIS] - ANKLE_Y + STEP_RISE);
        // the trunk is held by the legs that stand: a foot in the air is aimed from where the trunk is now, instead of being swung by it
        leg(f, true, mid - half, h, footY(f, true) > GROUND ? ln : poseLean[f], 0);
        leg(f, false, mid + half, h, footY(f, false) > GROUND ? ln : poseLean[f], 0);
    }

    double footY(int f, boolean lead) {
        int o = f * NP;
        return lead ? Math.min(w.py[o + Physics.LHEEL], w.py[o + Physics.LTOE]) : Math.min(w.py[o + Physics.RHEEL], w.py[o + Physics.RTOE]);
    }
    static double clamp(double v, double m) { return v < -m ? -m : v > m ? m : v; }

    /** How high the pelvis is asked to be above feet at a and b: as high as the stance when the legs reach, never much lower or higher than it is now. */
    double height(int f, double px, double a, double b, double now, double[] n, double high) {
        double far = Math.max(Math.abs(px - a), Math.abs(px - b));
        double top = far < REACH ? Math.sqrt(REACH * REACH - far * far) : 0.3;
        return Math.max(now - DROP, Math.min(top, Math.min(now + RISE - n[RISE_V] * w.vy[f * NP + Physics.PELVIS], high)));
    }

    /** How far the trunk leans forward now, in degrees: the hips are measured from the line between the neck and the pelvis. */
    double leanNow(int f) {
        int o = f * NP;
        return Physics.atan2(Physics.FACING[f] * (w.px[o + Physics.NECK] - w.px[o + Physics.PELVIS]), w.py[o + Physics.NECK] - w.py[o + Physics.PELVIS]) * Physics.RAD2DEG;
    }

    /**
     * The leg of a foot that travels to `place`: it is lifted, carried over, and put down on its place. The foot is aimed from where the pelvis
     * and the trunk are now, a little ahead of where the foot is: it follows a path over the floor whatever the body does meanwhile.
     */
    void swing(int f, boolean lead, int travel, double foot, double y, double place, double p, double hip, double[] n) {
        double left = travel * (place - foot);
        double v = Physics.FACING[f] * w.vx[f * NP + (lead ? Physics.LTOE : Physics.RTOE)];
        double clear = Math.max(0, Math.min(1, (y - FLOOR) / CLEAR));       // 0 on the floor, 1 once the foot has left it
        p += clear * n[AHEAD] * pelvisV(f);                               // the leg answers late: in the air it is aimed from where the pelvis will be
        if (left <= DOWN) { leg(f, lead, p - foot + clamp(n[BRAKE] * v, n[REACH_X]), hip + n[LAND], leanNow(f), STEP_S); return; }
        double lift = Math.min(n[LIFT], n[LIFT_K] * (left - DOWN));
        double reach = n[REACH_X] * (1 + n[BOOST] * clear);                // a foot is pulled harder once it has left the floor
        leg(f, lead, p - foot - clamp(travel * n[SLOW] * left - n[BRAKE] * v, reach), hip - lift, leanNow(f), STEP_S);
    }

    /**
     * The legs of a fighter that steps, decided again every TICK physics steps: forward (dir 1) the lead foot goes first, backwards (dir -1)
     * the rear foot. The first foot is put SPACING + d from the other one while that leg pushes the body after it; then the body is pushed
     * until its weight is going onto the first foot; then the second foot follows, to SPACING from the first; then the fighter settles.
     * Everything is decided from where the feet, the pelvis and the weight are now: a step that was cut short is finished by what comes next.
     * t counts the physics steps since the move began; with d = 0 the same rules put the feet back at SPACING.
     */
    void step(int f, int dir, double d, double[] n, int t, double cap) { step(f, dir, d, n, t, cap, 0); }
    void step(int f, int dir, double d, double[] n, int t, double cap, int toward) {
        double xl = ankleX(f, true), xr = ankleX(f, false), p = pelvisX(f), xi = weightX(f), lean = poseLean[f];
        double now = w.py[f * NP + Physics.PELVIS] - ANKLE_Y;
        boolean leadFirst = dir > 0;
        double first = leadFirst ? xl : xr, second = leadFirst ? xr : xl;
        double y1 = footY(f, leadFirst), y2 = footY(f, !leadFirst);
        boolean up1 = y1 > GROUND, up2 = y2 > GROUND;
        double wide = dir * (first - second) - SPACING;                     // how much further apart than in the stance the feet are
        double edge = leadFirst ? first - HEEL : first + FOOT;             // the edge of the first foot the weight has to reach
        boolean apart = Math.abs(wide) > TOL;
        boolean split = Math.abs(xl - xr) > SPLIT;                          // the feet are farther apart than both legs can reach
        int go;                                                             // 0 settle, 1 the first foot travels, 2 the second foot travels, 3 push, 4 push onto the foot that stays
        boolean capture = !Double.isNaN(cap);
        // a walker whose weight has left the first foot the way it walks takes its next step at once (the body has gone ahead of the feet)
        boolean ahead = !capture && d > 0 && dir * (xi - (leadFirst ? first + FOOT : first - HEEL)) > CAP_M;
        // a walking step that begins with the feet crossed (the first foot is behind the one that stays): the first foot does not leave the floor
        // before the weight point is ONTO past the edge of the foot that stays; until then both legs push the weight there
        double stay = leadFirst ? second - HEEL : second + FOOT;
        boolean onto = !capture && d > 0 && !up1 && !up2 && t < STEP_FIRST && dir * (first - second) < 0 && dir * (xi - stay) < ONTO;
        if (capture) go = 2;
        else if (onto) go = 4;
        else if (!up2 && (ahead || t < STEP_FIRST && (up1 || (d - wide > NEAR && (t < STEP_START || wide > START_MIN))))) go = 1;
        else if (apart) {
            // in a split the pelvis cannot reach the first foot: once the weight is past the middle of the feet and the pelvis no longer advances, the second foot is brought in
            boolean stalled = (split || wide > WIDE) && dir * (xi - (first + second) / 2) >= 0 && dir * pelvisV(f) < SPLIT_V;
            go = !up1 && (stalled || dir * (xi - edge) >= -(n[EDGE] + (up2 || split ? LATE : 0))) ? 2 : 3;
        }
        else go = 0;
        stage[f] = go;
        if (go != 0) calm[f] = false;
        if (go == 1) {
            // the weight follows the foot: it goes from where it rests to the edge of the place of the first foot as that foot travels
            double place = second + dir * (SPACING + d), rest = second + (leadFirst ? SPACING : 0) - LEAD_X + COM_X;
            double done = d > 0 ? Math.max(0, Math.min(1, 1 - dir * (place - first) / d)) : 1;
            // a body that is going faster than the step takes its foot further: the foot is put where the weight is going
            double under = xi + (leadFirst ? HEEL : -FOOT) + dir * CATCH;
            double placed = dir * Math.max(dir * place, dir * under);
            double px = p + clamp(n[G_PUSH] * (rest + done * (place + (leadFirst ? -HEEL : FOOT) + dir * n[AIM] - rest) - xi), n[PUSH]);
            double h = height(f, px, second, second, now, n, 1);                   // the leg that stays takes the load at once
            leg(f, !leadFirst, px - second, h, lean, STEP_S);
            swing(f, leadFirst, dir, first, y1, placed, p, now, n);
        } else if (go == 2) {
            double place = capture ? cap : first - dir * SPACING, home = leadFirst ? xl - LEAD_X : xr + SPACING - LEAD_X;
            double px = capture ? p : p + clamp(n[G_HOME] * (home + COM_X - xi), n[PUSH]);
            double h = height(f, px, first, first, now, n, poseH[f]);
            leg(f, leadFirst, px - first, h, lean, STEP_S);
            // a catching foot travels toward its place, which can be back toward the body (see hold())
            int travel = capture ? (place >= second ? 1 : -1) : wide > 0 ? dir : -dir;
            // the weight has left the first foot on the side the second one would move away from: that foot is put down where it is
            if (!capture && travel != dir && dir * (xi - (leadFirst ? first + FOOT : first - HEEL)) > CAP_M) place = second;
            // a foot that hangs in the air while nothing moves any more cannot reach its place: it is put down where it is
            if (!capture && up2 && Math.abs(pelvisV(f)) < REST_V && Math.abs(w.vx[f * NP + (leadFirst ? Physics.RTOE : Physics.LTOE)]) < REST_V) place = second;
            swing(f, !leadFirst, travel, second, y2, place, p, now, n);
        } else if (go == 4) {
            double px = p + clamp(n[G_PUSH] * (stay + dir * (ONTO + n[AIM]) - xi), n[PUSH]);
            double h = height(f, px, xl, xr, now, n, poseH[f]);
            leg(f, true, px - xl, h, lean, STEP_S); leg(f, false, px - xr, h, lean, STEP_S);
        } else if (go == 3) {
            double px = p + clamp(n[G_PUSH] * (edge + dir * n[AIM] - xi), n[PUSH]);
            // in a split, the leg of the first foot alone carries the pelvis: it does not sink, and the other foot is brought in sooner
            double h = split ? height(f, px, first, first, now, n, poseH[f]) : height(f, px, xl, xr, now, n, poseH[f]), ln = leanNow(f);
            // while the weight is pushed onto the first foot, the other leg holds the trunk, and the foot that receives the weight is pressed on the floor
            double hl = leadFirst ? Math.max(h, now + n[LAND]) : h, hr = leadFirst ? h : Math.max(h, now + n[LAND]);
            leg(f, true, px - xl, hl, leadFirst ? ln : lean, STEP_S); leg(f, false, px - xr, hr, leadFirst ? lean : ln, STEP_S);
        } else if (!calm[f]) {
            // settled on both feet: the legs are aimed once and hold (aiming them again and again would shake them)
            calm[f] = true;
            aim(f);
        }
    }

    /** The legs of a fighter that stands, decided again every TICK physics steps: they hold the pose, put the feet back SPACING apart, and step under the weight when the fighter is about to tip over. */
    void hold(int f) {
        double xl = ankleX(f, true), xr = ankleX(f, false), xi = weightX(f);
        boolean upL = footY(f, true) > GROUND, upR = footY(f, false) > GROUND;
        double dl = Math.max(0, Math.max(xl - HEEL - xi, xi - xl - FOOT)), dr = Math.max(0, Math.max(xr - HEEL - xi, xi - xr - FOOT));
        int dir = upL != upR ? (upL ? -1 : 1) : dl <= dr ? 1 : -1;
        double cap = Double.NaN;
        // about to tip over (toward: 1 forward, -1 backward): a foot goes under where the weight is going, the foot of `dir` stays
        int toward = 0;
        if (upL != upR) {
            // one foot in the air: only the other one carries the fighter. When the weight leaves that foot, the foot in the air goes under
            // the weight, unless its own place in the stance is already further on that side
            double xs = upL ? xr : xl, home = upL ? xs + SPACING : xs - SPACING;
            int s = xi > xs + FOOT + CAP_M ? 1 : xi < xs - HEEL - CAP_M ? -1 : 0;
            // a foot that is itself leaving the floor (a hop) carries nothing: no step is decided on it
            if (footY(f, upR) > FIRM) s = 0;
            if (s != 0 && (s * (home - xs) < 0 || s * (xi - TOL + s * CAP_AHEAD - home) > 0)) { toward = s; dir = upR ? 1 : -1; }
        } else if (!upL) {
            // both feet on the floor: the foot the weight is leaving steps past the other one, which carries the fighter meanwhile
            if (xi > Math.max(xl, xr) + FOOT + CAP_M) { toward = 1; dir = xl >= xr ? 1 : -1; }
            else if (xi < Math.min(xl, xr) - HEEL - CAP_M) { toward = -1; dir = xl >= xr ? -1 : 1; }
        }
        if (toward != 0) cap = xi - TOL + toward * CAP_AHEAD;
        // a foot in the air that is already LAND_PAST past the weight point goes no farther (chasing a body that keeps going makes the step longer
        // and longer): it comes down where it is; with the feet more than COME_IN apart, it comes down closer to the body when the catch point is on
        // that side (the feet come back together). Closer feet are left alone: pulling a foot in under a body that still moves takes its support away
        if (toward != 0 && upL != upR) {
            double xa = upL ? xl : xr, past = toward > 0 ? xa + FOOT - xi : xi - (xa - HEEL);
            if (past >= LAND_PAST) cap = Math.abs((upL ? xr : xl) - xa) <= COME_IN ? xa : toward > 0 ? Math.min(xa, cap) : Math.max(xa, cap);
        }
        boolean capture = toward != 0;
        // a step is reported once, not again while it is under way
        if (capture && !catching[f] && turn - lastCatch[f] > 2) stepDir[f] = toward;
        catching[f] = capture;
        if (capture) lastCatch[f] = turn;
        // the feet are put back SPACING apart when they are more than TOL from it (DUCK_TOL while ducked)
        double wide = xl - xr - SPACING;
        if (!capture && (Math.abs(wide) <= (poseH[f] < HIGH[1] - 0.02 ? DUCK_TOL : TOL))) {
            stage[f] = 0;
            if (!calm[f]) { calm[f] = true; aim(f); }
            return;
        }
        step(f, dir, 0, HOLD, STEP_FIRST, cap, toward);
    }

    /** -1 when the fighter is about to tip over backwards, 1 forwards, 0 when it stands: where its weight is going, against its feet. */
    public int offFeet(int f, double margin) {
        int o = f * NP, s = Physics.FACING[f];
        double xi = s * (w.comX(f) + w.comVX(f) * Math.sqrt(w.comY(f) / Physics.G));
        double back = Math.min(s * w.px[o + Physics.RHEEL], s * w.px[o + Physics.LHEEL]), front = Math.max(s * w.px[o + Physics.LTOE], s * w.px[o + Physics.RTOE]);
        return xi < back - margin ? -1 : xi > front + margin ? 1 : 0;
    }

    /** What the ankle reflex of a held pose adds to both ankle targets, in degrees. */
    public double reflex(int f) {
        int o = f * NP, s = Physics.FACING[f];
        double e = s * (w.comX(f) - (w.px[o + Physics.RHEEL] + w.px[o + Physics.LTOE]) / 2), v = s * w.comVX(f);
        e -= Math.max(0, Math.min(RREST * (1 - rise[f] / (double) Moves.RISE), e));
        return Math.max(-RMAX, Math.min(RMAX, RKP * e + RKD * v));
    }

    // ---------- one turn
    public void simulate() {
        for (int f = 0; f < 2; f++) {
            stepDir[f] = 0;
            if (walking[f] != 2) catching[f] = false;
            if (staminaOn) {
                // a named move costs what its table says; JOINTS costs the strengths asked
                int[] st = move[f] == Moves.NONE ? strength[f] : Moves.S[move[f]][frame[f]];
                int cost = 0;
                for (int j = 0; j < NJ; j++) cost += EFFORT[j] * st[j];
                stamina[f] = Math.max(0, Math.min(FULL, stamina[f] + REGEN - cost));
                w.fatigue[f] = 0.4 + 0.6 * stamina[f] / 100000.0;
            } else w.fatigue[f] = 1;
            w.setMotors(f, target[f], strength[f]);
        }
        int hp0 = w.hp[0], hp1 = w.hp[1];
        calm[0] = calm[1] = false;
        // decided once, from where the fighter is at the start of the turn
        for (int f = 0; f < 2; f++) braked[f] = brakes(f);
        w.beginTurn(roundAsked[0], roundAsked[1]);
        for (int s = 0; s < Physics.STEPS; s++) {
            if (s % TICK == 0) for (int f = 0; f < 2; f++) if (walking[f] != 0) {
                if (walking[f] == 2) hold(f);
                // a walker that first keeps its balance holds its place this turn: its step starts after
                else if (braked[f]) hold(f);
                else { double[] n = walking[f] > 0 ? FWD : BACK; step(f, walking[f], n[D], n, frame[f] * Physics.STEPS + s, Double.NaN); }
                w.setMotors(f, target[f], strength[f]);
            }
            w.step();
            if (film != null && s % 10 == 9) {
                int[] fr = film[s / 10];
                for (int i = 0; i < Physics.N; i++) { fr[2 * i] = (int) Math.rint(w.px[i] * 1000); fr[2 * i + 1] = (int) Math.rint(w.py[i] * 1000); }
            }
        }
        // everything the turn decides is settled now, at its end: first the HP taken by the strikes found during the steps
        for (int f = 0; f < 2; f++) w.hp[f] -= w.damage[f];
        hitLoss[0] = hp0 - w.hp[0]; hitLoss[1] = hp1 - w.hp[1];
        sinceContact = w.contactSteps > 0 ? 0 : Math.min(sinceContact + 1, STALE);
        sinceHit = hitLoss[0] > 0 || hitLoss[1] > 0 ? 0 : Math.min(sinceHit + 1, WINDOW + 1);
        if (staminaOn) for (int f = 0; f < 2; f++) stamina[f] = Math.max(0, stamina[f] - HIT_DRAIN * hitLoss[f]);
        // a fall costs FALL, or PUSH_FALL when the fighters have been pushing each other without anyone landing a hit
        boolean push = sinceContact <= WINDOW && sinceHit > WINDOW;
        stoodUp = false;
        // a split too deep to stand up from is a fall: at the end of the turn, the ankles more than SPLIT apart and the pelvis lower than SPLIT_LOW
        for (int f = 0; f < 2; f++) {
            split[f] = !w.down[f] && !w.out[f] && Math.abs(w.px[f * NP + Physics.LANK] - w.px[f * NP + Physics.RANK]) > SPLIT && w.py[f * NP + Physics.PELVIS] < SPLIT_LOW;
            if (split[f]) { w.down[f] = true; w.downPt[f] = Physics.PELVIS; if (w.fallStep[f] < 0) w.fallStep[f] = Physics.STEPS - 1; }
        }
        for (int f = 0; f < 2; f++) {
            down[f] = w.down[f]; out[f] = w.out[f]; fallStep[f] = w.fallStep[f];
            boolean fell = down[f] || out[f];
            pushFall[f] = fell && push;
            if (fell) w.hp[f] -= push ? PUSH_FALL : FALL;
            stoodUp |= fell;
        }
        if (stoodUp) standUp();
        else {
            w.roundState();
            for (int f = 0; f < 2; f++) if (move[f] != Moves.NONE && !Moves.HELD[move[f]]) {
                if (braked[f]) frame[f] = 0;
                else if (++frame[f] >= Moves.LENGTH[move[f]]) { move[f] = Moves.STANCE; frame[f] = 0; }
            }
        }
        roundAsked[0] = roundAsked[1] = false;
        turn++;
    }

    public boolean over() { return w.hp[0] <= 0 || w.hp[1] <= 0 || turn >= MAX_TURNS || stale(); }
    /** The fighters have not touched for STALE turns: nothing is happening, the match stops. */
    public boolean stale() { return sinceContact >= STALE; }
    /** The winner, or -1 for a draw. */
    public int winner() {
        if (w.hp[0] <= 0 && w.hp[1] <= 0) return -1;
        return w.hp[0] > w.hp[1] ? 0 : w.hp[1] > w.hp[0] ? 1 : -1;
    }
    public int score(int f) { return Math.max(0, w.hp[f]); }

    // ---------- what the bots read
    /** The line of the start: the distance between the two pelvises, the same for both players. */
    public String initLine(int p) { return String.valueOf(startDistance); }

    /**
     * The INPUT_LINES lines given to player p at the start of a turn, in the frame of the input (see XOFF): one line per fighter
     * (p first), then one line with the 38 points (p's 19 points first), PER_POINT values each: position, speed, the contact
     * counter of the striking part at the point, and the angle and motor of the joint that moves the point. Then the two
     * counters of the match.
     */
    public List<String> inputLines(int p) {
        List<String> out = new ArrayList<>(INPUT_LINES);
        int o = 1 - p;
        long s = p == 0 ? 1 : -1;                          // the fighter on the right reads the scene mirrored
        for (int f : new int[]{p, o}) out.add(w.hp[f] + " " + stamina[f] + " " + moveName(f) + " " + frame[f] + " " + rise[f] + " " + (w.roundLeg[f] ? 1 : 0));
        StringBuilder b = new StringBuilder(2 * NP * PER_POINT * 6);
        for (int f : new int[]{p, o}) for (int i = 0; i < NP; i++) {
            int q = f * NP + i, j = JOINT_AT[i], k = STRIKER_AT[i];
            if (b.length() > 0) b.append(' ');
            b.append(XOFF + s * mm(w.px[q])).append(' ').append(YTOP - mm(w.py[q])).append(' ').append(s * mm(w.vx[q])).append(' ').append(-mm(w.vy[q]))
                .append(' ').append(k < 0 ? Physics.SEP : w.apart[f * NS + Physics.STRIKERS[k]]);
            if (j < 0) b.append(" 0.00 0.00 0");
            else b.append(' ').append(deg(angleC(f, j))).append(' ').append(deg(target[f][j])).append(' ').append(strength[f][j]);
        }
        out.add(b.toString());
        out.add(sinceContact + " " + sinceHit);
        return out;
    }

    /** The move a fighter is in, as the input names it: a move of the list, or JOINTS when its motors were set one by one. */
    public String moveName(int f) { return move[f] == Moves.NONE ? "JOINTS" : Moves.NAME[move[f]]; }

    static long mm(double x) { return (long) Math.rint(x * 1000); }
    /** Angle of joint j of fighter f in hundredths of a degree. */
    public int angleC(int f, int j) { return (int) Math.rint(w.angle(f, j) * Physics.RAD2DEG * 100); }
    public long speed(int f, int j) { return (long) Math.rint(w.angSpeed(f, j)); }
    public static String deg(int c) { int a = Math.abs(c); return (c < 0 ? "-" : "") + a / 100 + "." + (a % 100 < 10 ? "0" : "") + a % 100; }
    static int hundredths(String s) {
        boolean neg = s.startsWith("-");
        int dot = s.indexOf('.');
        int v = Integer.parseInt(s.substring(neg ? 1 : 0, dot)) * 100 + Integer.parseInt(s.substring(dot + 1));
        return neg ? -v : v;
    }

    /**
     * Rebuilds the game from what a player read: the line of the start and the INPUT_LINES lines of a turn. The input is the whole
     * state. In the copy, the player is fighter 0, on the left, as the input shows it: give the copy the player's command with
     * apply(0, ...) and the opponent's with apply(1, ...) (or command, or setRaw), and after simulate(), inputLines(0) is exactly
     * what the referee will send next.
     */
    public static Game fromInput(String initLine, List<String> lines, boolean staminaOn, boolean jointsOn) {
        Game g = new Game(Integer.parseInt(initLine.trim()), staminaOn, jointsOn);
        for (int f = 0; f < 2; f++) {
            String[] q = lines.get(f).trim().split(" ");
            g.w.hp[f] = Integer.parseInt(q[0]); g.stamina[f] = Integer.parseInt(q[1]);
            g.move[f] = q[2].equals("JOINTS") ? Moves.NONE : Moves.index(q[2]); g.frame[f] = Integer.parseInt(q[3]);
            g.rise[f] = Integer.parseInt(q[4]); g.w.roundLeg[f] = q[5].equals("1");
        }
        String[] q = lines.get(2).trim().split(" ");
        for (int f = 0; f < 2; f++) for (int i = 0; i < NP; i++) {
            int idx = f * NP + i, j = JOINT_AT[i], k = STRIKER_AT[i], b = PER_POINT * idx;
            g.w.px[idx] = (Integer.parseInt(q[b]) - XOFF) / 1000.0; g.w.py[idx] = (YTOP - Integer.parseInt(q[b + 1])) / 1000.0;
            g.w.vx[idx] = Integer.parseInt(q[b + 2]) / 1000.0; g.w.vy[idx] = -Integer.parseInt(q[b + 3]) / 1000.0;
            if (k >= 0) g.w.apart[f * NS + Physics.STRIKERS[k]] = Integer.parseInt(q[b + 4]);
            if (j >= 0) { g.target[f][j] = hundredths(q[b + 6]); g.strength[f][j] = Integer.parseInt(q[b + 7]); }
        }
        q = lines.get(3).trim().split(" ");
        g.sinceContact = Integer.parseInt(q[0]); g.sinceHit = Integer.parseInt(q[1]);
        return g;
    }

    /** Copies another game (for bots that simulate ahead). The film is not copied. */
    public void copyFrom(Game g) {
        w.copyFrom(g.w);
        for (int f = 0; f < 2; f++) {
            move[f] = g.move[f]; frame[f] = g.frame[f]; rise[f] = g.rise[f]; stamina[f] = g.stamina[f];
            System.arraycopy(g.target[f], 0, target[f], 0, NJ); System.arraycopy(g.strength[f], 0, strength[f], 0, NJ);
            roundAsked[f] = g.roundAsked[f];
            walking[f] = g.walking[f]; poseX[f] = g.poseX[f]; poseH[f] = g.poseH[f]; poseLean[f] = g.poseLean[f]; stepDir[f] = g.stepDir[f]; lastCatch[f] = g.lastCatch[f]; catching[f] = g.catching[f]; afterHold[f] = g.afterHold[f];
            down[f] = g.down[f]; out[f] = g.out[f]; pushFall[f] = g.pushFall[f]; split[f] = g.split[f]; hitLoss[f] = g.hitLoss[f]; fallStep[f] = g.fallStep[f];
        }
        turn = g.turn; sinceContact = g.sinceContact; sinceHit = g.sinceHit; stoodUp = g.stoodUp;
    }
}

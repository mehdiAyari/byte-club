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
    /** A fall is free when the fighters touched and no strike scored in the last WINDOW turns: a push, not a knockdown. */
    public static final int WINDOW = 20;
    public static final int FULL = 100000;             // stamina is counted in thousandths of a percent
    public static final int REGEN = 3500, HIT_DRAIN = 1000;
    /** Stamina spent per turn and per percent of strength, for each joint. */
    public static final int[] EFFORT = {2, 2, 2, 3, 4, 4, 3, 4, 4, 4, 4, 4, 4, 4, 4};
    /** Ankle reflex of the held poses: degrees per metre of lean, per metre per second, and the most it adds. */
    public static final double RKP = 60, RKD = 40, RMAX = 15;
    static final int NP = Physics.NP, NJ = Physics.NJ, NS = Physics.NS;

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
    public final boolean[] down = new boolean[2], out = new boolean[2], freeFall = new boolean[2];
    public final int[] hitLoss = new int[2], fallStep = {-1, -1};
    public boolean stoodUp;
    public final int[][] look = {{2, 1}, {5, 0}};
    public final String[] message = {"", ""};
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
    /** Gives the line written by fighter f for this turn. Returns null, or why the line is refused. */
    public String apply(int f, String line) {
        Command c = Command.parse(line, turn == 0, jointsOn);
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
    // and how far past the edge of the first foot the push aims
    static final int D = 0, PUSH = 1, EDGE = 2, LIFT = 3, LIFT_K = 4, G_PUSH = 5, G_HOME = 6, AIM = 7, LAND = 8, REACH_X = 9, SLOW = 10, BRAKE = 11, BOOST = 12, RISE_V = 13, AHEAD = 14;
    public static final double[] FWD = {0.25, 0.08, 0.06, 0.09, 1.0, 1.5, 1.0, -0.03, 0.01, 0.08, 1.0, 0.04, 3.0, 0.15, 0.07}, BACK = {0.25, 0.08, 0.06, 0.09, 1.0, 1.5, 1.0, -0.03, 0.01, 0.08, 1.0, 0.04, 3.0, 0.15, 0.07}, HOLD = {0, 0.08, 0.06, 0.09, 1.0, 1.5, 1.0, -0.03, 0.01, 0.08, 1.0, 0.04, 3.0, 0.15, 0.07};
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
     * A fighter that stands is about to tip over when its weight is going more than CAP_M past its feet: the foot on that side is then put
     * CAP_AHEAD beyond where the weight is going. A foot that travels during a step is put at least CATCH beyond where the weight is going.
     */
    public static final double CAP_M = 0.02, CAP_AHEAD = 0.08, CATCH = 0.08;
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
    void step(int f, int dir, double d, double[] n, int t, double cap) {
        double xl = ankleX(f, true), xr = ankleX(f, false), p = pelvisX(f), xi = weightX(f), lean = poseLean[f];
        double now = w.py[f * NP + Physics.PELVIS] - ANKLE_Y;
        boolean leadFirst = dir > 0;
        double first = leadFirst ? xl : xr, second = leadFirst ? xr : xl;
        double y1 = footY(f, leadFirst), y2 = footY(f, !leadFirst);
        boolean up1 = y1 > GROUND, up2 = y2 > GROUND;
        double wide = dir * (first - second) - SPACING;                     // how much further apart than in the stance the feet are
        double edge = leadFirst ? first - HEEL : first + FOOT;             // the edge of the first foot the weight has to reach
        boolean apart = Math.abs(wide) > TOL;
        int go;                                                             // 0 settle, 1 the first foot travels, 2 the second foot travels, 3 push
        boolean capture = !Double.isNaN(cap);
        if (capture) go = 2;
        else if (!up2 && t < STEP_FIRST && (up1 || (d - wide > NEAR && (t < STEP_START || wide > START_MIN)))) go = 1;
        else if (apart) go = !up1 && dir * (xi - edge) >= -(n[EDGE] + (up2 ? LATE : 0)) ? 2 : 3;
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
            swing(f, !leadFirst, capture ? -dir : wide > 0 ? dir : -dir, second, y2, place, p, now, n);
        } else if (go == 3) {
            double px = p + clamp(n[G_PUSH] * (edge + dir * n[AIM] - xi), n[PUSH]);
            double h = height(f, px, xl, xr, now, n, poseH[f]), ln = leanNow(f);
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
        // about to tip over: the foot on that side goes under where the weight is going
        if (xi > Math.max(xl, xr) + FOOT + CAP_M && !upR) { dir = -1; cap = xi - TOL + CAP_AHEAD; }
        else if (xi < Math.min(xl, xr) - HEEL - CAP_M && !upL) { dir = 1; cap = xi - TOL - CAP_AHEAD; }
        boolean capture = !Double.isNaN(cap);
        // the foot that travels is the one of the other side of `dir`; a step is reported once, not again while it is under way
        if (capture && !catching[f] && turn - lastCatch[f] > 2) stepDir[f] = -dir;
        catching[f] = capture;
        if (capture) lastCatch[f] = turn;
        // the feet are put back SPACING apart when they are more than TOL from it (DUCK_TOL while ducked)
        double wide = xl - xr - SPACING;
        if (!capture && Math.abs(wide) <= (poseH[f] < HIGH[1] - 0.02 ? DUCK_TOL : TOL)) {
            stage[f] = 0;
            if (!calm[f]) { calm[f] = true; aim(f); }
            return;
        }
        step(f, dir, 0, HOLD, STEP_FIRST, cap);
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
        w.beginTurn(roundAsked[0], roundAsked[1]);
        for (int s = 0; s < Physics.STEPS; s++) {
            if (s % TICK == 0) for (int f = 0; f < 2; f++) if (walking[f] != 0) {
                if (walking[f] == 2) hold(f);
                else { double[] n = walking[f] > 0 ? FWD : BACK; step(f, walking[f], n[D], n, frame[f] * Physics.STEPS + s, Double.NaN); }
                w.setMotors(f, target[f], strength[f]);
            }
            w.step();
            if (film != null && s % 10 == 9) {
                int[] fr = film[s / 10];
                for (int i = 0; i < Physics.N; i++) { fr[2 * i] = (int) Math.rint(w.px[i] * 1000); fr[2 * i + 1] = (int) Math.rint(w.py[i] * 1000); }
            }
        }
        hitLoss[0] = hp0 - w.hp[0]; hitLoss[1] = hp1 - w.hp[1];
        sinceContact = w.contactSteps > 0 ? 0 : Math.min(sinceContact + 1, WINDOW + 1);
        sinceHit = hitLoss[0] > 0 || hitLoss[1] > 0 ? 0 : Math.min(sinceHit + 1, WINDOW + 1);
        if (staminaOn) for (int f = 0; f < 2; f++) stamina[f] = Math.max(0, stamina[f] - HIT_DRAIN * hitLoss[f]);
        // a fall costs FALL, except when the fighters have been pushing each other without anyone landing a hit
        boolean push = sinceContact <= WINDOW && sinceHit > WINDOW;
        stoodUp = false;
        for (int f = 0; f < 2; f++) {
            down[f] = w.down[f]; out[f] = w.out[f]; fallStep[f] = w.fallStep[f];
            boolean fell = down[f] || out[f];
            freeFall[f] = fell && push;
            if (fell && !push) w.hp[f] -= FALL;
            stoodUp |= fell;
        }
        if (stoodUp) standUp();
        else {
            w.roundState();
            for (int f = 0; f < 2; f++) if (move[f] != Moves.NONE && !Moves.HELD[move[f]] && ++frame[f] >= Moves.LENGTH[move[f]]) { move[f] = Moves.STANCE; frame[f] = 0; }
        }
        roundAsked[0] = roundAsked[1] = false;
        turn++;
    }

    public boolean over() { return w.hp[0] <= 0 || w.hp[1] <= 0 || turn >= MAX_TURNS; }
    /** The winner, or -1 for a draw. */
    public int winner() {
        if (w.hp[0] <= 0 && w.hp[1] <= 0) return -1;
        return w.hp[0] > w.hp[1] ? 0 : w.hp[1] > w.hp[0] ? 1 : -1;
    }
    public int score(int f) { return Math.max(0, w.hp[f]); }

    // ---------- what the bots read
    public String initLine(int p) { return p + " " + startDistance; }

    /** The 72 lines given to player p at the start of a turn. */
    public List<String> inputLines(int p) {
        List<String> out = new ArrayList<>(72);
        int o = 1 - p;
        out.add(w.hp[p] + " " + stamina[p] + " " + w.hp[o] + " " + stamina[o]);
        out.add(moveName(p) + " " + frame[p] + " " + moveName(o) + " " + frame[o]);
        for (int g = 0; g < 2; g++) {
            int b = (g == 0 ? p : o) * NP;
            for (int i = b; i < b + NP; i++) out.add(mm(w.px[i]) + " " + mm(w.py[i]) + " " + mm(w.vx[i]) + " " + mm(w.vy[i]));
        }
        for (int j = 0; j < NJ; j++) out.add(deg(angleC(p, j)) + " " + speed(p, j) + " " + deg(target[p][j]) + " " + strength[p][j]);
        for (int j = 0; j < NJ; j++) out.add(deg(angleC(o, j)) + " " + speed(o, j) + " " + deg(target[o][j]) + " " + strength[o][j]);
        out.add(rise[p] + " " + rise[o] + " " + (w.roundLeg[p] ? 1 : 0) + " " + (w.roundLeg[o] ? 1 : 0) + " " + sinceContact + " " + sinceHit);
        StringBuilder sb = new StringBuilder();
        for (int g = 0; g < 2; g++) for (int k = 0; k < Physics.STRIKERS.length; k++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(w.apart[(g == 0 ? p : o) * NS + Physics.STRIKERS[k]]);
        }
        out.add(sb.toString());
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
     * Rebuilds the game from what player p read: the line of the start and the 72 lines of a turn. The input is the whole state:
     * give the copy a command for each fighter (apply, command or setRaw), and simulate() gives exactly what the referee will send next.
     */
    public static Game fromInput(String initLine, List<String> lines, boolean staminaOn, boolean jointsOn) {
        String[] q = initLine.trim().split(" ");
        int p = Integer.parseInt(q[0]), o = 1 - p;
        Game g = new Game(Integer.parseInt(q[1]), staminaOn, jointsOn);
        int n = 0;
        q = lines.get(n++).split(" ");
        g.w.hp[p] = Integer.parseInt(q[0]); g.stamina[p] = Integer.parseInt(q[1]); g.w.hp[o] = Integer.parseInt(q[2]); g.stamina[o] = Integer.parseInt(q[3]);
        q = lines.get(n++).split(" ");
        g.move[p] = q[0].equals("JOINTS") ? Moves.NONE : Moves.index(q[0]); g.frame[p] = Integer.parseInt(q[1]);
        g.move[o] = q[2].equals("JOINTS") ? Moves.NONE : Moves.index(q[2]); g.frame[o] = Integer.parseInt(q[3]);
        for (int k = 0; k < 2; k++) {
            int b = (k == 0 ? p : o) * NP;
            for (int i = b; i < b + NP; i++) {
                q = lines.get(n++).split(" ");
                g.w.px[i] = Integer.parseInt(q[0]) / 1000.0; g.w.py[i] = Integer.parseInt(q[1]) / 1000.0;
                g.w.vx[i] = Integer.parseInt(q[2]) / 1000.0; g.w.vy[i] = Integer.parseInt(q[3]) / 1000.0;
            }
        }
        for (int j = 0; j < NJ; j++) { q = lines.get(n++).split(" "); g.target[p][j] = hundredths(q[2]); g.strength[p][j] = Integer.parseInt(q[3]); }
        for (int j = 0; j < NJ; j++) { q = lines.get(n++).split(" "); g.target[o][j] = hundredths(q[2]); g.strength[o][j] = Integer.parseInt(q[3]); }
        q = lines.get(n++).split(" ");
        g.rise[p] = Integer.parseInt(q[0]); g.rise[o] = Integer.parseInt(q[1]); g.w.roundLeg[p] = q[2].equals("1"); g.w.roundLeg[o] = q[3].equals("1");
        g.sinceContact = Integer.parseInt(q[4]); g.sinceHit = Integer.parseInt(q[5]);
        q = lines.get(n).split(" ");
        for (int k = 0; k < 2; k++) for (int i = 0; i < Physics.STRIKERS.length; i++)
            g.w.apart[(k == 0 ? p : o) * NS + Physics.STRIKERS[i]] = Integer.parseInt(q[k * Physics.STRIKERS.length + i]);
        return g;
    }

    /** Copies another game (for bots that simulate ahead). The film is not copied. */
    public void copyFrom(Game g) {
        w.copyFrom(g.w);
        for (int f = 0; f < 2; f++) {
            move[f] = g.move[f]; frame[f] = g.frame[f]; rise[f] = g.rise[f]; stamina[f] = g.stamina[f];
            System.arraycopy(g.target[f], 0, target[f], 0, NJ); System.arraycopy(g.strength[f], 0, strength[f], 0, NJ);
            roundAsked[f] = g.roundAsked[f];
            walking[f] = g.walking[f]; poseX[f] = g.poseX[f]; poseH[f] = g.poseH[f]; poseLean[f] = g.poseLean[f]; stepDir[f] = g.stepDir[f]; lastCatch[f] = g.lastCatch[f]; catching[f] = g.catching[f];
            down[f] = g.down[f]; out[f] = g.out[f]; freeFall[f] = g.freeFall[f]; hitLoss[f] = g.hitLoss[f]; fallStep[f] = g.fallStep[f];
        }
        turn = g.turn; sinceContact = g.sinceContact; sinceHit = g.sinceHit; stoodUp = g.stoodUp;
    }
}

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Random;

/**
 * The boss of Byte Club. Level 1: closes in, jabs and crosses, guards when a punch comes. Level 2: also steps out of pushes and kicks,
 * and kicks from a little further. Level 3: the same, and rests its arms when its stamina runs low.
 * Against an opponent that stays still in a held pose (a bot that does not fight, like the stub's code), it uses the strike that
 * goes through that pose and never steps forward while going back: such a bot must not draw or win. Such a bot plays every round
 * the same, so after a round that went wrong the boss attacks another way, and once ahead it may keep out of reach (the match stops
 * after 150 turns without contact).
 */
class Player {
    static final int LEVEL = 1;
    static final int NP = 19, HEAD = 0, NECK = 1, PELVIS = 4, LHAND = 7, RHAND = 10, LANK = 12, LHEEL = 13, LTOE = 14, RANK = 16, RHEEL = 17;
    static final double[] MASS = {5, 6, 9, 9, 12, 2, 1.5, 1.2, 2, 1.5, 1.2, 4, 2, 0.5, 1, 4, 2, 0.5, 1};
    static final double RING = 3.0, ANKLE_Y = 0.09;
    /** The frame of the input: the centre of the ring is at x = XOFF, the floor at y = YTOP, and y grows downward (mm). */
    static final int XOFF = 5000, YTOP = 3000;

    final int me = 0, opp = 1;                // the input shows the match from the left: the boss is always fighter 0
    int turn = -1, rest = 0;
    String myMove = "STANCE", oppMove = "STANCE";
    /** Turns in a row the opponent has spent in a held pose (STANCE, GUARD, RELAX, DUCK); the last turn the boss was in a KICK. */
    int oppStill = 0, lastKick = -100;
    int myStamina = 100000, myHp = 100, oppHp = 100;
    /** The start distance (mm), the HP when the round began, and the way the boss attacks a still opponent (0, 1, 2). */
    int startD, roundMy = 100, roundOpp = 100, way = 0;
    double prevD = 9;
    /** How close the boss comes before it kicks a ducked opponent, for each way. */
    // against a still ducked opponent: the step is cut within DUCK_CUT; the strike depends on the distance (OVERHAND, JAB, KICK)
    static final double[] DUCK_CUT = {1.12, 1.10, 1.05};
    static final double DUCK_FAR = 1.30, DUCK_OVERHAND = 1.20, DUCK_JAB = 1.10, DUCK_KICK = 0.95, MOVING = 0.15;
    static final int WAIT_MAX = 12;
    int waited = 0;
    final double[][] x = new double[2][NP], y = new double[2][NP], vx = new double[2][NP];
    final int[] face = {1, -1};
    final Random rnd = new Random(7);

    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        Player p = new Player();
        p.startD = Integer.parseInt(in.readLine().trim());   // the distance at the start: the fighters come back to it after a fall
        while (true) {
            String first = in.readLine();
            if (first == null) return;
            p.read(first, in);
            System.out.println(p.act());
            System.out.flush();
        }
    }

    void read(String first, BufferedReader in) throws Exception {
        turn++;
        String[] q = first.trim().split(" ");                // my line: hp stamina moveName frame rise roundKick
        myHp = Integer.parseInt(q[0]); myStamina = Integer.parseInt(q[1]); myMove = q[2];
        q = in.readLine().trim().split(" ");                 // the opponent's line: its HP and its move
        oppHp = Integer.parseInt(q[0]); oppMove = q[2];
        oppStill = isHeld(oppMove) ? oppStill + 1 : 0;
        q = in.readLine().trim().split(" ");                 // the 38 points on one line, 8 values each: x y vx vy apart angle target strength
        for (int f = 0; f < 2; f++) for (int i = 0; i < NP; i++) {
            int b = 8 * (f * NP + i);
            // back to metres from the centre of the ring, with y up from the floor
            x[f][i] = (Integer.parseInt(q[b]) - XOFF) / 1000.0; y[f][i] = (YTOP - Integer.parseInt(q[b + 1])) / 1000.0; vx[f][i] = Integer.parseInt(q[b + 2]) / 1000.0;
        }
        in.readLine();                                       // sinceContact sinceHit: not used by this boss
    }

    double dist() { return Math.abs(x[me][PELVIS] - x[opp][PELVIS]); }
    double comX(int f) { double m = 0, c = 0; for (int i = 0; i < NP; i++) { m += MASS[i]; c += MASS[i] * x[f][i]; } return c / m; }
    double comVX(int f) { double m = 0, c = 0; for (int i = 0; i < NP; i++) { m += MASS[i]; c += MASS[i] * vx[f][i]; } return c / m; }
    /** How far my weight is in front of the middle of my feet (toward the opponent), in metres. */
    double myLean() { return (comX(me) - (x[me][RHEEL] + x[me][LTOE]) / 2) * face[me]; }
    boolean pushedBack() { return myLean() < -0.10 || comVX(me) * face[me] < -0.30; }
    double roomBehind() { return me == 0 ? x[me][RANK] + RING : RING - x[me][RANK]; }
    /** The fastest of the opponent's hands coming at me, above 1 m: a punch on its way. */
    double oppHandApproach() {
        double best = -9;
        if (y[opp][LHAND] > 1.0) best = Math.max(best, vx[opp][LHAND] * face[opp]);
        if (y[opp][RHAND] > 1.0) best = Math.max(best, vx[opp][RHAND] * face[opp]);
        return best;
    }
    boolean oppFootUp() { return y[opp][LANK] > ANKLE_Y + 0.05 || y[opp][RANK] > ANKLE_Y + 0.05; }
    static boolean isHeld(String m) { return m.equals("STANCE") || m.equals("GUARD") || m.equals("RELAX") || m.equals("DUCK"); }
    boolean held() { return isHeld(myMove); }
    /** Leaning back or moving back: a step forward now would make it fall backward. */
    boolean goingBack() { return myLean() < -0.08 || comVX(me) * face[me] < -0.25; }
    /** An opponent that has not left its held pose for PASSIVE turns is still. */
    static final int PASSIVE = 10;

    String act() {
        double d = dist();
        // back at the start places after a fall: against a still opponent, a round that cost the boss HP (its own fall) or cost the
        // opponent nothing would come again exactly the same, so the boss attacks another way
        if (turn > 0 && Math.abs(d * 1000 - startD) < 3 && prevD * 1000 < startD - 100) {
            if (oppStill >= PASSIVE && (myHp < roundMy || oppHp >= roundOpp)) way++;
            roundMy = myHp; roundOpp = oppHp;
        }
        prevD = d;
        if (myMove.equals("KICK")) lastKick = turn;
        boolean tired = LEVEL >= 3 && myStamina < 30000;
        // pushed back by the opponent (in contact only: a wide stance after its own step is not a push), or far too close
        if (LEVEL >= 2 && ((pushedBack() && d < 0.85) || d < 0.55) && roomBehind() > 0.6 && !myMove.equals("STEP_BACK")) return "STEP_BACK";
        if (!held() && !myMove.equals("JOINTS")) {
            // against a still ducked opponent, the step is cut as soon as the strikes are in reach
            if (oppStill >= PASSIVE && oppMove.equals("DUCK") && myMove.equals("STEP_FORWARD") && myHp <= oppHp && d <= DUCK_CUT[way % 3]) return "GUARD";
            if (!myMove.startsWith("STEP")) rest = LEVEL >= 2 ? 1 : 2;
            return myMove;
        }
        if (oppHandApproach() > 2.5 && d < 1.0) return "GUARD";
        if (LEVEL >= 2 && oppFootUp() && d < 1.2 && roomBehind() > 0.6) return "STEP_BACK";
        if (tired && d > 1.2) return "RELAX";
        if (oppStill >= PASSIVE) return still(d);
        if (rest > 0) { rest--; return tired ? "STANCE" : "GUARD"; }
        // sent back by a strike or a push: no step forward while it still moves back (the feet would go one way and the weight the other)
        // leaning back only: it waits too, unless the opponent walks in (a walker would push a waiting fighter out of the ring)
        boolean sentBack = comVX(me) * face[me] < -0.25 || (myLean() < -0.08 && !oppMove.equals("STEP_FORWARD"));
        if (d > 0.95 && sentBack) return tired ? "STANCE" : "GUARD";
        // two guards touch at about 0.9 m: punch from 0.95 m, and never walk into the opponent
        if (d > 1.05) return "STEP_FORWARD";
        if (d > 0.95) return LEVEL >= 2 && rnd.nextInt(2) == 0 ? "KICK" : "STEP_FORWARD";
        return rnd.nextInt(3) == 0 ? "CROSS" : "JAB";
    }

    /** Against a still opponent: the strike that goes through its pose (the table of the statement). */
    String still(double d) {
        int w = way % 3;
        // ahead on HP: out of reach and wait; a still opponent never comes (against a ducked one at once, the others after a bad round)
        // no step back while the weight still goes forward: the feet would go one way and the weight the other
        String away = d < 1.4 && roomBehind() > 0.6 && !goingBack() && comVX(me) * face[me] < 0.25 && myLean() < 0.10 ? "STEP_BACK" : "STANCE";
        if (oppMove.equals("DUCK")) {
            // a strike into the ducked body throws the striker back: no strike and no step until it stands again
            if (turn - lastKick <= 3 && goingBack()) { rest = 0; return "GUARD"; }
            if (myHp > oppHp) return away;
            if (goingBack() && waited++ < WAIT_MAX) return "GUARD";
            // a ducked head is ahead of its pelvis: the punches reach it from farther than a standing head
            if (d > DUCK_FAR - 0.03 * w) { waited = 0; return "STEP_FORWARD"; }
            // no strike while still moving: a kick thrown on the move ends in a split
            if (Math.abs(comVX(me)) > MOVING && waited++ < WAIT_MAX) return "GUARD";
            waited = 0;
            if (d >= DUCK_OVERHAND) { rest = 0; return "OVERHAND"; }
            if (d >= DUCK_JAB) { rest = 0; return "JAB"; }
            if (d >= DUCK_KICK) { rest = 0; return "KICK"; }
            return roomBehind() > 0.6 ? "STEP_BACK" : "KICK";
        }
        if (way > 0 && myHp > oppHp) return away;
        // going back: no strike and no step until it stands again (a strike into the arms throws it further back, and it falls)
        if (goingBack()) return "GUARD";
        if (rest > 0) { rest--; return "GUARD"; }
        if (d > 1.05) return "STEP_FORWARD";
        if (d > 0.95) return rnd.nextInt(2) == 0 ? "KICK" : "STEP_FORWARD";
        if (w == 1 && d < 0.78 && roomBehind() > 0.6) return "STEP_BACK";
        return w == 2 ? "KICK" : "OVERHAND";
    }
}

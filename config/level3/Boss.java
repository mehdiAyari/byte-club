import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Random;

/**
 * The boss of Byte Club. Level 1: closes in, jabs and crosses, guards when a punch comes. Level 2: also steps out of pushes and kicks,
 * and kicks from a little further. Level 3: the same, and rests its arms when its stamina runs low.
 */
class Player {
    static final int LEVEL = 3;
    static final int NP = 19, NJ = 15, HEAD = 0, NECK = 1, PELVIS = 4, LHAND = 7, RHAND = 10, LANK = 12, LHEEL = 13, LTOE = 14, RANK = 16, RHEEL = 17;
    static final double[] MASS = {5, 6, 9, 9, 12, 2, 1.5, 1.2, 2, 1.5, 1.2, 4, 2, 0.5, 1, 4, 2, 0.5, 1};
    static final double RING = 3.0, ANKLE_Y = 0.09;

    int me, opp, turn = -1, rest = 0;
    String myMove = "STANCE";
    int myStamina = 100000;
    final double[][] x = new double[2][NP], y = new double[2][NP], vx = new double[2][NP];
    final int[] face = {1, -1};
    final Random rnd = new Random(7);

    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        Player p = new Player();
        String[] q = in.readLine().trim().split(" ");
        p.me = Integer.parseInt(q[0]); p.opp = 1 - p.me;
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
        String[] q = first.trim().split(" ");
        myStamina = Integer.parseInt(q[1]);
        q = in.readLine().trim().split(" ");
        myMove = q[0];
        for (int k = 0; k < 2; k++) {
            int f = k == 0 ? me : opp;
            for (int i = 0; i < NP; i++) {
                q = in.readLine().trim().split(" ");
                x[f][i] = Integer.parseInt(q[0]) / 1000.0; y[f][i] = Integer.parseInt(q[1]) / 1000.0; vx[f][i] = Integer.parseInt(q[2]) / 1000.0;
            }
        }
        for (int i = 0; i < 2 * NJ + 2; i++) in.readLine();     // the joints, then the last two lines: not used by this boss
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
    boolean held() { return myMove.equals("STANCE") || myMove.equals("GUARD") || myMove.equals("RELAX") || myMove.equals("DUCK"); }

    String act() {
        double d = dist();
        boolean tired = LEVEL >= 3 && myStamina < 30000;
        // pushed back by the opponent (in contact only: a wide stance after its own step is not a push), or far too close
        if (LEVEL >= 2 && ((pushedBack() && d < 0.85) || d < 0.55) && roomBehind() > 0.6 && !myMove.equals("STEP_BACK")) return "STEP_BACK";
        if (!held() && !myMove.equals("JOINTS")) { if (!myMove.startsWith("STEP")) rest = LEVEL >= 2 ? 1 : 2; return myMove; }
        if (oppHandApproach() > 2.5 && d < 1.0) return "GUARD";
        if (LEVEL >= 2 && oppFootUp() && d < 1.2 && roomBehind() > 0.6) return "STEP_BACK";
        if (tired && d > 1.2) return "RELAX";
        if (rest > 0) { rest--; return tired ? "STANCE" : "GUARD"; }
        // two guards touch at about 0.9 m: punch from 0.95 m, and never walk into the opponent
        if (d > 1.05) return "STEP_FORWARD";
        if (d > 0.95) return LEVEL >= 2 && rnd.nextInt(2) == 0 ? "KICK" : "STEP_FORWARD";
        return rnd.nextInt(3) == 0 ? "CROSS" : "JAB";
    }
}

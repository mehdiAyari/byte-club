package arena;

import com.codingame.game.engine.Physics;
import java.util.List;

/** What a bot reads: the line of the start, then 72 lines per turn. Lengths in metres, speeds in m/s, angles in degrees. */
public final class Obs {
    public static final int NP = Physics.NP, NJ = Physics.NJ;
    public static final int[] FACING = {1, -1};
    public int me, opp, turn = -1, myFrame, oppFrame, startDistance;
    public String myMove = "STANCE", oppMove = "STANCE";
    public final int[] hp = new int[2], stamina = new int[2];              // stamina in thousandths of a percent
    public final double[][] x = new double[2][NP], y = new double[2][NP], vx = new double[2][NP], vy = new double[2][NP];
    public final double[][] ang = new double[2][NJ], angSpeed = new double[2][NJ];
    public final int[] myTarget = new int[NJ], myStrength = new int[NJ], oppTarget = new int[NJ], oppStrength = new int[NJ];   // hundredths of a degree, percent
    public int rise, oppRise, sinceContact, sinceHit;
    public final boolean[] round = new boolean[2];
    public final int[][] apart = new int[2][6];
    public String initLine;
    public List<String> lines;

    public void init(String line) {
        initLine = line;
        String[] q = line.split(" ");
        me = Integer.parseInt(q[0]); opp = 1 - me; startDistance = Integer.parseInt(q[1]);
    }

    public void read(List<String> in) {
        if (in.size() != 72) throw new IllegalStateException("expected 72 lines, got " + in.size());
        lines = in; turn++;
        int n = 0;
        String[] q = in.get(n++).split(" ");
        hp[me] = Integer.parseInt(q[0]); stamina[me] = Integer.parseInt(q[1]); hp[opp] = Integer.parseInt(q[2]); stamina[opp] = Integer.parseInt(q[3]);
        q = in.get(n++).split(" ");
        myMove = q[0]; myFrame = Integer.parseInt(q[1]); oppMove = q[2]; oppFrame = Integer.parseInt(q[3]);
        for (int k = 0; k < 2; k++) {
            int f = k == 0 ? me : opp;
            for (int i = 0; i < NP; i++) {
                q = in.get(n++).split(" ");
                x[f][i] = Integer.parseInt(q[0]) / 1000.0; y[f][i] = Integer.parseInt(q[1]) / 1000.0;
                vx[f][i] = Integer.parseInt(q[2]) / 1000.0; vy[f][i] = Integer.parseInt(q[3]) / 1000.0;
            }
        }
        for (int j = 0; j < NJ; j++) {
            q = in.get(n++).split(" ");
            ang[me][j] = Double.parseDouble(q[0]); angSpeed[me][j] = Integer.parseInt(q[1]);
            myTarget[j] = (int) Math.rint(Double.parseDouble(q[2]) * 100); myStrength[j] = Integer.parseInt(q[3]);
        }
        for (int j = 0; j < NJ; j++) {
            q = in.get(n++).split(" ");
            ang[opp][j] = Double.parseDouble(q[0]); angSpeed[opp][j] = Integer.parseInt(q[1]);
            oppTarget[j] = (int) Math.rint(Double.parseDouble(q[2]) * 100); oppStrength[j] = Integer.parseInt(q[3]);
        }
        q = in.get(n++).split(" ");
        rise = Integer.parseInt(q[0]); oppRise = Integer.parseInt(q[1]); round[me] = q[2].equals("1"); round[opp] = q[3].equals("1");
        sinceContact = Integer.parseInt(q[4]); sinceHit = Integer.parseInt(q[5]);
        q = in.get(n).split(" ");
        for (int k = 0; k < 2; k++) for (int i = 0; i < 6; i++) apart[k == 0 ? me : opp][i] = Integer.parseInt(q[6 * k + i]);
    }

    public double dist() { return Math.abs(x[me][Physics.PELVIS] - x[opp][Physics.PELVIS]); }
    /** Fastest of the opponent's hands coming at me, above 1 m (a punch). */
    public double oppHandApproach() {
        double best = -9;
        if (y[opp][Physics.LHAND] > 1.0) best = Math.max(best, vx[opp][Physics.LHAND] * FACING[opp]);
        if (y[opp][Physics.RHAND] > 1.0) best = Math.max(best, vx[opp][Physics.RHAND] * FACING[opp]);
        return best;
    }
    /** How far the opponent's hand reaches out in front of its pelvis. */
    public double oppHandOut() {
        return Math.max((x[opp][Physics.LHAND] - x[opp][Physics.PELVIS]) * FACING[opp], (x[opp][Physics.RHAND] - x[opp][Physics.PELVIS]) * FACING[opp]);
    }
    public boolean oppFootUp() { return y[opp][Physics.LANK] > Physics.ANKH + 0.05 || y[opp][Physics.RANK] > Physics.ANKH + 0.05; }
    double comX(int f) { double m = 0, c = 0; for (int i = 0; i < NP; i++) { m += Physics.MASS[i]; c += Physics.MASS[i] * x[f][i]; } return c / m; }
    double comVX(int f) { double m = 0, c = 0; for (int i = 0; i < NP; i++) { m += Physics.MASS[i]; c += Physics.MASS[i] * vx[f][i]; } return c / m; }
    /** Weight relative to the middle of the feet (+ = toward the other fighter). */
    public double lean(int f) { return (comX(f) - (x[f][Physics.RHEEL] + x[f][Physics.LTOE]) / 2) * FACING[f]; }
    public double oppLean() { return lean(opp); }
    public double myLean() { return lean(me); }
    public double myComV() { return comVX(me) * FACING[me]; }
    public boolean pushedBack() { return myLean() < -0.10 || myComV() < -0.30; }
    public double roomBehind() { return me == 0 ? x[me][Physics.RANK] + 3.0 : 3.0 - x[me][Physics.RANK]; }
    public boolean running(String m) { return myMove.equals(m); }
    public boolean held() { return myMove.equals("STANCE") || myMove.equals("GUARD") || myMove.equals("RELAX") || myMove.equals("DUCK"); }
    /** In a timed move or a recovery step. */
    public boolean busy() { return !held() && !myMove.equals("JOINTS"); }
    /** What to send to go on with the current move. */
    public String same() { return myMove; }
    public boolean oppHandsLow() { return y[opp][Physics.RHAND] < y[opp][Physics.NECK] - 0.10; }
    public boolean oppGuardUp() { return y[opp][Physics.LHAND] > y[opp][Physics.NECK] - 0.05 && y[opp][Physics.RHAND] > y[opp][Physics.NECK] - 0.05; }
    public boolean oppFootHigh() { return y[opp][Physics.LANK] > 0.9 || y[opp][Physics.RANK] > 0.9; }
}

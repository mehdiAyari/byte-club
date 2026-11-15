package arena;

import com.codingame.game.engine.Game;
import com.codingame.game.engine.Physics;
import java.util.List;

/**
 * What a bot reads: the line of the start, then Game.INPUT_LINES lines per turn. The input shows the match from the left, so the
 * bot is always fighter 0 (me), on the left, facing right. Here, lengths are in metres from the centre of the ring, with y up
 * from the floor (the frame of the engine), speeds in m/s, angles in degrees.
 */
public final class Obs {
    public static final int NP = Physics.NP, NJ = Physics.NJ;
    public static final int[] FACING = {1, -1};
    public final int me = 0, opp = 1;
    /** The real side of the bot, given by the bench when it knows it (the input does not tell it): only the Script bot uses it. */
    public int side;
    public int turn = -1, myFrame, oppFrame, startDistance;
    public String myMove = "STANCE", oppMove = "STANCE";
    public final int[] hp = new int[2], stamina = new int[2];              // stamina in thousandths of a percent
    public final double[][] x = new double[2][NP], y = new double[2][NP], vx = new double[2][NP], vy = new double[2][NP];
    public int rise, oppRise, sinceContact, sinceHit;
    public final boolean[] round = new boolean[2];
    public final int[][] apart = new int[2][6];
    public final double[][] ang = new double[2][NJ];
    public final int[] myTarget = new int[NJ], myStrength = new int[NJ], oppTarget = new int[NJ], oppStrength = new int[NJ];   // hundredths of a degree, percent
    public String initLine;
    public List<String> lines;

    public void init(String line) {
        initLine = line;
        startDistance = Integer.parseInt(line.trim());
    }

    public void read(List<String> in) {
        if (in.size() != Game.INPUT_LINES) throw new IllegalStateException("expected " + Game.INPUT_LINES + " lines, got " + in.size());
        lines = in; turn++;
        int n = 0;
        for (int f = 0; f < 2; f++) {
            String[] q = in.get(n++).split(" ");
            hp[f] = Integer.parseInt(q[0]); stamina[f] = Integer.parseInt(q[1]); round[f] = q[5].equals("1");
            if (f == me) { myMove = q[2]; myFrame = Integer.parseInt(q[3]); rise = Integer.parseInt(q[4]); }
            else { oppMove = q[2]; oppFrame = Integer.parseInt(q[3]); oppRise = Integer.parseInt(q[4]); }
        }
        String[] p = in.get(n++).split(" ");                // the 38 points on one line, Game.PER_POINT values each
        if (p.length != 2 * NP * Game.PER_POINT) throw new IllegalStateException("expected " + 2 * NP * Game.PER_POINT + " values on the line of the points, got " + p.length);
        for (int f = 0; f < 2; f++) for (int i = 0; i < NP; i++) {
            int b = Game.PER_POINT * (f * NP + i), j = Game.JOINT_AT[i], k = Game.STRIKER_AT[i];
            x[f][i] = (Integer.parseInt(p[b]) - Game.XOFF) / 1000.0; y[f][i] = (Game.YTOP - Integer.parseInt(p[b + 1])) / 1000.0;
            vx[f][i] = Integer.parseInt(p[b + 2]) / 1000.0; vy[f][i] = -Integer.parseInt(p[b + 3]) / 1000.0;
            if (k >= 0) apart[f][k] = Integer.parseInt(p[b + 4]);
            if (j >= 0) {
                ang[f][j] = Double.parseDouble(p[b + 5]);
                int t = (int) Math.rint(Double.parseDouble(p[b + 6]) * 100), st = Integer.parseInt(p[b + 7]);
                if (f == me) { myTarget[j] = t; myStrength[j] = st; } else { oppTarget[j] = t; oppStrength[j] = st; }
            }
        }
        String[] q = in.get(n).split(" ");
        sinceContact = Integer.parseInt(q[0]); sinceHit = Integer.parseInt(q[1]);
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

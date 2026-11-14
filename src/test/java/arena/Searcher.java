package arena;

import com.codingame.game.engine.Game;
import com.codingame.game.engine.Moves;
import com.codingame.game.engine.Physics;
import java.util.ArrayList;
import java.util.List;

/**
 * Search bot: each turn it rebuilds the game from what it read, simulates candidate plans (SH turns of joint targets;
 * the opponent is assumed to hold its angles, or to play its own best plan with MODEL 1) and sends the first turn of the best.
 * Name: S:plans:turns:model:balance:defence[:energy]
 */
public final class Searcher extends Bot {
    static final int NJ = Physics.NJ, NP = Physics.NP;
    static final int[] SEEDS = {Moves.STANCE, Moves.GUARD, Moves.DUCK, Moves.JAB, Moves.CROSS, Moves.KICK, Moves.STEP_FORWARD, Moves.STEP_BACK, Moves.HIGH_KICK};
    final int SB, SH, MODEL;
    final double WBAL, WDEF;
    double WEN = 1;
    final boolean staminaOn, jointsOn;
    Game base, sim;
    Plan best, bestOpp;
    public double lastScore;
    public long sims;

    static final class Plan {
        final int[][] t, s; final boolean[] r;
        Plan(int n) { t = new int[n][NJ]; s = new int[n][NJ]; r = new boolean[n]; }
        Plan copy() {
            Plan p = new Plan(r.length);
            for (int i = 0; i < r.length; i++) { System.arraycopy(t[i], 0, p.t[i], 0, NJ); System.arraycopy(s[i], 0, p.s[i], 0, NJ); p.r[i] = r[i]; }
            return p;
        }
    }

    public Searcher(String n, int sb, int sh, int model, double wbal, double wdef, boolean staminaOn, boolean jointsOn) {
        super(n); SB = sb; SH = sh; MODEL = model; WBAL = wbal; WDEF = wdef; this.staminaOn = staminaOn; this.jointsOn = jointsOn;
    }

    /** A named move written as joint targets (what JOINTS can ask: targets inside the joint ranges). */
    Plan fromMove(int m) {
        Plan p = new Plan(SH);
        for (int t = 0; t < SH; t++) {
            boolean in = Moves.HELD[m] || t < Moves.LENGTH[m];
            int mv = in ? m : Moves.STANCE, fr = Moves.HELD[m] || !in ? 0 : t;
            for (int j = 0; j < NJ; j++) {
                p.t[t][j] = Math.max(Physics.JMINC[j], Math.min(Physics.JMAXC[j], Moves.T[mv][fr][j]));
                p.s[t][j] = Moves.S[mv][fr][j];
            }
            p.r[t] = in && Moves.ROUND[m];
        }
        return p;
    }

    /** Plan p for fighter me, plan q for the other one (null: it keeps its motors); score seen from me. */
    double roll(Plan p, Plan q, int me) {
        sim.copyFrom(base);
        int op = 1 - me, hpMe = sim.w.hp[me], hpOp = sim.w.hp[op];
        double sc = 0; long energy = 0; boolean fell = false;
        for (int t = 0; t < SH; t++) {
            sim.setRaw(me, p.t[t], p.s[t], p.r[t]);
            for (int j = 0; j < NJ; j++) energy += Game.EFFORT[j] * p.s[t][j];
            if (q != null) sim.setRaw(op, q.t[t], q.s[t], q.r[t]);
            sim.simulate(); sims++;
            if (sim.down[me] || sim.out[me]) { sc -= WBAL * (30 + 5 * (SH - t)); fell = true; break; }
            if (sim.down[op] || sim.out[op]) { fell = true; break; }
        }
        // HP: hits and paid falls (20 HP) on both sides
        sc += 2.0 * (hpOp - sim.w.hp[op]) - 2.5 * WDEF * (hpMe - sim.w.hp[me]);
        sc -= 0.15 * WEN * energy / 1000.0;
        if (!fell) {
            Physics w = sim.w;
            int o2 = me * NP, s = Physics.FACING[me];
            double xi = s * (w.comX(me) + w.comVX(me) * Math.sqrt(w.comY(me) / Physics.G));
            double back = Math.min(s * w.px[o2 + Physics.RHEEL], s * w.px[o2 + Physics.LHEEL]), front = Math.max(s * w.px[o2 + Physics.LTOE], s * w.px[o2 + Physics.RTOE]);
            sc -= WBAL * 60 * (Math.max(0, back - xi) + Math.max(0, xi - front));
            double d = Math.abs(w.px[me * NP + Physics.PELVIS] - w.px[op * NP + Physics.PELVIS]);
            sc -= 3 * Math.max(0, d - 1.0) + 3 * Math.max(0, 0.6 - d);
        }
        return sc;
    }

    Plan shift(Plan b) {
        Plan p = new Plan(SH);
        for (int t = 0; t < SH; t++) {
            int u = Math.min(SH - 1, t + 1);
            System.arraycopy(b.t[u], 0, p.t[t], 0, NJ); System.arraycopy(b.s[u], 0, p.s[t], 0, NJ); p.r[t] = b.r[u];
        }
        return p;
    }

    Plan mutate(Plan p) {
        Plan q = p.copy();
        int n = 1 + rnd.nextInt(3);
        for (int k = 0; k < n; k++) {
            int t = rnd.nextInt(SH), j = rnd.nextInt(NJ);
            if (rnd.nextInt(4) == 0) q.s[t][j] = Math.max(0, Math.min(100, q.s[t][j] + (int) Math.rint(rnd.nextGaussian() * 30)));
            else q.t[t][j] = Math.max(Physics.JMINC[j], Math.min(Physics.JMAXC[j], q.t[t][j] + (int) Math.rint(rnd.nextGaussian() * 2000)));
            if (rnd.nextBoolean()) for (int u = t + 1; u < SH; u++) { q.t[u][j] = q.t[t][j]; q.s[u][j] = q.s[t][j]; }
        }
        return q;
    }

    @Override public String act(Obs o) {
        base = Game.fromInput(o.initLine, o.lines, staminaOn, jointsOn);
        if (sim == null) { sim = new Game(base.startDistance, staminaOn, jointsOn); sim.w.record = false; }
        int me = o.me, op = o.opp, used = 0;
        Plan q = null;
        if (MODEL == 1) {       // first the opponent's best plan against my last plan, with a third of the rollouts
            Plan mine = best == null ? null : shift(best);
            List<Plan> sd = new ArrayList<Plan>();
            for (int mv : SEEDS) sd.add(fromMove(mv));
            if (bestOpp != null) sd.add(shift(bestOpp));
            double qs = -1e18; q = sd.get(0);
            for (Plan p : sd) { double v = roll(p, mine, op); used++; if (v > qs) { qs = v; q = p; } }
            for (; used < SB / 3; used++) { Plan c = mutate(q); double v = roll(c, mine, op); if (v >= qs) { qs = v; q = c; } }
            bestOpp = q;
        }
        List<Plan> seeds = new ArrayList<Plan>();
        for (int mv : SEEDS) seeds.add(fromMove(mv));
        if (best != null) seeds.add(shift(best));
        Plan b = seeds.get(0); double bs = -1e18;
        for (Plan p : seeds) { double v = roll(p, q, me); used++; if (v > bs) { bs = v; b = p; } }
        for (; used < SB; used++) { Plan c = mutate(b); double v = roll(c, q, me); if (v >= bs) { bs = v; b = c; } }
        best = b; lastScore = bs;
        return line(b.t[0], b.s[0], b.r[0]);
    }

    public static String line(int[] t, int[] s, boolean round) {
        StringBuilder sb = new StringBuilder("JOINTS");
        for (int j = 0; j < NJ; j++) sb.append(' ').append(Game.deg(t[j])).append(' ').append(s[j]);
        if (round) sb.append(" ROUND");
        return sb.toString();
    }
}

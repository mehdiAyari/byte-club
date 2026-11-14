package arena;

import com.codingame.game.engine.Game;
import com.codingame.game.engine.Moves;
import com.codingame.game.engine.Physics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Finds the numbers of the steps (Game.F_*, Game.B_*): plays many sequences of moves and scores how well every step does what it says
 * (the feet travel the length of the step, end SPACING apart, the body stops, nothing happens while the fighter then stands).
 *
 *   Gait eval fwd|back                 scores the numbers that are in Game
 *   Gait tune fwd|back EVALS SEED      searches from them, prints every improvement
 */
public final class Gait {
    static final double FOOT = 0.17;

    static double ankle(Game g, boolean lead) { return g.w.px[lead ? Physics.LTOE : Physics.RTOE] - FOOT; }
    static double middle(Game g) { return (ankle(g, true) + ankle(g, false)) / 2; }
    static boolean airborne(Game g) {
        double[] y = g.w.py;
        return Math.min(Math.min(y[Physics.LHEEL], y[Physics.LTOE]), Math.min(y[Physics.RHEEL], y[Physics.RTOE])) > 0.045;
    }

    /** One item of a sequence: a move played `reps` times (a held pose: `reps` turns); scored when it is the step under test. */
    static final class Item {
        final int move, reps; final boolean scored;
        Item(String m, int reps, boolean scored) { move = Moves.index(m); this.reps = reps; this.scored = scored; }
    }
    static Item it(String m, int reps) { return new Item(m, reps, false); }
    static Item test(String m) { return new Item(m, 1, true); }

    static final class Score {
        double length, spacing, speed, drift, restSpeed; int steps, pauses, falls, recoveries, air;
        double total() {
            int n = Math.max(1, steps), p = Math.max(1, pauses);
            return (4 * length + 4 * spacing + speed) / n + (3 * drift + restSpeed) / p + (10.0 * falls + 2.0 * recoveries + 0.5 * air) / n;
        }
        @Override public String toString() {
            int n = Math.max(1, steps), p = Math.max(1, pauses);
            return String.format(Locale.ROOT, "cost %.4f | per step: length error %.3f m, spacing error %.3f m, speed left %.2f m/s | per pause: drift %.3f m, speed %.2f | falls %d, recoveries %d, turns in the air %d (%d steps)",
                    total(), length / n, spacing / n, speed / n, drift / p, restSpeed / p, falls, recoveries, air, steps);
        }
    }

    static StringBuilder log;
    static void play(int distance, Item[] seq, int dir, double d, Score sc) {
        Game g = new Game(distance, false, true);
        if (log != null) { log.append("\n"); for (Item i : seq) log.append(i.scored ? "S" : Moves.NAME[i.move].substring(0, 2) + i.reps).append(' '); log.append("\n   "); }
        for (Item item : seq) {
            String name = Moves.NAME[item.move];
            boolean held = Moves.HELD[item.move];
            for (int r = 0; r < (held ? 1 : item.reps); r++) {
                int turns = held ? item.reps : Moves.LENGTH[item.move];
                double anchor = ankle(g, dir < 0), mid = middle(g);
                boolean fell = false;
                for (int t = 0; t < turns; t++) {
                    g.apply(0, name); g.apply(1, "STANCE");
                    g.simulate();
                    if (g.stepDir[0] != 0) sc.recoveries++;
                    if (g.stoodUp) { sc.falls++; fell = true; if (log != null) log.append(" FELL(").append(name).append(" t").append(t).append(")"); break; }
                    if (log != null && g.stepDir[0] != 0) log.append(" rec");
                    if (airborne(g)) sc.air++;
                }
                if (fell) continue;
                if (item.scored) {
                    sc.steps++;
                    sc.length += Math.abs(ankle(g, dir < 0) - anchor - dir * d);
                    sc.spacing += Math.abs(ankle(g, true) - ankle(g, false) - Game.SPACING);
                    sc.speed += Math.abs(g.w.comVX(0));
                    if (log != null) log.append(String.format(Locale.ROOT, " %+.2f/%.2f/%+.1f", ankle(g, dir < 0) - anchor, ankle(g, true) - ankle(g, false), g.w.comVX(0)));
                } else if (held) {
                    sc.pauses++;
                    sc.drift += Math.abs(middle(g) - mid);
                    sc.restSpeed += Math.abs(g.w.comVX(0));
                }
            }
        }
    }

    static Item[] seq(Object... parts) {
        List<Item> l = new ArrayList<>();
        for (Object o : parts) { if (o instanceof Item) l.add((Item) o); else for (Item i : (Item[]) o) l.add(i); }
        return l.toArray(new Item[0]);
    }
    /** n times: the step, then `pause` turns of the held pose. */
    static Item[] steps(String step, int n, String pose, int pause) {
        List<Item> l = new ArrayList<>();
        for (int i = 0; i < n; i++) { l.add(test(step)); if (pause > 0) l.add(it(pose, pause)); }
        return l.toArray(new Item[0]);
    }

    static Score eval(String mode) {
        Score sc = new Score();
        boolean fwd = mode.equals("fwd");
        String st = fwd ? "STEP_FORWARD" : "STEP_BACK", other = fwd ? "STEP_BACK" : "STEP_FORWARD";
        int dir = fwd ? 1 : -1, far = fwd ? 4400 : 1600, n = fwd ? 8 : 6;
        double d = fwd ? Game.FWD[0] : Game.BACK[0];
        for (int pause : new int[]{0, 1, 2, 3, 5, 8, 12}) play(far, steps(st, n, "STANCE", pause), dir, d, sc);
        play(far, steps(st, n, "GUARD", 2), dir, d, sc);
        play(far, steps(st, n, "GUARD", 6), dir, d, sc);
        play(far, seq(it("JAB", 1), steps(st, 4, "STANCE", 0), it("STANCE", 6)), dir, d, sc);
        play(far, seq(it("JAB", 2), it("STANCE", 2), steps(st, 4, "STANCE", 2)), dir, d, sc);
        play(far, seq(it("CROSS", 1), it("STANCE", 1), steps(st, 4, "STANCE", 4)), dir, d, sc);
        play(far, seq(it("OVERHAND", 1), steps(st, 3, "GUARD", 3)), dir, d, sc);
        play(far, seq(it("DUCK", 4), steps(st, 4, "STANCE", 3)), dir, d, sc);
        play(far, seq(it("DUCK", 2), it("STANCE", 2), steps(st, 4, "DUCK", 2)), dir, d, sc);
        play(far, seq(it("KICK", 1), it("STANCE", 6), steps(st, 4, "STANCE", 2)), dir, d, sc);
        play(far, seq(it("KICK", 1), steps(st, 4, "STANCE", 6)), dir, d, sc);
        play(far, seq(it("JUMP", 1), it("STANCE", 8), steps(st, 4, "STANCE", 1)), dir, d, sc);
        play(far, seq(it("RELAX", 5), steps(st, 4, "RELAX", 3)), dir, d, sc);
        if (!fwd) {                                 // back and forth, with the forward step already found
            play(far, seq(steps(st, 3, "STANCE", 0), it(other, 3), steps(st, 3, "STANCE", 2), it(other, 2), it("STANCE", 3), steps(st, 2, "STANCE", 6)), dir, d, sc);
            play(far, seq(it(other, 2), steps(st, 4, "STANCE", 1), it(other, 1), steps(st, 2, "GUARD", 4)), dir, d, sc);
        }
        return sc;
    }

    // ---------- the numbers
    static final String[] NAMES = {"length", "push", "edge", "lift", "liftK", "gPush", "gHome", "aim", "land", "reachX", "slow", "brake", "boost", "riseV", "ahead"};
    static final double[] LO = {0, 0.02, 0.00, 0.03, 0.2, 0.3, 0.3, -0.15, -0.02, 0.03, 0.2, 0, 0, 0, 0}, HI = {0, 0.25, 0.20, 0.16, 2.0, 4.0, 4.0, 0.15, 0.04, 0.25, 2.0, 0.12, 3, 0.4, 0.2}, STEP = {0, 0.03, 0.03, 0.02, 0.3, 0.5, 0.5, 0.03, 0.01, 0.03, 0.3, 0.02, 0.5, 0.05, 0.03};
    static double[] get(String mode) { return (mode.equals("fwd") ? Game.FWD : Game.BACK).clone(); }
    static void set(String mode, double[] x) { System.arraycopy(x, 0, mode.equals("fwd") ? Game.FWD : Game.BACK, 0, x.length); }
    static void bounds(String mode, double[] lo, double[] hi, double[] step) {
        System.arraycopy(LO, 0, lo, 0, lo.length); System.arraycopy(HI, 0, hi, 0, hi.length); System.arraycopy(STEP, 0, step, 0, step.length);
    }
    static String show(String mode, double[] x) {
        StringBuilder sb = new StringBuilder(mode.equals("fwd") ? "FWD = {" : "BACK = {");
        for (int i = 0; i < x.length; i++) sb.append(i > 0 ? ", " : "").append(String.format(Locale.ROOT, "%.3f", x[i]));
        return sb + "}";
    }

    public static void main(String[] a) {
        String mode = a[1];
        double[] x = get(mode);
        Score s0 = eval(mode);
        System.out.println("start: " + s0 + "\n  " + show(mode, x));
        if (a[0].equals("detail")) { log = new StringBuilder(); eval(mode); System.out.println(log); return; }
        if (a[0].equals("eval")) return;
        int evals = Integer.parseInt(a[2]);
        Random rnd = new Random(Long.parseLong(a[3]));
        double[] lo = new double[x.length], hi = new double[x.length], step = new double[x.length];
        bounds(mode, lo, hi, step);
        double best = s0.total(), scale = 1;
        long t0 = System.currentTimeMillis();
        for (int e = 0; e < evals; e++) {
            double[] y = x.clone();
            boolean any = false;
            double pr = rnd.nextInt(4) == 0 ? 1.0 : 0.25;
            for (int i = 0; i < y.length; i++) if (step[i] > 0 && rnd.nextDouble() < pr) {
                y[i] = Math.max(lo[i], Math.min(hi[i], y[i] + scale * step[i] * rnd.nextGaussian())); any = true;
            }
            if (!any) continue;
            set(mode, y);
            Score sc = eval(mode);
            if (sc.total() < best) {
                best = sc.total(); x = y; scale = Math.min(2, scale * 1.4);
                System.out.println("eval " + e + " (" + (System.currentTimeMillis() - t0) / 1000 + " s): " + sc + "\n  " + show(mode, x));
            } else scale = Math.max(0.08, scale * 0.97);
            if (e % 200 == 199 && scale < 0.15) scale = 1;       // start again from wide moves
        }
        set(mode, x);
        System.out.println("end: " + eval(mode) + "\n  " + show(mode, x));
    }
}

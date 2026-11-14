package arena;

import com.codingame.game.engine.Game;
import com.codingame.game.engine.Moves;
import com.codingame.game.engine.Physics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Scripted scenes: one rule or one trick at a time, with the trace of what happens. */
public final class Scenes {
    static final int NP = Physics.NP, NJ = Physics.NJ;
    static final String[] STRIKES = {"JAB", "CROSS", "OVERHAND", "KICK", "HIGH_KICK"};

    static String[] fill(int n, String s) { String[] a = new String[n]; Arrays.fill(a, s); return a; }
    static int len(String mv) { int m = Moves.index(mv); return Moves.HELD[m] ? 1 : Moves.LENGTH[m]; }
    static String ev(Arena.Result r, int n) { return Tests.cut(r.evlog.toString(), n); }

    // ---------- one named strike on a held pose
    static void strike(int[] dists) {
        for (String mv : STRIKES) for (String pose : new String[]{"STANCE", "GUARD", "DUCK"}) for (int d : dists) {
            Arena.Opt o = Tests.opt();
            String[] sc = fill(5 + len(mv), mv);
            for (int t = 0; t < 5; t++) sc[t] = "STANCE";
            o.scripts = new String[][]{sc, {}}; o.maxTurns = 30;
            Arena.Result r = Arena.play("Script", "Hold:" + pose, d, 1, o);
            System.out.printf(Locale.ROOT, "  %-9s on %-6s at %.2f m: %d hit(s) for %2d HP, %d stopped by the arms | %s%n", mv, pose, d / 1000.0, r.hits[0], r.hpHit[1], r.blocked[0], ev(r, 400));
        }
    }

    // ---------- both fighters play exactly the same script: the match must be a mirror
    static void push() {
        String[][] pats = {{"STANCE"}, {"GUARD"}, {"STEP_FORWARD"}, {"STEP_FORWARD", "JAB"}, {"STEP_FORWARD", "CROSS"}, {"STEP_FORWARD", "KICK"}, {"STEP_FORWARD", "HIGH_KICK"}, {"JUMP"}, {"DUCK"}};
        for (String[] pat : pats) for (int d : new int[]{700, 800, 1000, 1600, 2000}) {
            String[] sc = new String[Game.MAX_TURNS]; int k = 0;
            for (int t = 0; t < sc.length; ) { String mv = pat[k++ % pat.length]; for (int u = 0, n = len(mv); u < n && t < sc.length; u++) sc[t++] = mv; }
            Arena.Opt o = Tests.opt(); o.scripts = new String[][]{sc, sc};
            Arena.Result r = Arena.play("Script", "Script", d, 1, o);
            System.out.printf(Locale.ROOT, "  both %-22s start %.2f m: %s | falls+outs left %d (free %d), right %d (free %d) | HP lost to hits %d/%d%n", String.join("+", pat), d / 1000.0, Arena.describe(r),
                r.falls[0] + r.outs[0], r.freeFalls[0], r.falls[1] + r.outs[1], r.freeFalls[1], r.hpHit[0], r.hpHit[1]);
        }
    }

    // ---------- left walks into the opponent, goes limp and falls; after the reset it goes limp again at once, far from the opponent
    static void chain() {
        for (int d : new int[]{1600, 2000, 2400}) {
            String[] sc = fill(Game.MAX_TURNS, Bots.ZERO);
            int t = 0, steps = (int) Math.ceil((d / 1000.0 - 0.75) / 0.17);
            for (int k = 0; k < steps; k++) for (int u = 0; u < 5; u++) sc[t++] = "STEP_FORWARD";
            final int walk = t; final StringBuilder sb = new StringBuilder();
            Arena.Opt o = Tests.opt(); o.scripts = new String[][]{sc, fill(Game.MAX_TURNS, "STANCE")};
            o.hook = new Arena.Hook() {
                public void turn(Game g, int tt, String[] cmd) {
                    if (tt < walk - 2 || tt >= walk + 45) return;
                    double gap = 9;
                    for (int i = 0; i < NP; i++) for (int j = 0; j < NP; j++) gap = Math.min(gap, Math.hypot(g.w.px[i] - g.w.px[NP + j], g.w.py[i] - g.w.py[NP + j]));
                    sb.append(String.format(Locale.ROOT, " t%d:%.2f", tt, gap));
                }
            };
            Arena.Result r = Arena.play("Script", "Script", d, 1, o);
            System.out.printf(Locale.ROOT, "  start %.1f m, walks %d turns then limp for the rest of the match: %s%n     events: %s%n     smallest point-to-point gap (m):%s%n", d / 1000.0, walk, Arena.describe(r), ev(r, 420), sb);
        }
    }

    // ---------- one high kick with ROUND asked for the first K frames only
    static void launch() {
        int n = Moves.LENGTH[Moves.HIGH_KICK];
        for (String pose : new String[]{"GUARD", "STANCE", "DUCK"}) for (int d : new int[]{700, 800, 900}) for (int K = 0; K <= n; K++) {
            String[] sc = fill(4 + n, "STANCE");
            for (int k = 0; k < n; k++) sc[4 + k] = Searcher.line(Bots.clampT(Moves.T[Moves.HIGH_KICK][k]), Moves.S[Moves.HIGH_KICK][k], k < K);
            Arena.Opt o = Tests.opt(); o.scripts = new String[][]{sc, {}}; o.maxTurns = 40;
            final double[] top = {0, 0}; final int[] at = {0, 0};
            o.hook = new Arena.Hook() {
                public void turn(Game g, int t, String[] cmd) {
                    if (t > 20 || g.stoodUp) return;
                    for (int f = 0; f < 2; f++) for (int i = f * NP; i < f * NP + NP; i++) { double v = Math.hypot(g.w.vx[i], g.w.vy[i]); if (v > top[f]) { top[f] = v; at[f] = t; } }
                }
            };
            Arena.Result r = Arena.play("Script", "Hold:" + pose, d, 1, o);
            System.out.printf(Locale.ROOT, "  %-6s at %.2f m, ROUND on %2d of %d frames: defender's top speed %4.1f m/s (t%d), kicker's %4.1f m/s | %s%n", pose, d / 1000.0, K, n, top[1], at[1], top[0], ev(r, 300));
        }
    }

    // ---------- stamina of the held poses and of the moves
    static void tire() {
        for (String pose : new String[]{"STANCE", "GUARD", "RELAX", "DUCK"}) for (int d : new int[]{2000, 2180}) {
            final StringBuilder sb = new StringBuilder();
            Arena.Opt o = Tests.opt();
            o.hook = new Arena.Hook() {
                public void turn(Game g, int t, String[] cmd) {
                    if (t % 25 == 24) sb.append(String.format(Locale.ROOT, " t%d:%.0f%% x%+.2f h%.2f", t, g.stamina[0] / 1000.0, g.w.px[Physics.PELVIS], g.w.py[Physics.PELVIS]));
                }
            };
            Arena.Result r = Arena.play("Hold:" + pose, "Idle", d, 1, o);
            System.out.printf(Locale.ROOT, "  holding %-6s alone (start %.2f m), stamina, pelvis x and height:%s%n     -> %s | recovery steps %d | events: %s%n", pose, d / 1000.0, sb, Arena.describe(r), r.recoveries[0], ev(r, 300));
        }
        for (int m = 0; m < Moves.COUNT; m++) {
            int n = Moves.LENGTH[m]; long tot = 0;
            for (int k = 0; k < n; k++) { int c = 0; for (int j = 0; j < NJ; j++) c += Game.EFFORT[j] * Moves.S[m][k][j]; tot += Game.REGEN - c; }
            System.out.printf(Locale.ROOT, "  %-16s %2d turn(s): stamina %+.2f %% in total (%+.2f per turn)%n", Moves.NAME[m], n, tot / 1000.0, tot / 1000.0 / n);
        }
    }

    // ---------- random joint commands with ROUND at random: nothing may explode
    static void chaos(int matches) {
        double worst = 0; String where = ""; long turns = 0; int bad = 0;
        for (int k = 0; k < matches; k++) {
            final Random rnd = new Random(900 + k);
            final int kind = k % 4;
            String[][] sc = new String[2][Game.MAX_TURNS];
            for (int p = 0; p < 2; p++) for (int t = 0; t < Game.MAX_TURNS; t++) {
                StringBuilder sb = new StringBuilder("JOINTS");
                for (int j = 0; j < NJ; j++) {
                    if (kind == 0) sb.append(' ').append(rnd.nextBoolean() ? "-180" : "180").append(' ').append(rnd.nextBoolean() ? "100" : "0");          // bang-bang
                    else if (kind == 1) sb.append(' ').append(String.format(Locale.ROOT, "%.2f", Physics.JMIN[j] + rnd.nextDouble() * (Physics.JMAX[j] - Physics.JMIN[j]))).append(' ').append(rnd.nextInt(101));
                    else if (kind == 2) sb.append(rnd.nextInt(3) == 0 ? " * *" : " " + (rnd.nextInt(361) - 180) + " 100");
                    else sb.append(' ').append(t % 2 == 0 ? Physics.JMIN[j] : Physics.JMAX[j]).append(" 100");                                              // every joint end to end, every turn
                }
                if (rnd.nextInt(kind == 3 ? 2 : 3) == 0) sb.append(" ROUND");
                sc[p][t] = sb.toString();
            }
            Arena.Opt o = Tests.opt(); o.scripts = sc; o.checkSim = o.checkMirror = true;
            final double[] top = {0}; final String[] w = {""}; final boolean[] nan = {false};
            final int kk = k;
            o.hook = new Arena.Hook() {
                public void turn(Game g, int t, String[] cmd) {
                    for (int i = 0; i < Physics.N; i++) {
                        double v = Math.hypot(g.w.vx[i], g.w.vy[i]);
                        if (Double.isNaN(v) || Double.isNaN(g.w.px[i]) || Double.isNaN(g.w.py[i]) || Math.abs(g.w.px[i]) > 6 || g.w.py[i] < -0.2 || g.w.py[i] > 4) nan[0] = true;
                        if (v > top[0]) { top[0] = v; w[0] = "match " + kk + " t" + t + " " + Physics.PNAME[i % NP] + " of fighter " + i / NP; }
                    }
                }
            };
            int d = k % 3 == 0 ? 700 + 2 * rnd.nextInt(100) : 1600 + 2 * rnd.nextInt(401);
            Arena.Result r = Arena.play("Script", "Script", d, k, o);
            turns += r.turns;
            if (nan[0] || r.simBad > 0 || r.mirrorBad > 0) { bad++; System.out.printf("  match %d (kind %d, start %d mm): out of bounds %b, copy wrong %d (%s), mirror wrong %d (%s)%n", k, kind, d, nan[0], r.simBad, r.simFirst, r.mirrorBad, r.mirrorFirst); }
            if (top[0] > worst) { worst = top[0]; where = w[0] + " (kind " + kind + ", start " + d + " mm): " + Arena.describe(r); }
        }
        System.out.printf(Locale.ROOT, "  %d matches of random joint commands, %d turns: %d with a state out of bounds, a wrong copy or a wrong mirror | top point speed at the end of a turn %.1f m/s, %s%n", matches, turns, bad, worst, where);
    }

    // ---------- a shove: how hard a push each held pose survives. At the end of turn 5 every point of the left fighter is given the same extra speed.
    static void shove(int lo, int hi, int step) {
        for (String pose : new String[]{"STANCE", "GUARD", "RELAX", "DUCK"}) for (int dv = lo; dv <= hi; dv += step) {
            if (dv == 0) continue;
            final int push = dv; final StringBuilder steps = new StringBuilder(); final double[] x = new double[3]; final int[] fall = {-1};
            Arena.Opt o = Tests.opt(); o.maxTurns = 80;
            o.hook = new Arena.Hook() {
                public void turn(Game g, int t, String[] cmd) {
                    if (t == 5) { x[0] = g.w.px[Physics.PELVIS]; for (int i = 0; i < NP; i++) g.w.vx[i] += push / 1000.0; }
                    if (t > 5 && fall[0] < 0) {
                        if (g.stepDir[0] != 0) steps.append(" t").append(t).append(g.stepDir[0] < 0 ? "b" : "f");
                        if (g.stoodUp) fall[0] = t; else { x[1] = g.w.px[Physics.PELVIS]; x[2] = Math.max(x[2], Math.abs(x[1] - x[0])); }
                    }
                }
            };
            Arena.Result r = Arena.play("Hold:" + pose, "Idle", 2400, 1, o);
            System.out.printf(Locale.ROOT, "  %-6s pushed %s at %.2f m/s: %s | recovery steps:%s | farthest %.2f m | %s%n", pose, dv < 0 ? "back   " : "forward", Math.abs(dv) / 1000.0,
                fall[0] < 0 ? String.format(Locale.ROOT, "stands, ends %+.2f m from where it was", x[1] - x[0]) : "FALLS at t" + fall[0], steps.length() == 0 ? " none" : steps.toString(), x[2], ev(r, 200));
        }
    }

    // ---------- the size of what the viewer needs
    static void size(String a, String b) {
        Arena.Opt o = Tests.opt(); o.film = true;
        Arena.Result r = Arena.play(a, b, 2000, 3, o);
        long plain = 0, delta = 0; int[] prev = null;
        for (int[][] turn : r.film) for (int[] fr : turn) {
            StringBuilder p = new StringBuilder(), dl = new StringBuilder();
            for (int i = 0; i < fr.length; i++) { p.append(fr[i]).append(' '); dl.append(prev == null ? fr[i] : fr[i] - prev[i]).append(' '); }
            plain += p.length(); delta += dl.length(); prev = fr;
        }
        System.out.printf("  %s: %d turns, 4 frames per turn, 76 numbers per frame: %d characters as plain millimetres, %d as differences%n", Arena.describe(r), r.turns, plain, delta);
    }
}

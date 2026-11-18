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
            System.out.printf(Locale.ROOT, "  both %-22s start %.2f m: %s | falls+outs left %d (pushed %d), right %d (pushed %d) | HP lost to hits %d/%d%n", String.join("+", pat), d / 1000.0, Arena.describe(r),
                r.falls[0] + r.outs[0], r.freeFalls[0], r.falls[1] + r.outs[1], r.freeFalls[1], r.hpHit[0], r.hpHit[1]);
        }
    }

    // ---------- the price of every fall, counted again here from the contacts and the hits of the match: FALL, or PUSH_FALL after a push
    static void fallCost() {
        String[][] pairs = {{"Kicker", "Hold:DUCK"}, {"Hold:GUARD", "Kicker"}, {"Hold:STEP_FORWARD", "Hold:GUARD"}, {"Rusher", "Hold:STANCE"}, {"Boxer", "Mixed"}, {"Flailer", "Jumper"}, {"Brawler", "Backer"}};
        final int[] n = new int[3];
        for (String[] pr : pairs) for (int d = 1600; d <= 2400; d += 200) for (int side = 0; side < 2; side++) {
            final int[] hp = {100, 100}, last = {-1000, -1000};
            Arena.Opt o = Tests.opt();
            o.hook = new Arena.Hook() {
                public void turn(Game g, int t, String[] cmd) {
                    if (g.w.contactSteps > 0) last[0] = t;
                    if (g.hitLoss[0] > 0 || g.hitLoss[1] > 0) last[1] = t;
                    boolean push = t - last[0] <= Game.WINDOW && t - last[1] > Game.WINDOW;
                    for (int f = 0; f < 2; f++) {
                        boolean fell = g.down[f] || g.out[f];
                        int want = hp[f] - g.hitLoss[f] - (fell ? push ? Game.PUSH_FALL : Game.FALL : 0);
                        if (fell) n[push ? 1 : 0]++;
                        if (g.w.hp[f] != want || g.pushFall[f] != (fell && push)) { n[2]++; if (n[2] <= 5) System.out.println("  BAD t" + t + " fighter " + f + ": HP " + g.w.hp[f] + ", want " + want); }
                        hp[f] = g.w.hp[f];
                    }
                    if (g.down[0] || g.out[0] || g.down[1] || g.out[1]) last[0] = last[1] = -1000;        // back to the start places: the count starts again
                }
            };
            Arena.play(pr[side], pr[1 - side], d, 7, o);
        }
        System.out.println("  falls and ring-outs at " + Game.FALL + " HP: " + n[0] + ", after a push at " + Game.PUSH_FALL + " HP: " + n[1]);
        System.out.println(n[2] == 0 && n[0] > 0 && n[1] > 0 ? "FALLCOST: all checks pass" : "FALLCOST: " + n[2] + " checks FAIL");
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

    // ---------- the legs: catch steps and wide stances. Every scene says what must happen and is played from both sides; the last line counts the scenes that went wrong
    // The scenes are played at full strength (stamina off, as in leagues 1 and 2): a tired fighter is weaker on its legs, which is the stamina rule and has its own table (pushWalk)
    static void legs(String only) {
        // what happens | the script of the fighter under test ("MOVE*turns,...", the last one goes on) | the pose the other one holds | start distance (mm)
        // | push given to the fighter under test at turn 5 (mm/s, forward positive) | turns played | it must take a catch step
        // | optional: how far its pelvis must go from the turn its last command begins to the end (mm, forward positive; a negative number is a way back)
        Object[][] scenes = LEG_SCENES;
        int wrong = 0, n = 0;
        for (Object[] sc : scenes) for (int side = 0; side < 2; side++) {
            if (!only.isEmpty() && !((String) sc[0]).contains(only)) continue;
            final int me = side, push = (Integer) sc[4], turns = (Integer) sc[5];
            List<String> cmds = new ArrayList<>();
            for (String part : ((String) sc[1]).split(",")) { String[] p = part.split("\\*"); for (int k = 0, m = p.length > 1 ? Integer.parseInt(p[1]) : 1; k < m; k++) cmds.add(p[0]); }
            final int last = cmds.size() - 1, far = sc.length > 7 ? (Integer) sc[7] : 0;
            while (cmds.size() < turns) cmds.add(cmds.get(cmds.size() - 1));
            final int[] steps = {0}; final double[] gap = {0, 0}, went = {0, 0};
            Arena.Opt o = Tests.opt(); o.stamina = false; o.maxTurns = turns; o.scripts = new String[][]{{}, {}}; o.scripts[me] = cmds.toArray(new String[0]);
            o.hook = new Arena.Hook() {
                public void turn(Game g, int t, String[] cmd) {
                    if (t == 5 && push != 0) for (int i = 0; i < NP; i++) g.w.vx[me * NP + i] += Physics.FACING[me] * push / 1000.0;
                    if (t > 5 && g.stepDir[me] != 0) steps[0]++;
                    double x = Physics.FACING[me] * g.w.px[me * NP + Physics.PELVIS];
                    if (t <= last) went[0] = x;
                    went[1] = x - went[0];
                    gap[0] = Math.abs(g.w.px[me * NP + Physics.LANK] - g.w.px[me * NP + Physics.RANK]); gap[1] = Math.max(gap[1], gap[0]);
                }
            };
            String other = "Hold:" + sc[2];
            Arena.Result r = me == 0 ? Arena.play("Script", other, (Integer) sc[3], 1, o) : Arena.play(other, "Script", (Integer) sc[3], 1, o);
            int mine = r.falls[me] + r.outs[me], theirs = r.falls[1 - me] + r.outs[1 - me];
            boolean ok = mine == 0 && theirs == 0 && (!(Boolean) sc[6] || steps[0] > 0) && (far == 0 || (far > 0 ? went[1] * 1000 >= far : went[1] * 1000 <= far));
            n++; if (!ok) wrong++;
            System.out.printf(Locale.ROOT, "  %-5s %-62s (%s): falls %d, the other one %d | catch steps %d | feet %.2f m apart at the end, %.2f m at most%s | %s%n", ok ? "ok" : "WRONG", sc[0], me == 0 ? "left" : "right",
                mine, theirs, steps[0], gap[0], gap[1], far == 0 ? "" : String.format(Locale.ROOT, " | went %+.2f m with its last command (%+.2f m asked)", went[1], far / 1000.0), ev(r, 120));
        }
        System.out.println(wrong + " wrong of " + n + " (expected everywhere: nobody falls" + ", a catch step where the scene asks for one, and the way asked where the scene gives one)");
    }
    // ---------- a push, then a walk: where the walker goes down, by push speed, by delay and by stamina. A table to read, with no verdict
    static void pushWalk() {
        for (int pin : new int[]{-1, 75, 50, 25}) for (int sign = -1; sign <= 1; sign += 2) {
            System.out.println((pin < 0 ? "full strength (stamina off)" : "stamina held at " + pin + " %") + ": in GUARD, pushed " + (sign > 0 ? "forward" : "back")
                + " at turn 5, STEP_FORWARD for 12 turns K turns later, then GUARD | one column per push speed, 1.0 to 2.6 m/s by 0.1 | '.' stands, 'F' falls or steps out");
            for (int k = 0; k <= 6; k++) {
                StringBuilder sb = new StringBuilder();
                for (int v = 1000; v <= 2600; v += 100) {
                    List<String> cmds = new ArrayList<>();
                    for (int i = 0; i < 5 + k; i++) cmds.add("GUARD");
                    for (int i = 0; i < 12; i++) cmds.add("STEP_FORWARD");
                    while (cmds.size() < 70) cmds.add("GUARD");
                    final int push = sign * v, level = pin * 1000;
                    Arena.Opt o = Tests.opt(); o.stamina = pin >= 0; o.maxTurns = 70; o.scripts = new String[][]{cmds.toArray(new String[0]), {}};
                    o.hook = new Arena.Hook() {
                        public void turn(Game g, int t, String[] cmd) {
                            if (level >= 0) g.stamina[0] = g.stamina[1] = level;
                            if (t == 5) for (int i = 0; i < NP; i++) g.w.vx[i] += push / 1000.0;
                        }
                    };
                    Arena.Result r = Arena.play("Script", "Hold:GUARD", 3000, 1, o);
                    sb.append(r.falls[0] + r.outs[0] > 0 ? 'F' : '.');
                }
                System.out.println("  K=" + k + " " + sb);
            }
        }
    }
    static final Object[][] LEG_SCENES = {
        {"standing, pushed back at 1.5 m/s: steps back", "STANCE", "GUARD", 2400, -1500, 80, true},
        {"standing, pushed forward at 1.5 m/s: steps forward", "STANCE", "GUARD", 2400, 1500, 80, true},
        {"ducked, pushed back at 1.5 m/s: steps back", "DUCK", "GUARD", 2400, -1500, 80, true},
        {"guard, pushed forward at 1 m/s: steps forward", "GUARD", "GUARD", 2400, 1000, 80, true},
        {"a kick after a stop, into a ducked body", "STEP_FORWARD*10,STANCE*8,KICK*6,STANCE", "DUCK", 1600, 0, 54, false},
        {"a kick after a stop, into a ducked body, from farther", "STEP_FORWARD*16,STANCE*8,KICK*6,STANCE", "DUCK", 1840, 0, 60, false},
        {"walking into a ducked fighter, then standing", "STEP_FORWARD*25,STANCE", "DUCK", 1760, 0, 60, false},
        {"a kick right after a step", "STEP_FORWARD*14,KICK*6,STANCE", "STANCE", 1760, 0, 50, false},
        {"a short step back, a held guard, then walking in", "STEP_BACK*2,GUARD*5,STEP_FORWARD*14,GUARD", "STANCE", 2400, 0, 60, false},
        {"a step back, a guard, then walking in", "STEP_BACK*3,GUARD*5,STEP_FORWARD*14,GUARD", "STANCE", 2400, 0, 60, false},
        {"a step back, a longer guard, then walking in", "STEP_BACK*3,GUARD*7,STEP_FORWARD*14,GUARD", "STANCE", 2400, 0, 60, false},
        // a walker first keeps its balance: pushed one way, it walks the other way, or the same way
        {"pushed forward at 1.2 m/s, a walk back a turn later", "GUARD*6,STEP_BACK*12,GUARD", "GUARD", 3000, 1200, 70, false},
        {"pushed forward at 1.4 m/s, a walk back two turns later", "GUARD*7,STEP_BACK*12,GUARD", "GUARD", 2400, 1400, 70, false},
        {"pushed back at 1.5 m/s, a walk forward two turns later", "GUARD*7,STEP_FORWARD*12,GUARD", "GUARD", 2400, -1500, 70, false},
        {"pushed back at 1.5 m/s, a walk forward three turns later", "GUARD*8,STEP_FORWARD*12,GUARD", "GUARD", 2400, -1500, 70, false},
        {"pushed forward at 1.6 m/s, then a walk forward", "GUARD*5,STEP_FORWARD*12,GUARD", "GUARD", 2400, 1600, 70, false},
        {"pushed forward at 1.6 m/s, a walk forward a turn later", "GUARD*6,STEP_FORWARD*12,GUARD", "GUARD", 2400, 1600, 70, false},
        {"pushed forward at 1.6 m/s, a walk forward three turns later", "GUARD*8,STEP_FORWARD*12,GUARD", "GUARD", 3000, 1600, 70, false},
        // a long walk back stays slow, and a step back never begins with the weight on the foot it lifts first
        {"a long walk back", "STEP_BACK*49,GUARD", "GUARD", 1600, 0, 80, false},
        {"a walk back cut after 3 turns, again and again", "STEP_BACK*3,GUARD*2,STEP_BACK*3,GUARD*2,STEP_BACK*3,GUARD*2,STEP_BACK*3,GUARD*2,STEP_BACK*3,GUARD", "GUARD", 1600, 0, 60, false},
        {"a kick, then a walk back", "KICK*6,STEP_BACK*14,GUARD", "GUARD", 2400, 0, 50, false},
        {"a long walk back, then walking in again", "STEP_BACK*21,STEP_FORWARD*21,GUARD", "GUARD", 1600, 0, 70, false},
        {"a cross, then a walk back", "CROSS*3,STEP_BACK*14,GUARD", "GUARD", 2400, 0, 50, false},
        {"a jab, then a walk back", "JAB*3,STEP_BACK*14,GUARD", "GUARD", 2400, 0, 50, false},
        {"walking in, then back at once", "STEP_FORWARD*7,STEP_BACK*14,GUARD", "GUARD", 2400, 0, 50, false},
        // a walker whose trunk leans first straightens up
        {"ducked for 8 turns, then a walk forward", "DUCK*8,STEP_FORWARD*14,GUARD", "GUARD", 2400, 0, 60, false},
        {"ducked for 9 turns, then a walk forward", "DUCK*9,STEP_FORWARD*14,GUARD", "GUARD", 2400, 0, 60, false},
        // feet out of place do not hold a walker: its step puts them back
        {"a walk, an overhand, a guard with the feet wide, then a walk that goes on", "STEP_FORWARD*7,GUARD*2,STEP_FORWARD*21,OVERHAND*4,GUARD*7,STEP_FORWARD", "STANCE", 1618, 0, 80, false, 300},
        // a walker that still goes the other way, its weight beyond the foot that stays, first catches its balance
        {"kicks and walks around a ducked fighter, then a step back while going forward", "STEP_FORWARD*7,GUARD*2,STEP_FORWARD*7,GUARD,STEP_FORWARD*21,GUARD*5,KICK*6,STANCE*2,STEP_BACK*28,STANCE*75,STEP_BACK*7,STANCE*4,STEP_BACK*19,GUARD", "DUCK", 2266, 0, 200, false},
        // a walking step that begins with the feet crossed: the weight goes onto the foot that stays before the first foot leaves the floor
        {"a kick on the move, a guard on crossed feet, then the walk again", "STEP_FORWARD*8,KICK*6,GUARD*4,STEP_FORWARD", "GUARD", 1800, 0, 90, false},
    };

    // ---------- a split too deep is a fall
    /** The scenes of the deep split: the fighter under test plays a script against a held pose; a deep split must end as a fall, and nobody ends a turn in one. */
    static void split() {
        // what happens | the script ("MOVE*turns,...", the last one goes on) | the pose the other one holds | start distance (mm) | turns played | a fall by split is expected
        Object[][] scenes = {
            {"a kick right after a walk, then a walk again, into a ducked body: a deep split", "STEP_FORWARD*18,KICK,WAIT*3,STEP_FORWARD", "DUCK", 2000, 45, true},
            {"kicks on the move, then punches: the feet come back together", "STEP_FORWARD*10,KICK,WAIT,GUARD,KICK,WAIT*3,CROSS,OVERHAND,GUARD", "GUARD", 1600, 70, false},
            {"holds DUCK", "DUCK", "GUARD", 2400, 60, false},
            {"walks in, then stands", "STEP_FORWARD*20,STANCE", "GUARD", 2400, 60, false},
            {"a kick after a stop", "STEP_FORWARD*10,STANCE*8,KICK*6,STANCE", "STANCE", 1600, 54, false},
        };
        int wrong = 0, n = 0;
        for (Object[] sc : scenes) for (int side = 0; side < 2; side++) {
            final int me = side, turns = (Integer) sc[4];
            List<String> cmds = new ArrayList<>();
            for (String part : ((String) sc[1]).split(",")) { String[] p = part.split("\\*"); for (int k = 0, m = p.length > 1 ? Integer.parseInt(p[1]) : 1; k < m; k++) cmds.add(p[0]); }
            while (cmds.size() < turns) cmds.add(cmds.get(cmds.size() - 1));
            final int[] count = {0, 0, -1};                                  // falls by split, turns that end in a deep split, the turn of the first fall by split
            Arena.Opt o = Tests.opt(); o.maxTurns = turns; o.scripts = new String[][]{{}, {}}; o.scripts[me] = cmds.toArray(new String[0]);
            o.hook = new Arena.Hook() {
                public void turn(Game g, int t, String[] cmd) {
                    for (int f = 0; f < 2; f++) {
                        if (g.split[f] && !g.down[f]) count[1]++;            // a fall by split is a down
                        if (Math.abs(g.w.px[f * NP + Physics.LANK] - g.w.px[f * NP + Physics.RANK]) > Game.SPLIT && g.w.py[f * NP + Physics.PELVIS] < Game.SPLIT_LOW) count[1]++;
                    }
                    if (g.split[me]) { count[0]++; if (count[2] < 0) count[2] = t; }
                }
            };
            String other = "Hold:" + sc[2];
            Arena.Result r = me == 0 ? Arena.play("Script", other, (Integer) sc[3], 1, o) : Arena.play(other, "Script", (Integer) sc[3], 1, o);
            boolean ok = count[1] == 0 && (count[0] > 0) == (Boolean) sc[5] && r.falls[me] >= count[0];
            n++; if (!ok) wrong++;
            System.out.printf(Locale.ROOT, "  %-5s %-58s (%s): falls by split %d (first at turn %d), falls %d | turns ended in a deep split %d | %s%n", ok ? "ok" : "WRONG", sc[0], me == 0 ? "left" : "right",
                count[0], count[2], r.falls[me], count[1], ev(r, 120));
        }
        System.out.println(wrong + " wrong of " + n + " (expected: a deep split ends as a fall, where the scene has one; no turn ends with the ankles more than " + Game.SPLIT + " m apart and the pelvis under " + Game.SPLIT_LOW + " m)");
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

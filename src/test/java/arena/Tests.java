package arena;

import com.codingame.game.engine.Command;
import com.codingame.game.engine.Game;
import com.codingame.game.engine.Moves;
import com.codingame.game.engine.Physics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Collectors;

/** The test bench of the game. Each mode prints traces; see TESTS.md for what each one proves. */
public final class Tests {
    static final int NP = Physics.NP, NJ = Physics.NJ;
    static final boolean EV = Boolean.getBoolean("ev");

    static Arena.Opt opt() {
        Arena.Opt o = new Arena.Opt();
        o.stamina = !"false".equals(System.getProperty("stamina")); o.joints = !"false".equals(System.getProperty("joints"));
        o.checkSim = o.checkMirror = Boolean.getBoolean("check");
        o.sabotage = Integer.getInteger("sabotage", 0);
        return o;
    }
    static String rep(String s, int n) { StringBuilder sb = new StringBuilder(); for (int i = 0; i < n; i++) sb.append(s); return sb.toString(); }
    static String cut(String s, int n) { return s.length() > n ? s.substring(0, n) + "..." : s; }

    // ---------- bad outputs: what does the referee do with each?
    static void fuzz() {
        String ok = "JOINTS" + rep(" 10 50", NJ);
        String[] lines = {"JAB", "jab", "JAB hello", " JAB ", "WAIT", "WAIT hello", "", "FOO", "RECOVER_BACK", "STANCE STANCE", "JOINTS", "JOINTS 1 2 3", ok, ok + " ROUND", ok + " round", ok + " hello", ok + " ROUND hello",
            ok.replaceFirst("10", "NaN"), ok.replaceFirst("50", "NaN"), ok.replaceFirst("10", "1e999"), ok.replaceFirst("10", "abc"), ok.replaceFirst("50", "-5"), ok.replaceFirst("10", "0x1p3"), ok.replaceFirst("10", "10d"),
            ok.replaceFirst("10", "Infinity"), "JOINTS" + rep(" * *", NJ), "LOOK 3 3 JAB"};
        for (String l : lines) run1(l, 3);
        for (String l : new String[]{"LOOK", "LOOK 3", "LOOK a b", "LOOK 99 99 JAB", "LOOK 3 3", "LOOK 3 3 hello", "LOOK 3 3 " + ok, "LOOK 3.0 3 JAB", "look 3 3 JAB", "LOOK 3 3 LOOK 2 2"}) run1(l, 0);
        // more: values, spaces, lengths
        for (String l : new String[]{ok + " 5", ok + " *", ok.replaceFirst("10", "+10"), ok.replaceFirst("10", "1e2"), ok.replaceFirst("10", "999999"), ok.replaceFirst("10", "9999999"), ok.replaceFirst("10", "-0.004"),
            ok.replaceFirst("50", "100.5"), ok.replaceFirst("50", "250"), ok.replaceFirst("10", "10."), ok.replaceFirst("10", ".5"), ok.replaceFirst("10", "1,5"), ok.replace(" ", "\t"), ok.replace(" ", "  "),
            "JAB\r", "JAB " + rep("x", 5000), "JOINTS" + rep(" 10 50", 5000), "\u00a0JAB", "JAB\u0000", "STANCE ROUND", "HIGH_KICK ROUND", "WAIT WAIT", "Guard", "GUARD;JAB"}) run1(l, 3);
        Arena.Opt o = opt(); o.joints = false;
        System.out.println("  league without JOINTS:");
        for (String l : new String[]{ok, "JAB", "WAIT"}) run1(l, 3, o);
    }
    // ---------- LOOK is cosmetic: it never costs the match. Each case: the line, first turn or not, JOINTS allowed, expected error (null: accepted),
    // expected type, move, skin and hair (-1: the look is not changed)
    static void look() {
        Object[][] cases = {
            {"LOOK 3 5 JAB", true, true, null, Command.MOVE, Moves.JAB, 3, 5},
            {"LOOK 3 5", true, true, null, Command.WAIT, Moves.NONE, 3, 5},
            {"LOOK 7 9 STEP_FORWARD", true, true, null, Command.MOVE, Moves.STEP_FORWARD, 7, 9},
            {"LOOK 0 0 GUARD", true, true, null, Command.MOVE, Moves.GUARD, 0, 0},
            {"LOOK 8 5 JAB", true, true, null, Command.MOVE, Moves.JAB, -1, -1},          // skins go from 0 to 7
            {"LOOK 3 10 JAB", true, true, null, Command.MOVE, Moves.JAB, -1, -1},         // hairs go from 0 to 9
            {"LOOK 99 99 JAB", true, true, null, Command.MOVE, Moves.JAB, -1, -1},
            {"LOOK -1 5 JAB", true, true, null, Command.MOVE, Moves.JAB, -1, -1},
            {"LOOK 3.0 3 JAB", true, true, null, Command.MOVE, Moves.JAB, -1, -1},
            {"LOOK 3 JAB", true, true, null, Command.MOVE, Moves.JAB, -1, -1},
            {"LOOK JAB", true, true, null, Command.MOVE, Moves.JAB, -1, -1},
            {"LOOK", true, true, null, Command.WAIT, Moves.NONE, -1, -1},
            {"LOOK 3 5 JAB", false, true, null, Command.MOVE, Moves.JAB, -1, -1},         // a later turn: the look stays as it is
            {"LOOK 3 5", false, true, null, Command.WAIT, Moves.NONE, -1, -1},
            {"LOOK 3 5 JAB hello there", false, true, null, Command.MOVE, Moves.JAB, -1, -1},
            {"LOOK 3 5 " + "JOINTS" + rep(" 10 50", NJ), false, true, null, Command.JOINTS, Moves.NONE, -1, -1},
            {"LOOK 3 5 " + "JOINTS" + rep(" 10 50", NJ), true, false, "JOINTS is not available in this league", 0, 0, 0, 0},   // the command itself stays strict
            {"look 3 5 JAB", true, true, "unknown command: look", 0, 0, 0, 0},
            {"LOOK a b", true, true, "unknown command: a", 0, 0, 0, 0},
            {"LOOK 3 5 hello", false, true, "unknown command: hello", 0, 0, 0, 0},
            {"LOOK 3 5 LOOK 2 2", true, true, "unknown command: LOOK", 0, 0, 0, 0},
        };
        int bad = 0;
        for (Object[] k : cases) {
            Command c = Command.parse((String) k[0], (Boolean) k[1], (Boolean) k[2]);
            String want = (String) k[3];
            boolean ok = want == null ? c.error == null && c.type == (Integer) k[4] && c.move == (Integer) k[5] && c.skin == (Integer) k[6] && c.hair == (Integer) k[7]
                                      : want.equals(c.error);
            if (!ok) bad++;
            System.out.printf("  %-4s %-40s first turn %-5s -> %s%n", ok ? "ok" : "BAD", "\"" + cut((String) k[0], 38) + "\"", k[1],
                c.error != null ? "refused: " + c.error : "type " + c.type + ", move " + (c.move >= 0 ? Moves.NAME[c.move] : "-") + ", look " + c.skin + " " + c.hair + (c.message.isEmpty() ? "" : ", message \"" + c.message + "\""));
        }
        // through the engine: a look sent on the first turn stays for the whole match; later LOOKs change nothing and cost nothing
        Game g = new Game(2000, true, true);
        String[] left = {"LOOK 3 5 STANCE", "STANCE", "LOOK 1 1 JAB", "WAIT", "WAIT", "LOOK 99 99 GUARD", "LOOK", "LOOK 6 2 STEP_BACK"};
        for (String l : left) {
            String e = g.apply(0, l), e2 = g.apply(1, g.turn == 0 ? "LOOK 6 0 STANCE" : "STANCE");
            if (e != null || e2 != null) { bad++; System.out.println("  BAD: refused at turn " + g.turn + ": " + e + " / " + e2); }
            g.simulate();
        }
        boolean looks = g.look[0][0] == 3 && g.look[0][1] == 5 && g.look[1][0] == 6 && g.look[1][1] == 0;
        if (!looks) bad++;
        System.out.printf("  %-4s engine, 8 turns with LOOK on turns 0, 2, 5, 6, 7: looks %d %d / %d %d (want 3 5 / 6 0), move now %s%n", looks ? "ok" : "BAD", g.look[0][0], g.look[0][1], g.look[1][0], g.look[1][1], g.moveName(0));
        System.out.println(bad == 0 ? "LOOK: all checks pass" : "LOOK: " + bad + " checks FAIL");
    }

    static void run1(String l, int at) { run1(l, at, opt()); }
    static void run1(String l, int at, Arena.Opt o) {
        String[] sc = new String[at + 1];
        Arrays.fill(sc, "STANCE"); sc[at] = l;
        o.scripts = new String[][]{sc, {}}; o.maxTurns = 12;
        String show = l.replace("\t", "\\t").replace("\r", "\\r").replace("\u0000", "\\0").replace("\u00a0", "\\u00a0");
        show = show.length() > 60 ? show.substring(0, 28) + " ... " + show.substring(show.length() - 26) : show;
        try {
            Arena.Result r = Arena.play("Script", "Idle", 2000, 1, o);
            boolean bad = false;
            for (int i = 0; i < Physics.N; i++) if (Double.isNaN(r.last.w.px[i]) || Double.isNaN(r.last.w.py[i])) bad = true;
            System.out.printf("  turn %d %-62s -> %s%s%n", at, "\"" + show + "\"", r.end.equals("invalid") ? "REFUSED, the bot loses (" + r.invalid.trim() + ")" : "accepted" + (r.last.message[0].isEmpty() ? "" : ", message \"" + cut(r.last.message[0], 50) + "\""), bad ? ", STATE IS NaN" : "");
        } catch (Throwable t) {
            System.out.printf("  turn %d %-62s -> REFEREE THROWS %s: %s%n", at, "\"" + show + "\"", t.getClass().getSimpleName(), String.valueOf(t.getMessage()).replaceAll("\\s+", " "));
        }
    }

    // ---------- matches between two named bots: one line per match (and its events with -Dev=true)
    static List<Arena.Result> m(final String a, final String b, int n, long seed0, int dist, boolean print) {
        Random rnd = new Random(seed0);
        final List<long[]> tasks = new ArrayList<long[]>();
        for (int k = 0; k < n; k++) tasks.add(new long[]{k % 2, dist > 0 ? dist : 1600 + 2 * rnd.nextInt(401), rnd.nextLong(), k});
        final Arena.Opt o = opt();
        List<Arena.Result> rs = tasks.parallelStream().map(t -> Arena.play(t[0] == 1 ? b : a, t[0] == 1 ? a : b, (int) t[1], t[2], o.copy())).collect(Collectors.toList());
        int[] tot = new int[3]; long[] agg = new long[14];
        for (int k = 0; k < n; k++) {
            Arena.Result r = rs.get(k);
            int f = (int) tasks.get(k)[0];
            if (r.winner == f) tot[0]++; else if (r.winner < 0) tot[1]++; else tot[2]++;
            agg[0] += r.hpHit[f]; agg[1] += r.hpFall[f]; agg[2] += r.hpHit[1 - f]; agg[3] += r.hpFall[1 - f]; agg[4] += r.falls[f] + r.outs[f]; agg[5] += r.falls[1 - f] + r.outs[1 - f];
            agg[6] += r.freeFalls[f]; agg[7] += r.freeFalls[1 - f]; agg[8] += r.botNanos[f] / Math.max(1, r.turns); agg[9] += r.botNanos[1 - f] / Math.max(1, r.turns); agg[10] += r.turns; agg[11] += r.resets;
            agg[12] += r.simBad; agg[13] += r.mirrorBad;
            if (print) {
                System.out.printf(Locale.ROOT, "  #%02d %s (%s left, start %.2f m): %s%n", k, a, f == 1 ? b : a, r.dist / 1000.0, Arena.line(r));
                if (o.checkSim && (r.simBad > 0 || r.mirrorBad > 0)) System.out.printf("        CHECKS: copy wrong %d of %d (%s) | mirror wrong %d (%s)%n", r.simBad, r.simChecks, r.simFirst, r.mirrorBad, r.mirrorFirst);
                if (EV) System.out.println("        " + cut(r.evlog.toString(), 2500));
            }
        }
        System.out.printf(Locale.ROOT, "== %s vs %s: %d wins, %d draws, %d losses of %d | per match: %s loses %.1f HP to hits + %.1f to falls (%.1f falls, %.1f free); %s loses %.1f + %.1f (%.1f falls, %.1f free) | %.0f turns, %.1f resets | ms per turn %.1f vs %.1f%s%n",
            a, b, tot[0], tot[1], tot[2], n, a, (double) agg[0] / n, (double) agg[1] / n, (double) agg[4] / n, (double) agg[6] / n, b, (double) agg[2] / n, (double) agg[3] / n, (double) agg[5] / n, (double) agg[7] / n,
            (double) agg[10] / n, (double) agg[11] / n, agg[8] / 1e6 / n, agg[9] / 1e6 / n, o.checkSim ? " | checks: copy wrong " + agg[12] + ", mirror wrong " + agg[13] : "");
        return rs;
    }

    // ---------- exactness: for many kinds of fights, the copy rebuilt from the input and the mirrored match must agree bit for bit
    static void check(int n) {
        String[][] pairs = {{"Mixed", "Kicker"}, {"Boxer", "Brawler"}, {"Smart", "Ducker"}, {"Flailer", "Rusher"}, {"Jumper", "Backer"}, {"Counter", "Mixed"}, {"Flailer", "Flailer"}, {"Turtle", "Rusher"},
            {"S:20:6:0:1:1", "Mixed"}, {"S:20:6:0:1:1", "S:20:6:0:1:1"}, {"Fling:8", "Kicker"}, {"Fling:12", "Boxer"}, {"Round:Mixed", "Kicker"}, {"Raw:Kicker", "Raw:Boxer"}, {"SLead:FLOP", "Mixed"}, {"Hold:DUCK", "Idle"},
            {"Limp", "Rusher"}, {"Hold:RELAX", "Jumper"}};
        long checks = 0, simBad = 0, mirBad = 0, turns = 0;
        for (final String[] pr : pairs) {
            final Arena.Opt o = opt(); o.checkSim = o.checkMirror = true;
            List<Integer> ks = new ArrayList<Integer>();
            for (int k = 0; k < n; k++) ks.add(k);
            List<Arena.Result> rs = ks.parallelStream().map(k -> Arena.play(k % 2 == 0 ? pr[0] : pr[1], k % 2 == 0 ? pr[1] : pr[0], 0, 1000 + k, o.copy())).collect(Collectors.toList());
            long c = 0, sb = 0, mb = 0, tn = 0, falls = 0, hits = 0; String first = "";
            for (Arena.Result r : rs) {
                c += r.simChecks; sb += r.simBad; mb += r.mirrorBad; tn += r.turns; falls += r.falls[0] + r.falls[1] + r.outs[0] + r.outs[1]; hits += r.hpHit[0] + r.hpHit[1];
                if (first.isEmpty() && r.simBad > 0) first = " | first copy error: " + r.simFirst;
                if (first.isEmpty() && r.mirrorBad > 0) first = " | first mirror error: " + r.mirrorFirst;
            }
            System.out.printf("  %-14s vs %-14s %d matches, %5d turns, %3d falls, %4d HP of hits: copy wrong %d of %d, mirror wrong %d of %d turns%s%n", pr[0], pr[1], n, tn, falls, hits, sb, c, mb, tn, first);
            checks += c; simBad += sb; mirBad += mb; turns += tn;
        }
        System.out.printf("  total: %d turns; rebuilt copy wrong %d of %d predictions; mirrored match wrong on %d of %d turns%n", turns, simBad, checks, mirBad, turns);
    }

    // ---------- the same bot on both sides with the same seed: the match must be its own mirror image, so a draw
    static void mirror(int seeds) {
        List<String> bots = new ArrayList<String>(Arrays.asList(Bots.NAMED));
        bots.add("S:20:6:0:1:1"); bots.add("S:30:6:1:1:1");
        int tl = 0, tr = 0, td = 0, broken = 0;
        for (String b : bots) {
            int L = 0, R = 0, D = 0, bad = 0; String ex = "";
            for (int s = 0; s < seeds; s++) {
                Arena.Opt o = opt(); o.botSeed = new long[]{s + 100, s + 100};
                final int[] first = {-1}; final String[] what = {""};
                o.hook = new Arena.Hook() {
                    public void turn(Game g, int t, String[] cmd) {
                        if (first[0] >= 0) return;
                        for (int i = 0; i < NP; i++) if (g.w.px[i] != -g.w.px[NP + i] || g.w.py[i] != g.w.py[NP + i] || g.w.vx[i] != -g.w.vx[NP + i] || g.w.vy[i] != g.w.vy[NP + i]) {
                            first[0] = t; what[0] = Physics.PNAME[i] + " " + g.w.px[i] + "," + g.w.py[i] + " / " + g.w.px[NP + i] + "," + g.w.py[NP + i]; return;
                        }
                        if (g.w.hp[0] != g.w.hp[1]) { first[0] = t; what[0] = "HP " + g.w.hp[0] + "/" + g.w.hp[1]; }
                    }
                };
                int dist = 1600 + 2 * (int) Math.round(400.0 * s / Math.max(1, seeds - 1));
                Arena.Result r = Arena.play(b, b, dist, s, o);
                if (r.winner == 0) L++; else if (r.winner == 1) R++; else D++;
                if (first[0] >= 0) { bad++; if (ex.isEmpty()) ex = String.format(Locale.ROOT, " | start %.2f m: mirror broken at t%d (%s), first contact t%d; %s", dist / 1000.0, first[0], what[0], r.firstContact, Arena.describe(r)); }
            }
            tl += L; tr += R; td += D; broken += bad;
            System.out.printf("  %-13s vs itself, %d starts: left wins %d, right wins %d, draws %d | matches where the mirror broke: %d%s%n", b, seeds, L, R, D, bad, ex);
        }
        System.out.printf("  total: left %d, right %d, draws %d, broken mirrors %d%n", tl, tr, td, broken);
    }

    // ---------- everyone against everyone
    static void tour(int n, String excl, String extra) {
        List<String> bots = new ArrayList<String>();
        List<String> ex = Arrays.asList(excl.split(","));
        for (String b : Bots.NAMED) if (!ex.contains(b)) bots.add(b);
        if (!extra.isEmpty()) bots.addAll(Arrays.asList(extra.split(",")));
        int B = bots.size();
        int[][] wdl = new int[B][3]; long[][] st = new long[B][8];
        int[][] grid = new int[B][B];
        int matches = 0, time = 0, ko = 0, dko = 0, invalid = 0, draws = 0; long turns = 0, firstHit = 0, noHit = 0, resets = 0;
        for (int i = 0; i < B; i++) for (int j = i + 1; j < B; j++) {
            List<Arena.Result> rs = m(bots.get(i), bots.get(j), n, 5000 + 37 * i + j, 0, false);
            for (int k = 0; k < rs.size(); k++) {
                Arena.Result r = rs.get(k);
                int f = k % 2;                       // the side of bot i
                int[] who = {f == 0 ? i : j, f == 0 ? j : i};
                matches++; turns += r.turns; resets += r.resets;
                if (r.end.equals("time")) time++; else if (r.end.equals("KO")) ko++; else if (r.end.equals("double KO")) dko++; else invalid++;
                if (r.winner < 0) { draws++; wdl[i][1]++; wdl[j][1]++; } else { wdl[who[r.winner]][0]++; wdl[who[1 - r.winner]][2]++; grid[who[r.winner]][who[1 - r.winner]]++; }
                if (r.firstHit < 0) noHit++; else firstHit += r.firstHit;
                for (int s = 0; s < 2; s++) {
                    long[] q = st[who[s]];
                    q[0] += r.hpHit[s]; q[1] += r.hpFall[s]; q[2] += r.falls[s]; q[3] += r.outs[s]; q[4] += r.freeFalls[s]; q[5] += r.recoveries[s]; q[6] += r.recChains[s]; q[7]++;
                }
            }
        }
        System.out.printf(Locale.ROOT, "%n%d matches (stamina %s, JOINTS %s): %d to the time limit, %d KO, %d double KO, %d invalid | %d draws | %.0f turns and %.1f resets per match | first hit at turn %.0f on average, %d matches without a hit%n",
            matches, opt().stamina ? "on" : "off", opt().joints ? "on" : "off", time, ko, dko, invalid, draws, (double) turns / matches, (double) resets / matches, (double) firstHit / Math.max(1, matches - noHit), noHit);
        Integer[] order = new Integer[B];
        for (int i = 0; i < B; i++) order[i] = i;
        Arrays.sort(order, (x, y) -> (2 * wdl[y][0] + wdl[y][1]) - (2 * wdl[x][0] + wdl[x][1]));
        System.out.println("  bot              W   D   L | per match: HP lost to hits, to falls | falls, ring-outs, of which free | recovery steps, of which back to back");
        for (int i : order) {
            long[] q = st[i]; double c = Math.max(1, q[7]);
            System.out.printf(Locale.ROOT, "  %-14s %3d %3d %3d | %5.1f %5.1f | %4.1f %4.1f %4.1f | %5.1f %5.1f%n", bots.get(i), wdl[i][0], wdl[i][1], wdl[i][2], q[0] / c, q[1] / c, q[2] / c, q[3] / c, q[4] / c, q[5] / c, q[6] / c);
        }
        System.out.println("  wins of the row against the column:");
        StringBuilder h = new StringBuilder("  " + rep(" ", 15));
        for (int i : order) h.append(String.format("%-4s", bots.get(i).length() > 3 ? bots.get(i).substring(0, 3) : bots.get(i)));
        System.out.println(h);
        for (int i : order) {
            StringBuilder sb = new StringBuilder(String.format("  %-14s ", bots.get(i)));
            for (int j : order) sb.append(i == j ? "  . " : String.format("%3d ", grid[i][j]));
            System.out.println(sb);
        }
    }

    // ---------- every fall of a tournament, sorted by what caused it; each example can be replayed with "trace A B N SEED K FROM TO"
    static void falls(int n, String excl, String extra) {
        List<String> bots = new ArrayList<String>();
        List<String> ex = Arrays.asList(excl.split(","));
        for (String b : Bots.NAMED) if (!ex.contains(b)) bots.add(b);
        if (!extra.isEmpty()) bots.addAll(Arrays.asList(extra.split(",")));
        String far = "paid: no contact for more than " + Game.WINDOW + " turns";
        String[] kind = {"free: pushed, nobody hit in the " + Game.WINDOW + " turns before", "paid: it was hit in the " + Game.WINDOW + " turns before", "paid: it landed a hit in the " + Game.WINDOW + " turns before, took none",
            far + ", a recovery step in the 25 turns before", far + ", no recovery step",
            "(of the paid ones) it was hit 11 to " + Game.WINDOW + " turns before and nobody was hit after that, contact in the window", "(of the paid ones) it landed a hit 11 to " + Game.WINDOW + " turns before, took none, nobody was hit after that, contact in the window"};
        int[] count = new int[kind.length], outs = new int[kind.length]; List<List<String>> examples = new ArrayList<List<String>>();
        for (int c = 0; c < kind.length; c++) examples.add(new ArrayList<String>());
        int matches = 0;
        for (int i = 0; i < bots.size(); i++) for (int j = i + 1; j < bots.size(); j++) {
            long seed0 = 5000 + 37 * i + j;
            List<Arena.Result> rs = m(bots.get(i), bots.get(j), n, seed0, 0, false);
            for (int k = 0; k < rs.size(); k++) {
                matches++;
                for (int[] e : rs.get(k).fallLog) {
                    int c = e[3] == 1 ? 0 : e[4] > 0 ? 1 : e[5] > 0 ? 2 : e[7] <= 25 ? 3 : 4;
                    count[c]++; outs[c] += e[2];
                    String who = (e[1] == 0) == (k % 2 == 0) ? bots.get(i) : bots.get(j);
                    String line = String.format("%s falls: trace %s %s %d %d %d %d %d", who, bots.get(i), bots.get(j), n, seed0, k, Math.max(0, e[0] - 22), e[0] + 1);
                    examples.get(c).add(line);
                    if ((c == 1 || c == 2) && e[9] + e[10] == 0 && e[6] <= Game.WINDOW) { int d = c + 4; count[d]++; outs[d] += e[2]; examples.get(d).add(line); }
                }
            }
        }
        int total = 0;
        for (int c = 0; c < 5; c++) total += count[c];
        System.out.printf("%n%d matches, %d falls and ring-outs%n", matches, total);
        for (int c = 0; c < kind.length; c++) {
            System.out.printf("  %4d (%d ring-outs)  %s%n", count[c], outs[c], kind[c]);
            List<String> e = examples.get(c);
            Collections.shuffle(e, new Random(5));
            for (int q = 0; q < Math.min(e.size(), c >= 3 ? 8 : 3); q++) System.out.println("         " + e.get(q));
        }
    }

    // ---------- one match of "m A B N SEED", turn by turn: what each fighter asked, where its weight is, the contacts
    static void trace(final String a, final String b, int n, long seed0, int k, final int from, final int to) {
        Random rnd = new Random(seed0);
        long[] task = null;
        for (int i = 0; i <= k; i++) task = new long[]{i % 2, 1600 + 2 * rnd.nextInt(401), rnd.nextLong(), i};
        Arena.Opt o = opt();
        o.hook = new Arena.Hook() {
            public void turn(Game g, int t, String[] cmd) {
                if (t < from || t > to) return;
                StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "  t%-3d contact %2d steps, since contact %2d, since hit %2d |", t, g.w.contactSteps, g.sinceContact, g.sinceHit));
                for (int f = 0; f < 2; f++) {
                    int s = Physics.FACING[f], q = f * NP;
                    double back = Math.min(s * g.w.px[q + Physics.RHEEL], s * g.w.px[q + Physics.LHEEL]), front = Math.max(s * g.w.px[q + Physics.LTOE], s * g.w.px[q + Physics.RTOE]);
                    double xi = s * (g.w.comX(f) + g.w.comVX(f) * Math.sqrt(g.w.comY(f) / Physics.G));
                    sb.append(String.format(Locale.ROOT, " %c %-12s -> %-15s x %+5.2f v %+5.2f head %4.2f weight %+5.2f in [%+5.2f, %+5.2f]%s hp %3d st %3d |", f == 0 ? 'L' : 'R', cut(cmd[f], 9),
                        (g.move[f] == Moves.NONE ? "JOINTS" : Moves.NAME[g.move[f]]) + " " + g.frame[f], g.w.px[q + Physics.PELVIS], g.w.comVX(f), g.w.py[q + Physics.HEAD], xi, back, front,
                        g.offFeet(f, Game.CAP_M) != 0 ? "!" : " ", g.w.hp[f], g.stamina[f] / 1000));
                }
                System.out.println(sb);
            }
        };
        Arena.Result r = Arena.play(task[0] == 1 ? b : a, task[0] == 1 ? a : b, (int) task[1], task[2], o);
        System.out.println("  " + Arena.line(r));
        System.out.println("  " + cut(r.evlog.toString(), 2500));
    }

    /** "600:1200:20" = from 600 to 1200 mm by steps of 20. */
    static int[] range(String s) {
        String[] q = s.split(":");
        int lo = Integer.parseInt(q[0]), hi = Integer.parseInt(q[1]), st = Integer.parseInt(q[2]);
        int[] r = new int[(hi - lo) / st + 1];
        for (int i = 0; i < r.length; i++) r[i] = lo + i * st;
        return r;
    }

    public static void main(String[] a) {
        String mode = a[0];
        if (mode.equals("fuzz")) fuzz();
        else if (mode.equals("look")) look();
        else if (mode.equals("m")) m(a[1], a[2], Integer.parseInt(a[3]), a.length > 4 ? Long.parseLong(a[4]) : 1, a.length > 5 ? Integer.parseInt(a[5]) : 0, true);
        else if (mode.equals("trace")) trace(a[1], a[2], Integer.parseInt(a[3]), Long.parseLong(a[4]), Integer.parseInt(a[5]), Integer.parseInt(a[6]), Integer.parseInt(a[7]));
        else if (mode.equals("falls")) falls(Integer.parseInt(a[1]), a.length > 2 ? a[2] : "", a.length > 3 ? a[3] : "");
        else if (mode.equals("check")) check(Integer.parseInt(a[1]));
        else if (mode.equals("mirror")) mirror(Integer.parseInt(a[1]));
        else if (mode.equals("strike")) Scenes.strike(a.length > 1 ? range(a[1]) : new int[]{780, 820, 860, 900});
        else if (mode.equals("push")) Scenes.push();
        else if (mode.equals("chain")) Scenes.chain();
        else if (mode.equals("launch")) Scenes.launch();
        else if (mode.equals("tire")) Scenes.tire();
        else if (mode.equals("chaos")) Scenes.chaos(Integer.parseInt(a[1]));
        else if (mode.equals("size")) Scenes.size(a[1], a[2]);
        else if (mode.equals("tour")) tour(Integer.parseInt(a[1]), a.length > 2 ? a[2] : "", a.length > 3 ? a[3] : "");
        else if (mode.equals("shove")) { int[] q = a.length > 1 ? range(a[1]) : range("-3000:3000:250"); Scenes.shove(q[0], q[q.length - 1], q.length > 1 ? q[1] - q[0] : 250); }
        else throw new IllegalArgumentException(mode);
    }
}

package arena;

import com.codingame.game.engine.Game;
import com.codingame.game.engine.Physics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Plays one match between two bots through the text protocol, as the referee does, and keeps a trace of it. */
public final class Arena {
    public interface Hook { void turn(Game g, int turn, String[] cmd); }

    public static final class Opt {
        public boolean stamina = true, joints = true;
        public long[] botSeed;                 // null: seed * 31 + 1 and seed * 31 + 2
        public boolean checkSim, checkMirror, film;
        /** To prove that the checks can fail: 1 = the copy is given a back target wrong by a hundredth of a degree, 2 = the copy is not given the opponent's command, 3 = the mirror match is not given the swapped commands. */
        public int sabotage;
        public int maxTurns = Game.MAX_TURNS;
        public String[][] scripts = {{}, {}};
        public Hook hook;
        public Opt copy() {
            Opt o = new Opt(); o.stamina = stamina; o.joints = joints; o.botSeed = botSeed; o.checkSim = checkSim; o.checkMirror = checkMirror; o.film = film; o.sabotage = sabotage;
            o.maxTurns = maxTurns; o.scripts = scripts; o.hook = hook; return o;
        }
    }

    public static final class Result {
        public String a, b, end = "time", invalid = "", warnings = "";
        public int winner = -1, turns, dist, firstContact = -1, firstHit = -1, resets, farTurns;
        public long seed;
        public final int[] hp = new int[2], hpHit = new int[2], hpFall = new int[2], falls = new int[2], outs = new int[2], freeFalls = new int[2], recoveries = new int[2], recChains = new int[2];
        public final int[] stamina = new int[2], minStamina = {Game.FULL, Game.FULL};
        /** Per attacker: strikes that scored, strikes stopped by the arms, and the strikes that scored by the part they landed on (Physics.KNAME). */
        public final int[] hits = new int[2], blocked = new int[2]; public final int[][] hitsOn = new int[2][Physics.KNAME.length];
        public final long[] botNanos = new long[2], botMax = new long[2];
        public final StringBuilder evlog = new StringBuilder();
        public int simChecks, simBad, mirrorBad, negative; public String simFirst = "", mirrorFirst = "", negativeFirst = "";
        public final List<int[][]> film = new ArrayList<int[][]>();
        /** Every fall: {turn, fighter, 1 for a ring-out, 1 after a push, HP it lost to hits in the window, HP it took from the other, turns since the last contact, turns since its last recovery step started, 1 when it was in a recovery step}. */
        public final List<int[]> fallLog = new ArrayList<int[]>();
        public Game last;
    }

    static final char[] SIDE = {'L', 'R'};

    public static int startDistance(long seed) { return 1600 + 2 * new Random(seed * 7919 + 13).nextInt(401); }

    public static Result play(String na, String nb, int dist, long seed, Opt opt) {
        if (opt == null) opt = new Opt();
        if (dist <= 0) dist = startDistance(seed);
        Result r = new Result(); r.a = na; r.b = nb; r.dist = dist; r.seed = seed;
        Bot[] bot = {Bots.make(na, opt.botSeed != null ? opt.botSeed[0] : seed * 31 + 1, opt), Bots.make(nb, opt.botSeed != null ? opt.botSeed[1] : seed * 31 + 2, opt)};
        Game g = new Game(dist, opt.stamina, opt.joints);
        Game mir = opt.checkMirror ? new Game(dist, opt.stamina, opt.joints) : null;
        Obs[] obs = {new Obs(), new Obs()};
        for (int p = 0; p < 2; p++) { obs[p].init(g.initLine(p)); obs[p].side = p; }
        int[][] took = new int[opt.maxTurns + 1][2];
        int lastContact = -100;
        int[] recStart = {-100, -100};
        String[] cmd = new String[2];
        List<List<String>> in = new ArrayList<List<String>>();
        in.add(null); in.add(null);
        while (!g.over() && g.turn < opt.maxTurns) {
            int t = g.turn;
            for (int p = 0; p < 2; p++) {
                List<String> lines = g.inputLines(p);
                in.set(p, lines);
                long t0 = System.nanoTime();
                obs[p].read(lines);
                String c = bot[p].act(obs[p]);
                long dt = System.nanoTime() - t0;
                r.botNanos[p] += dt; if (t >= 20 && dt > r.botMax[p]) r.botMax[p] = dt;
                if (t == 0 && bot[p].look != null) c = "LOOK " + bot[p].look[0] + " " + bot[p].look[1] + " " + c;
                cmd[p] = c;
            }
            String e0 = g.apply(0, cmd[0]), e1 = g.apply(1, cmd[1]);
            for (int p = 0; p < 2; p++) if (g.warning[p] != null && r.warnings.length() < 400) r.warnings += "t" + t + " " + SIDE[p] + ": " + g.warning[p] + "; ";
            if (e0 != null || e1 != null) {           // an invalid output loses at once; both: a draw
                r.end = "invalid"; r.winner = e0 != null && e1 != null ? -1 : e0 != null ? 1 : 0;
                r.invalid = (e0 != null ? "L: " + e0 + " " : "") + (e1 != null ? "R: " + e1 : "");
                r.evlog.append("t").append(t).append(' ').append(r.invalid.trim()).append("; ");
                break;
            }
            int[][] film = null;
            if (opt.film) { film = new int[4][2 * Physics.N]; g.film = film; }
            g.simulate();
            if (film != null) r.film.add(film);
            boolean[] inStep = new boolean[2];           // a step to stay on its feet began this turn
            for (int p = 0; p < 2; p++) if (g.stepDir[p] != 0) {
                inStep[p] = true;
                r.recoveries[p]++; if (t - recStart[p] <= 8) r.recChains[p]++;
                recStart[p] = t;
                r.evlog.append('t').append(t).append(' ').append(SIDE[p]).append(g.stepDir[p] < 0 ? " steps back; " : " steps forward; ");
            }
            // ---- the trace of the turn
            Physics w = g.w;
            for (int f = 0; f < 2; f++) r.blocked[f] += w.blockedBy[f];
            for (double[] e : w.hitEvents) {
                int att = (int) e[1];
                r.hits[att]++; r.hitsOn[att][Physics.SH[(int) e[3]][2]]++;
                r.evlog.append('t').append(t).append(' ').append(SIDE[att]).append(' ').append(Physics.KNAME[Physics.SH[(int) e[2]][2]]).append('>')
                    .append(Physics.KNAME[Physics.SH[(int) e[3]][2]]).append(' ').append((int) e[5]).append("; ");
            }
            for (int f = 0; f < 2; f++) { took[t][f] = g.hitLoss[f]; r.hpHit[f] += g.hitLoss[f]; }
            if (w.contactSteps > 0) { lastContact = t; if (r.firstContact < 0) r.firstContact = t; }
            if (!w.hitEvents.isEmpty() && r.firstHit < 0) r.firstHit = t;
            for (int f = 0; f < 2; f++) if (g.down[f] || g.out[f]) {
                if (g.down[f]) r.falls[f]++; else r.outs[f]++;
                r.evlog.append('t').append(t).append(' ').append(SIDE[f]).append(g.down[f] ? " DOWN(" : " OUT(");
                int tk = 0, gv = 0;
                for (int u = Math.max(0, t - Game.WINDOW); u <= t; u++) { tk += took[u][f]; gv += took[u][1 - f]; }
                int tk10 = 0, gv10 = 0;
                for (int u = Math.max(0, t - 10); u <= t; u++) { tk10 += took[u][f]; gv10 += took[u][1 - f]; }
                r.fallLog.add(new int[]{t, f, g.down[f] ? 0 : 1, g.pushFall[f] ? 1 : 0, tk, gv, Math.min(999, t - lastContact), Math.min(999, t - recStart[f]), inStep[f] ? 1 : 0, tk10, gv10});
                if (g.pushFall[f]) { r.freeFalls[f]++; r.hpFall[f] += Game.PUSH_FALL; r.evlog.append("-" + Game.PUSH_FALL + ", push); "); }
                else {
                    r.hpFall[f] += Game.FALL;
                    r.evlog.append("-20, took ").append(tk).append(" gave ").append(gv).append(" in " + Game.WINDOW + " turns").append(t - lastContact > Game.WINDOW ? ", no contact); " : "); ");
                }
            }
            if (g.stoodUp) { r.resets++; lastContact = -100; recStart[0] = recStart[1] = -100; for (int u = 0; u <= t; u++) took[u][0] = took[u][1] = 0; }
            if (Math.abs(w.px[Physics.PELVIS] - w.px[Physics.NP + Physics.PELVIS]) > 1.3) r.farTurns++;
            for (int f = 0; f < 2; f++) if (g.stamina[f] < r.minStamina[f]) r.minStamina[f] = g.stamina[f];
            // ---- a copy rebuilt from what each player read, given both commands, must predict the next input exactly.
            // In the copy, the player is fighter 0, on the left, as the input shows it.
            if (opt.checkSim) for (int p = 0; p < 2; p++) {
                Game c = Game.fromInput(g.initLine(p), in.get(p), opt.stamina, opt.joints);
                c.turn = t;
                String e = c.apply(0, cmd[p]);
                if (opt.sabotage != 2) { String e2 = c.apply(1, cmd[1 - p]); if (e == null) e = e2; }
                if (opt.sabotage == 1) c.target[1][Physics.UB] += 1;
                c.simulate();
                List<String> want = g.inputLines(p), got = c.inputLines(0);
                String[] pts = want.get(2).split(" ");
                for (int i = 0; i < 2 * Physics.NP; i++) {      // every coordinate of the input is 0 or more: the origin is at the top left
                    int b = Game.PER_POINT * i;
                    if (Integer.parseInt(pts[b]) < 0 || Integer.parseInt(pts[b + 1]) < 0) { r.negative++; if (r.negativeFirst.isEmpty()) r.negativeFirst = "t" + t + " player " + p + " point " + i + ": " + pts[b] + " " + pts[b + 1]; }
                }
                r.simChecks++;
                if (e != null || !want.equals(got)) {
                    r.simBad++;
                    if (r.simFirst.isEmpty()) {
                        int k = 0;
                        while (k < want.size() && want.get(k).equals(got.get(k))) k++;
                        r.simFirst = "t" + t + " player " + p + " line " + k + ": referee \"" + (k < want.size() ? want.get(k) : "") + "\" copy \"" + (k < got.size() ? got.get(k) : "") + "\"" + (e != null ? " (" + e + ")" : "");
                    }
                }
            }
            // ---- the same match with the sides swapped must be the mirror image, bit for bit
            if (mir != null) {
                mir.turn = t;
                if (opt.sabotage == 3) { mir.apply(0, cmd[0]); mir.apply(1, cmd[1]); } else { mir.apply(0, cmd[1]); mir.apply(1, cmd[0]); }
                mir.simulate();
                String d = mirrorDiff(g, mir);
                if (d != null) { r.mirrorBad++; if (r.mirrorFirst.isEmpty()) r.mirrorFirst = "t" + t + " " + d; }
            }
            if (opt.hook != null) opt.hook.turn(g, t, cmd);
        }
        r.turns = g.turn; r.hp[0] = g.w.hp[0]; r.hp[1] = g.w.hp[1]; r.stamina[0] = g.stamina[0]; r.stamina[1] = g.stamina[1];
        if (!r.end.equals("invalid")) {
            r.winner = g.winner();
            r.end = g.w.hp[0] <= 0 && g.w.hp[1] <= 0 ? "double KO" : g.w.hp[0] <= 0 || g.w.hp[1] <= 0 ? "KO" : "time";
        }
        r.last = g;
        return r;
    }

    /** Null when m is exactly the mirror image of g with the fighters swapped; otherwise the first difference. */
    public static String mirrorDiff(Game g, Game m) {
        int NP = Physics.NP, NS = Physics.NS;
        for (int f = 0; f < 2; f++) {
            int o = 1 - f;
            for (int i = 0; i < NP; i++) {
                int a = f * NP + i, b = o * NP + i;
                if (m.w.px[b] != -g.w.px[a] || m.w.py[b] != g.w.py[a] || m.w.vx[b] != -g.w.vx[a] || m.w.vy[b] != g.w.vy[a])
                    return String.format(Locale.ROOT, "%s of fighter %d: (%.6f, %.6f) v (%.4f, %.4f) against mirrored (%.6f, %.6f) v (%.4f, %.4f)", Physics.PNAME[i], f,
                        g.w.px[a], g.w.py[a], g.w.vx[a], g.w.vy[a], -m.w.px[b], m.w.py[b], -m.w.vx[b], m.w.vy[b]);
            }
            if (m.w.hp[o] != g.w.hp[f]) return "HP " + g.w.hp[f] + " against " + m.w.hp[o];
            if (m.stamina[o] != g.stamina[f]) return "stamina " + g.stamina[f] + " against " + m.stamina[o];
            if (m.move[o] != g.move[f] || m.frame[o] != g.frame[f] || m.rise[o] != g.rise[f]) return "move of fighter " + f;
            for (int j = 0; j < Physics.NJ; j++) if (m.target[o][j] != g.target[f][j] || m.strength[o][j] != g.strength[f][j]) return "motor " + Physics.JNAME[j] + " of fighter " + f + ": " + g.target[f][j] + " against " + m.target[o][j];
            for (int k = 0; k < NS; k++) if (m.w.apart[o * NS + k] != g.w.apart[f * NS + k]) return "contact counter " + k + " of fighter " + f;
            if (m.w.roundLeg[o] != g.w.roundLeg[f]) return "round flag of fighter " + f;
            if (m.down[o] != g.down[f] || m.out[o] != g.out[f] || m.pushFall[o] != g.pushFall[f]) return "fall of fighter " + f;
        }
        if (m.sinceContact != g.sinceContact || m.sinceHit != g.sinceHit) return "contact or hit counters";
        return null;
    }

    public static String describe(Result r) {
        String w = r.winner < 0 ? "draw" : (r.winner == 0 ? r.a : r.b) + " wins";
        return String.format(Locale.ROOT, "%s %d-%d %s, %s, %s at t%d", r.a, r.hp[0], r.hp[1], r.b, w, r.end, r.turns);
    }

    public static String line(Result r) {
        return String.format(Locale.ROOT, "%s | HP lost to hits %d/%d, to falls %d/%d | falls %d/%d (pushed %d/%d) outs %d/%d | first contact t%d, first hit t%d | resets %d | far %d%% | recovery steps %d/%d | bot ms/turn %.1f/%.1f (max %.0f/%.0f)",
            describe(r), r.hpHit[0], r.hpHit[1], r.hpFall[0], r.hpFall[1], r.falls[0], r.falls[1], r.freeFalls[0], r.freeFalls[1], r.outs[0], r.outs[1], r.firstContact, r.firstHit, r.resets,
            100 * r.farTurns / Math.max(1, r.turns), r.recoveries[0], r.recoveries[1], r.botNanos[0] / 1e6 / Math.max(1, r.turns), r.botNanos[1] / 1e6 / Math.max(1, r.turns), r.botMax[0] / 1e6, r.botMax[1] / 1e6);
    }
}

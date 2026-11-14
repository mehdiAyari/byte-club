package arena;

import com.codingame.game.engine.Game;
import com.codingame.game.engine.Physics;
import com.codingame.game.view.FightModule;
import com.codingame.gameengine.runner.MultiplayerGameRunner;
import com.codingame.gameengine.runner.simulate.GameResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Plays the same match twice: through the real referee (separate bot programs, CodinGame's runner) and in the bench.
 * Every turn must agree: the points shown by the viewer (unpacked as the viewer does), the HP, and the final scores.
 * Usage: RefCheck league seed A B [A B ...]
 */
public final class RefCheck {
    /** The viewer's unpacking, in Java: the 4 quarter-turns of a turn from the positions before it. */
    static int[][] unpack(String text, int[] from) {
        int n = from.length, k = 0, half = FightModule.DIGITS.length() / 2, base = FightModule.DIGITS.length();
        int[][] out = new int[4][n];
        int[] last = from;
        for (int q = 0; q < 4; q++) {
            for (int i = 0; i < n; i++) {
                int z = FightModule.DIGITS.indexOf(text.charAt(k++));
                if (z >= half) z = half + (z - half) * base + FightModule.DIGITS.indexOf(text.charAt(k++));
                out[q][i] = last[i] + ((z & 1) != 0 ? -((z + 1) >> 1) : (z >> 1));
            }
            last = out[q];
        }
        if (k != text.length()) throw new IllegalStateException("packed text longer than expected");
        return out;
    }

    static int[] ints(JsonArray a) { int[] r = new int[a.size()]; for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsInt(); return r; }

    public static void main(String[] args) {
        int league = Integer.parseInt(args[0]);
        long seed0 = Long.parseLong(args[1]);
        int bad = 0, n = 0;
        for (int m = 2; m + 1 < args.length; m += 2, n++) {
            String a = args[m], b = args[m + 1];
            long seed = seed0 + n;
            int dist = Arena.startDistance(seed);
            MultiplayerGameRunner runner = new MultiplayerGameRunner();
            runner.setLeagueLevel(league);
            runner.setSeed(seed);
            Properties p = new Properties();
            p.setProperty("distance", String.valueOf(dist));
            runner.setGameParameters(p);
            runner.addAgent(Agent.commandLine(a, seed * 31 + 1, league), a);
            runner.addAgent(Agent.commandLine(b, seed * 31 + 2, league), b);
            GameResult res = runner.simulate();
            if (a.startsWith("Sleep:") || a.startsWith("Quit:") || b.startsWith("Sleep:") || b.startsWith("Quit:")) {     // not bench bots: only what the referee did
                int last = 0;
                for (int i = 0; i < res.summaries.size(); i++) if (!res.summaries.get(i).isEmpty()) last = i;
                System.out.println(String.format("league %d  %-8s vs %-8s seed %d dist %d: scores %d %d, %d frames, fail cause %s, tooltips %s", league, a, b, seed, dist,
                    res.scores.get(0), res.scores.get(1), res.views.size(), res.failCause, res.tooltips));
                System.out.println("    last summary: " + res.summaries.get(last).replace("\n", "\n    "));
                String lastView = res.views.get(res.views.size() - 1);
                System.out.println("    last view: " + lastView.substring(lastView.indexOf('\n') + 1));
                continue;
            }

            Arena.Opt opt = new Arena.Opt();
            opt.stamina = league >= 3; opt.joints = league >= 2; opt.film = true;
            final List<int[]> hp = new ArrayList<int[]>();
            opt.hook = new Arena.Hook() { public void turn(Game g, int t, String[] cmd) { hp.add(new int[]{g.w.hp[0], g.w.hp[1]}); } };
            Arena.Result r = Arena.play(a, b, dist, seed, opt);

            // ---- the frames of the viewer
            List<JsonObject> key = new ArrayList<JsonObject>();
            long bytes = 0; int frames = 0;
            for (String v : res.views) {
                bytes += v.length(); frames++;
                int nl = v.indexOf('\n');
                if (v.startsWith("KEY_FRAME")) key.add(JsonParser.parseString(v.substring(nl + 1)).getAsJsonObject());
            }
            String first = "";
            int turnsSeen = 0, packed = 0;
            JsonObject global = key.get(0).getAsJsonObject("global").getAsJsonObject(FightModule.NAME);
            int[] start = ints(global.getAsJsonArray("pos")), base = start;
            for (int k = 1; k < key.size(); k++) {
                JsonObject f = key.get(k).getAsJsonObject(FightModule.NAME);
                if (f == null || !f.has("p")) continue;
                int t = turnsSeen++;
                packed += f.get("p").getAsString().length();
                int[][] sub = unpack(f.get("p").getAsString(), base);
                base = f.has("r") ? start : sub[3];
                if (t >= r.film.size()) { if (first.isEmpty()) first = "turn " + t + ": the referee played more turns than the bench"; continue; }
                int[][] want = r.film.get(t);
                for (int q = 0; q < 4 && first.isEmpty(); q++) for (int i = 0; i < want[q].length; i++) if (want[q][i] != sub[q][i]) {
                    first = "turn " + t + " quarter " + q + " " + Physics.PNAME[(i / 2) % Physics.NP] + " of fighter " + i / 2 / Physics.NP + (i % 2 == 0 ? " x" : " y") + ": bench " + want[q][i] + " viewer " + sub[q][i];
                    break;
                }
                int[] h = ints(f.getAsJsonArray("h"));
                if (first.isEmpty() && (h[0] != hp.get(t)[0] || h[1] != hp.get(t)[1])) first = "turn " + t + ": HP bench " + hp.get(t)[0] + " " + hp.get(t)[1] + " viewer " + h[0] + " " + h[1];
            }
            if (first.isEmpty() && turnsSeen != r.turns) first = "turns: bench " + r.turns + " referee " + turnsSeen;
            int s0 = res.scores.get(0), s1 = res.scores.get(1);
            int w0 = r.end.equals("invalid") ? (r.winner == 0 ? Math.max(0, r.hp[0]) : -1) : Math.max(0, r.hp[0]), w1 = r.end.equals("invalid") ? (r.winner == 1 ? Math.max(0, r.hp[1]) : -1) : Math.max(0, r.hp[1]);
            if (first.isEmpty() && (s0 != w0 || s1 != w1)) first = "scores: bench " + w0 + " " + w1 + " referee " + s0 + " " + s1;
            int longest = 0, lines = 0;
            for (String s : res.summaries) { longest = Math.max(longest, s.length()); if (!s.isEmpty()) lines++; }
            if (res.failCause != null) first = "the referee failed: " + res.failCause;
            if (!first.isEmpty()) bad++;
            System.out.println(String.format("league %d  %-8s vs %-8s seed %d dist %d: %3d turns, scores %3d %3d (%s) | viewer %d frames, %d bytes (%d of packed points), longest summary %d chars, %d tooltips | %s",
                league, a, b, seed, dist, turnsSeen, s0, s1, r.end, frames, bytes, packed, longest, res.tooltips.size(), first.isEmpty() ? "same as the bench" : "DIFFERENT: " + first));
            if (System.getProperty("show") != null) {
                int shown = 0;
                for (String s : res.summaries) if (!s.isEmpty() && shown++ < 14) System.out.println("    " + s.replace("\n", "\n    "));
                for (String e : res.errors.get("referee")) if (e != null && !e.isEmpty() && shown++ < 20) System.out.println("    referee stderr: " + e);
            }
        }
        System.out.println(n + " matches, " + bad + " different");
        System.exit(0);
    }
}

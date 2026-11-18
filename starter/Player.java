import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Byte Club: a starter that simulates. Paste this whole file in the IDE.
 *
 * Each turn, it rebuilds the match from the input, tries each move of MOVES on a copy of the engine, plays AHEAD turns
 * ahead, and keeps the move with the best score. The opponent is played as going on with what it is doing (WAIT).
 * It is an example of how to simulate, not a strong player: the search, the score and the opponent's model are to be written.
 *
 * Below the Player class are the four classes of the game's engine (Command, Moves, Physics, Game) without their comments.
 * Their source with comments is in src/main/java/com/codingame/game/engine.
 */
class Player {
    /** The league being played: 1 = ready-made moves, 2 = JOINTS too, 3 = stamina too. The input does not give it. */
    static final int LEAGUE = 1;
    static final String[] MOVES = {"STANCE", "GUARD", "DUCK", "JAB", "CROSS", "OVERHAND", "KICK", "STEP_FORWARD", "STEP_BACK"};
    static final int AHEAD = 4;
    /** No new move is tried after this time in the turn (the limit is 50 ms). */
    static final long BUDGET_NS = 20_000_000L;

    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String init = in.readLine();
        Game copy = null;
        while (true) {
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < Game.INPUT_LINES; i++) { String l = in.readLine(); if (l == null) return; lines.add(l); }
            long start = System.nanoTime();
            // the input is the whole state: the player is fighter 0, the opponent is fighter 1
            Game now = Game.fromInput(init, lines, LEAGUE >= 3, LEAGUE >= 2);
            if (copy == null) {
                // first turn (1 second allowed): run the engine for a while, so that Java compiles it before the 50 ms turns
                copy = Game.fromInput(init, lines, LEAGUE >= 3, LEAGUE >= 2);
                while (System.nanoTime() - start < 400_000_000L) for (String m : MOVES) score(copy, now, m);
                start = System.nanoTime();
            }
            String best = "STANCE";
            double bestScore = -1e9;
            int tried = 0;
            for (String m : MOVES) {
                if (System.nanoTime() - start > BUDGET_NS) break;
                double s = score(copy, now, m);
                tried++;
                if (s > bestScore) { bestScore = s; best = m; }
            }
            System.err.println(tried + " moves tried, " + tried * AHEAD + " turns simulated in " + (System.nanoTime() - start) / 1000000 + " ms");
            System.out.println(best);
        }
    }

    /** Plays the move, then AHEAD - 1 more turns, on a copy of the match. The score: the HP ahead, then being 80 cm away. */
    static double score(Game copy, Game now, String move) {
        copy.copyFrom(now);
        for (int t = 0; t < AHEAD && !copy.over(); t++) {
            copy.apply(0, t == 0 ? move : "WAIT");
            copy.apply(1, "WAIT");
            copy.simulate();
        }
        double distance = Math.abs(copy.w.px[Physics.NP + Physics.PELVIS] - copy.w.px[Physics.PELVIS]);
        return copy.w.hp[0] - copy.w.hp[1] - Math.abs(distance - 0.8);
    }
}

final class Command {
	public static final int WAIT = 0, MOVE = 1, JOINTS = 2;
	public static final int SKINS = 8, HAIRS = 10, MESSAGE_MAX = 40;
	public int type = WAIT, move = Moves.NONE;
	public final int[] target = new int[Physics.NJ], strength = new int[Physics.NJ];
	public final boolean[] keepTarget = new boolean[Physics.NJ], keepStrength = new boolean[Physics.NJ];
	public boolean round;
	public int skin = -1, hair = -1;
	public String message = "";
	public String error;
	public String warning;
	private Command fail(String why) { error = why; return this; }
	private void note(String why) { warning = warning == null ? why : warning + "; " + why; }
	private Command waitInstead(String why) { note(why + ": WAIT is played instead"); type = WAIT; move = Moves.NONE; round = false; return this; }
	public static Command move(int m) { Command c = new Command(); c.type = MOVE; c.move = m; return c; }
	public static Command parse(String line, boolean firstTurn, boolean jointsAllowed) {
		Command c = new Command();
		if (line == null) return c.fail("no output");
		String[] q = line.trim().split("\\s+");
		if (q.length == 0 || q[0].isEmpty()) return c.fail("empty output");
		int i = 0;
		if (q[0].equals("LOOK")) {
			i = 1;
			String skin = i < q.length && numberLike(q[i]) ? q[i++] : null;
			String hair = skin != null && i < q.length && numberLike(q[i]) ? q[i++] : null;
			if (!firstTurn) c.note("LOOK is ignored: the look is chosen on the first turn only");
			else if (hair != null && isIndex(skin, SKINS) && isIndex(hair, HAIRS)) { c.skin = skin.charAt(0) - '0'; c.hair = hair.charAt(0) - '0'; }
			else c.note("LOOK is ignored: it takes a skin from 0 to " + (SKINS - 1) + " and a hair from 0 to " + (HAIRS - 1));
			if (i == q.length) return c;
		}
		String name = q[i++];
		if (name.equals("JOINTS")) {
			if (!jointsAllowed) return c.waitInstead("JOINTS is not available in this league");
			int n = Physics.NJ;
			if (q.length < i + 2 * n) return c.waitInstead("JOINTS takes " + 2 * n + " values (a target and a strength for each joint), found " + (q.length - i));
			StringBuilder out = new StringBuilder();
			for (int j = 0; j < n; j++) {
				String t = q[i + 2 * j], s = q[i + 2 * j + 1];
				if (t.equals("*")) c.keepTarget[j] = true;
				else {
					if (!isNumber(t)) return c.waitInstead("JOINTS: the target of " + Physics.JNAME[j] + " is not a number: " + clip(t));
					long v = new BigDecimal(t).movePointRight(2).setScale(0, RoundingMode.HALF_EVEN).longValue();
					c.target[j] = clamp(v, Physics.JMINC[j], Physics.JMAXC[j]);
					if (c.target[j] != v) out.append(out.length() > 0 ? ", " : "").append(Physics.JNAME[j]).append(" target ").append(Game.deg(c.target[j]));
				}
				if (s.equals("*")) c.keepStrength[j] = true;
				else {
					if (!isNumber(s)) return c.waitInstead("JOINTS: the strength of " + Physics.JNAME[j] + " is not a number: " + clip(s));
					long v = new BigDecimal(s).setScale(0, RoundingMode.HALF_EVEN).longValue();
					c.strength[j] = clamp(v, 0, 100);
					if (c.strength[j] != v) out.append(out.length() > 0 ? ", " : "").append(Physics.JNAME[j]).append(" strength ").append(c.strength[j]);
				}
			}
			i += 2 * n;
			if (i < q.length && q[i].equals("ROUND")) { c.round = true; i++; }
			else if (i < q.length && q[i].equalsIgnoreCase("ROUND")) return c.waitInstead("ROUND must be written in capital letters");
			if (i < q.length && (q[i].equals("*") || numberLike(q[i]))) return c.waitInstead("JOINTS takes exactly " + 2 * n + " values");
			if (out.length() > 0) c.note("JOINTS: out of range, played as " + out);
			c.type = JOINTS;
		} else if (!name.equals("WAIT")) {
			int m = Moves.index(name);
			if (m < 0) return c.fail("unknown command: " + clip(name));
			c.type = MOVE; c.move = m;
		}
		StringBuilder sb = new StringBuilder();
		for (; i < q.length && sb.length() < MESSAGE_MAX; i++) {
			if (sb.length() > 0) sb.append(' ');
			for (int k = 0; k < q[i].length(); k++) { char ch = q[i].charAt(k); if (ch > 32 && !(ch >= 127 && ch < 160) && !Character.isSurrogate(ch)) sb.append(ch); }
		}
		c.message = sb.length() > MESSAGE_MAX ? sb.substring(0, MESSAGE_MAX) : sb.toString();
		return c;
	}
	static boolean isIndex(String s, int n) { return s.length() == 1 && s.charAt(0) >= '0' && s.charAt(0) - '0' < n; }
	public static boolean isNumber(String s) {
		int n = s.length(), i = 0, d = 0;
		if (i < n && s.charAt(i) == '-') i++;
		while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9') { i++; d++; }
		if (d < 1 || d > 6) return false;
		if (i == n) return true;
		if (s.charAt(i++) != '.') return false;
		d = 0;
		while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9') { i++; d++; }
		return i == n && d >= 1 && d <= 6;
	}
	static boolean numberLike(String s) {
		for (int k = 0; k < s.length(); k++) { char ch = s.charAt(k); if (!(ch >= '0' && ch <= '9') && ch != '-' && ch != '+' && ch != '.') return false; }
		return true;
	}
	static int clamp(long v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : (int) v; }
	static String clip(String s) {
		StringBuilder sb = new StringBuilder();
		for (int k = 0; k < s.length() && sb.length() < 20; k++) { char ch = s.charAt(k); sb.append(ch > 32 && ch < 127 ? ch : '?'); }
		return sb.toString();
	}
}

final class Moves {
	public static final int NONE = -1;
	public static final int STANCE = 0, GUARD = 1, RELAX = 2, DUCK = 3, JAB = 4, CROSS = 5, OVERHAND = 6, KICK = 7, HIGH_KICK = 8, STEP_FORWARD = 9, STEP_BACK = 10, JUMP = 11, COUNT = 12;
	public static final String[] NAME = {"STANCE", "GUARD", "RELAX", "DUCK", "JAB", "CROSS", "OVERHAND", "KICK", "HIGH_KICK", "STEP_FORWARD", "STEP_BACK", "JUMP"};
	public static final boolean[] HELD = {true, true, true, true, false, false, false, false, false, false, false, false};
	public static final boolean[] ROUND = {false, false, false, false, false, false, false, false, true, false, false, false};
	public static final int[] LENGTH = {1, 1, 1, 1, 3, 3, 4, 6, 12, 7, 7, 7};
	public static final int[][][] T = {
		{
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 3266, 2803, -77, 358, 2538, -2720}
		},
		{
			{2000, 500, 500, 1500, 10500, 10000, -1000, 10500, 10000, 3266, 2803, -77, 358, 2538, -2720}
		},
		{
			{1000, 500, 500, 1500, 1000, 2000, -1000, 1000, 2000, 3266, 2803, -77, 358, 2538, -2720}
		},
		{
			{2000, 1500, 3500, 1500, 10500, 10000, -1000, 10500, 10000, 7896, 6551, -1743, 4621, 6420, -4887}
		},
		{
			{300, 1300, 1600, 5100, 9200, 1200, -5700, 9000, 10500, 3848, 3279, -1061, -148, 672, -1730},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 3266, 2803, -77, 358, 2538, -2720},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 3266, 2803, -77, 358, 2538, -2720}
		},
		{
			{2500, -900, 2400, -700, 9500, 7500, 5600, 10600, 0, 3958, 3645, -1097, -246, 726, -2222},
			{2500, -900, 2400, -700, 9500, 7500, 5600, 10600, 0, 3958, 3645, -1097, -246, 726, -2222},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 3266, 2803, -77, 358, 2538, -2720}
		},
		{
			{1000, 400, 1000, 1200, 9500, 7500, -2300, 10500, 11000, 3562, 3243, -582, 27, 1705, -2578},
			{1000, 400, 1000, 1200, 9500, 7500, -2300, 10500, 11000, 3562, 3243, -582, 27, 1705, -2578},
			{2500, -1000, 2500, -2200, 9500, 7500, 5400, 10800, 400, 3925, 3330, -825, 147, 1250, -2273},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 3266, 2803, -77, 358, 2538, -2720}
		},
		{
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 4013, 1765, 1214, 2831, 4131, -3294},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 13000, 9800, 3573, -96, 4174, -2360},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 7300, 2900, 3000, 1304, 5639, -2360},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 13000, 9781, 3000, -96, 4174, -2360},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 5226, 4136, -483, 622, 4484, -3562},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 3353, 4136, -483, 952, 4484, -3562}
		},
		{
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 3201, 2594, 487, 535, 3638, -3554},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 3201, 2594, 487, 535, 3638, -3554},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 3201, 2594, 487, 535, 3638, -3554},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 3201, 2594, 487, 535, 3638, -3554},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 3201, 2594, 487, 535, 3638, -3554},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 12400, 10000, 3000, 652, 4446, -3404},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 12700, 100, 3000, 652, 4446, -3404},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 12700, 100, 3000, 652, 4446, -3404},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 12400, 10000, 3000, 652, 4446, -3404},
			{1000, -2000, -2053, 1500, 9500, 7500, -1000, 9000, 10500, 5812, 0, 545, 946, 4250, -4000},
			{1000, -2000, -2053, 1500, 9500, 7500, -1000, 9000, 10500, 3132, 2417, 545, 946, 4250, -4000},
			{1000, -2000, -2053, 1500, 9500, 7500, -1000, 9000, 10500, 1860, 5277, 545, 946, 4250, -4000}
		},
		{
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 6659, 5893, 1756, -701, 1642, -3002},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 5177, 3903, 294, 753, 3974, -4201},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 5177, 3903, 294, 753, 3974, -4201},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 4373, 2888, 175, 1881, 4035, -4574},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 2725, 2803, -77, -182, 2538, -2720},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 2725, 2803, -77, -182, 2538, -2720},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 2725, 2803, -77, -182, 2538, -2720}
		},
		{
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 4690, 3969, -139, 1082, 6593, -8031},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 3833, 1142, 2212, -1512, 1501, -3493},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 3833, 1142, 2212, -1512, 1501, -3493},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 3474, 2364, 479, -1573, 2527, -3379},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 3474, 2364, 479, -1573, 2527, -3379},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 2725, 2803, -77, -182, 2538, -2720},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 2725, 2803, -77, -182, 2538, -2720}
		},
		{
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 6283, 5853, -1191, 3191, 7244, -4000},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 1041, 2015, 3800, -1554, 261, 5000},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 822, 1200, 3800, 1091, 1200, 2724},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 4098, 8394, -2905, -1075, 6822, -4000},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 4071, 4908, -1654, 913, 7844, -3511},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 4050, 4519, -273, -1427, 2618, -2379},
			{1000, 500, 500, 1500, 9500, 7500, -1000, 9000, 10500, 2278, 4850, -359, -1041, 3252, -2866}
		}
	};
	public static final int[][][] S = {
		{
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 80, 80, 80, 80, 80, 80}
		},
		{
			{80, 60, 60, 50, 80, 80, 50, 80, 80, 80, 80, 80, 80, 80, 80}
		},
		{
			{80, 60, 60, 50, 10, 10, 50, 10, 10, 80, 80, 80, 80, 80, 80}
		},
		{
			{80, 60, 60, 50, 80, 80, 50, 80, 80, 80, 80, 80, 80, 80, 80}
		},
		{
			{80, 100, 100, 100, 100, 100, 100, 40, 40, 100, 100, 100, 100, 100, 100},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 80, 80, 80, 80, 80, 80},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 80, 80, 80, 80, 80, 80}
		},
		{
			{80, 100, 100, 100, 40, 40, 100, 100, 100, 100, 100, 100, 100, 100, 100},
			{80, 100, 100, 100, 40, 40, 100, 100, 100, 100, 100, 100, 100, 100, 100},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 80, 80, 80, 80, 80, 80}
		},
		{
			{80, 100, 100, 100, 40, 40, 100, 100, 100, 100, 100, 100, 100, 100, 100},
			{80, 100, 100, 100, 40, 40, 100, 100, 100, 100, 100, 100, 100, 100, 100},
			{80, 100, 100, 100, 40, 40, 100, 100, 100, 100, 100, 100, 100, 100, 100},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 80, 80, 80, 80, 80, 80}
		},
		{
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 94, 94, 94, 94, 94, 94},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 100, 100, 100, 100, 100, 100},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 100, 100, 100, 100, 100, 100},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 100, 40, 100, 100, 100, 100},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 100, 40, 100, 100, 100, 100},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 100, 100, 100, 100, 100, 100}
		},
		{
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 85, 85, 85, 85, 85, 85},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 85, 85, 85, 85, 85, 85},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 85, 85, 85, 85, 85, 85},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 85, 85, 85, 85, 85, 85},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 85, 85, 85, 85, 85, 85},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 100, 100, 100, 100, 100, 100},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 100, 100, 100, 100, 100, 100},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 100, 100, 100, 100, 100, 100},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 100, 100, 100, 100, 100, 100},
			{80, 53, 53, 50, 40, 40, 50, 40, 40, 100, 100, 100, 100, 100, 100},
			{80, 53, 53, 50, 40, 40, 50, 40, 40, 100, 100, 100, 100, 100, 100},
			{80, 53, 53, 50, 40, 40, 50, 40, 40, 100, 100, 100, 100, 100, 100}
		},
		{
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 95, 95, 95, 95, 95, 95},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 95, 95, 95, 95, 95, 95},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 95, 95, 95, 95, 95, 95},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 95, 95, 95, 95, 95, 95},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 95, 95, 95, 95, 95, 95},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 95, 95, 95, 95, 95, 95},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 95, 95, 95, 95, 95, 95}
		},
		{
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 93, 93, 93, 93, 93, 93},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 93, 93, 93, 93, 93, 93},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 93, 93, 93, 93, 93, 93},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 93, 93, 93, 93, 93, 93},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 93, 93, 93, 93, 93, 93},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 93, 93, 93, 93, 93, 93},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 93, 93, 93, 93, 93, 93}
		},
		{
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 100, 100, 100, 100, 100, 100},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 100, 100, 100, 100, 100, 100},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 100, 100, 100, 100, 100, 100},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 100, 100, 100, 100, 100, 100},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 33, 33, 33, 33, 33, 33},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 67, 67, 67, 67, 67, 67},
			{80, 60, 60, 50, 40, 40, 50, 40, 40, 83, 83, 83, 83, 83, 83}
		}
	};
	public static final int RISE = 4;
	public static final double[][][] LUNGE = new double[COUNT][][];
	static {
		LUNGE[JAB] = new double[][]{{0.113, 0.859, 16.3}, {0, 0.850, 5.4}, {0, 0.850, 5.4}};
		LUNGE[CROSS] = new double[][]{{0.092, 0.848, 14.1}, {0.092, 0.848, 14.1}, {0, 0.850, 5.4}};
		LUNGE[OVERHAND] = new double[][]{{0.044, 0.850, 9.0}, {0.044, 0.850, 9.0}, {0.075, 0.853, 14.3}, {0, 0.850, 5.4}};
	}
	public static final int[] STANCE_X = {115, 47, 16, 0, 0, 69, 369, 443, 12, 311, 264, 206, 200, 150, 370, -14, -220, -270, -50};
	public static final int[] STANCE_Y = {1625, 1437, 1259, 1080, 940, 1308, 1306, 1576, 1311, 1283, 1559, 540, 90, 30, 30, 490, 90, 30, 30};
	public static int index(String name) {
		for (int i = 0; i < COUNT; i++) if (NAME[i].equals(name)) return i;
		return NONE;
	}
	private Moves() { }
}

final class Physics {
	public static final double G = 9.81, RING = 3.0, MU = 0.9;
	public static final int STEPS = 40;
	public static final double DT = 0.0025, DRAG = 0.99975;
	public static final double PI = 3.141592653589793, DEG2RAD = 0.017453292519943295, RAD2DEG = 57.29577951308232;
	public static final int HEAD = 0, NECK = 1, CHEST = 2, WAIST = 3, PELVIS = 4,
		LSH = 5, LELB = 6, LHAND = 7, RSH = 8, RELB = 9, RHAND = 10,
		LKNEE = 11, LANK = 12, LHEEL = 13, LTOE = 14, RKNEE = 15, RANK = 16, RHEEL = 17, RTOE = 18, NP = 19;
	public static final String[] PNAME = {"HEAD", "NECK", "CHEST", "WAIST", "PELVIS", "LEAD_SHOULDER", "LEAD_ELBOW", "LEAD_HAND",
		"REAR_SHOULDER", "REAR_ELBOW", "REAR_HAND", "LEAD_KNEE", "LEAD_ANKLE", "LEAD_HEEL", "LEAD_TOES",
		"REAR_KNEE", "REAR_ANKLE", "REAR_HEEL", "REAR_TOES"};
	public static final double[] MASS = {5, 6, 9, 9, 12, 2, 1.5, 1.2, 2, 1.5, 1.2, 4, 2, 0.5, 1, 4, 2, 0.5, 1};
	public static final double[] RAD = {0.11, 0.12, 0.13, 0.12, 0.13, 0.06, 0.05, 0.065, 0.06, 0.05, 0.065, 0.08, 0.04, 0.03, 0.03, 0.08, 0.04, 0.03, 0.03};
	public static final double HEELX = 0.05, TOEX = 0.17, FOOTY = 0.06, ANKH = 0.09;
	public static final double LAH = 0.07810249675906655, LAT = 0.18027756377319948, LHT = 0.22000000000000003;
	public static final double GIRDLE = 0.13, UARM = 0.30, FARM = 0.28, THIGH = 0.45, SHIN = 0.45;
	public static final int[][] BONE = {{NECK, HEAD}, {CHEST, NECK}, {WAIST, CHEST}, {PELVIS, WAIST},
		{NECK, LSH}, {LSH, LELB}, {LELB, LHAND}, {NECK, RSH}, {RSH, RELB}, {RELB, RHAND},
		{PELVIS, LKNEE}, {LKNEE, LANK}, {LANK, LHEEL}, {LANK, LTOE}, {LHEEL, LTOE},
		{PELVIS, RKNEE}, {RKNEE, RANK}, {RANK, RHEEL}, {RANK, RTOE}, {RHEEL, RTOE}};
	public static final double[] BLEN = {0.20, 0.18, 0.18, 0.14, GIRDLE, UARM, FARM, GIRDLE, UARM, FARM,
		THIGH, SHIN, LAH, LAT, LHT, THIGH, SHIN, LAH, LAT, LHT};
	public static final boolean[] FOOTPT = new boolean[NP];
	static { FOOTPT[LANK] = FOOTPT[LHEEL] = FOOTPT[LTOE] = FOOTPT[RANK] = FOOTPT[RHEEL] = FOOTPT[RTOE] = true; }
	public static final int NK = 0, UB = 1, LB = 2, LG = 3, LS = 4, LE = 5, RG = 6, RS = 7, RE = 8, LH = 9, LK = 10, LA = 11, RH = 12, RK = 13, RA = 14, NJ = 15;
	public static final String[] JNAME = {"NK", "UB", "LB", "LG", "LS", "LE", "RG", "RS", "RE", "LH", "LK", "LA", "RH", "RK", "RA"};
	public static final String[] JLONG = {"NECK", "UPPER_BACK", "LOWER_BACK", "LEAD_GIRDLE", "LEAD_SHOULDER", "LEAD_ELBOW",
		"REAR_GIRDLE", "REAR_SHOULDER", "REAR_ELBOW", "LEAD_HIP", "LEAD_KNEE", "LEAD_ANKLE", "REAR_HIP", "REAR_KNEE", "REAR_ANKLE"};
	public static final int[] JA = {CHEST, WAIST, PELVIS, NECK, NECK, LSH, NECK, NECK, RSH, NECK, PELVIS, LANK, NECK, PELVIS, RANK};
	public static final int[] JB = {NECK, CHEST, WAIST, PELVIS, PELVIS, LELB, PELVIS, PELVIS, RELB, PELVIS, LKNEE, LKNEE, PELVIS, RKNEE, RKNEE};
	public static final int[] JJ = {NECK, CHEST, WAIST, NECK, LSH, LELB, NECK, RSH, RELB, PELVIS, LKNEE, LANK, PELVIS, RKNEE, RANK};
	public static final int[] JC = {HEAD, NECK, CHEST, LSH, LELB, LHAND, RSH, RELB, RHAND, LKNEE, LANK, LTOE, RKNEE, RANK, RTOE};
	public static final int[] JK = {-1, -1, -1, 1, 1, 1, 1, 1, 1, 1, -1, -1, 1, -1, -1};
	public static final double[] JOFF = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.9100889412489412, 0, 0, 1.9100889412489412};
	public static final double[] JMIN = {-30, -20, -25, -60, -60, 0, -60, -60, 0, -45, 0, -40, -45, 0, -40};
	public static final double[] JMAX = {45, 35, 40, 60, 180, 150, 60, 180, 150, 130, 150, 50, 130, 150, 50};
	public static final int[] JMINC = new int[NJ], JMAXC = new int[NJ];
	static { for (int j = 0; j < NJ; j++) { JMINC[j] = (int) Math.rint(JMIN[j] * 100); JMAXC[j] = (int) Math.rint(JMAX[j] * 100); } }
	public static final double[] TMAX = {40, 120, 150, 120, 80, 60, 120, 80, 60, 220, 220, 150, 220, 220, 150};
	public static final double[] WMAX = {10, 10, 10, 15, 40, 60, 15, 40, 60, 25, 50, 20, 25, 50, 20};
	public static final double[] KP = {229.1831180523293, 687.5493541569879, 859.4366926962349, 687.5493541569879, 458.3662361046586, 343.77467707849394, 687.5493541569879, 458.3662361046586, 343.77467707849394, 1260.5071492878112, 1260.5071492878112, 1718.8733853924698, 1260.5071492878112, 1260.5071492878112, 1718.8733853924698};
	public static final double[] KD = {4.541638539636288, 10.155412503859614, 11.354096349090721, 4.541638539636288, 9.574614729634385, 5.684612532563537, 4.541638539636288, 9.574614729634385, 5.684612532563537, 75.06308651605339, 28.623934862926816, 7.1809610472257885, 75.06308651605339, 28.623934862926816, 7.1809610472257885};
	public static final double[] KDFLAT = {4.541638539636288, 10.155412503859614, 11.354096349090721, 4.541638539636288, 9.574614729634385, 5.684612532563537, 4.541638539636288, 9.574614729634385, 5.684612532563537, 75.06308651605339, 28.623934862926816, 343.5465571863146, 75.06308651605339, 28.623934862926816, 343.5465571863146};
	public static final double[] JLO = {-0.5235987755982988, -0.3490658503988659, -0.4363323129985824, -1.0471975511965976, -1.0471975511965976, 0.0, -1.0471975511965976, -1.0471975511965976, 0.0, -0.7853981633974483, 0.0, -0.6981317007977318, -0.7853981633974483, 0.0, -0.6981317007977318};
	public static final double[] JHI = {0.7853981633974483, 0.6108652381980153, 0.6981317007977318, 1.0471975511965976, 3.141592653589793, 2.6179938779914944, 1.0471975511965976, 3.141592653589793, 2.6179938779914944, 2.2689280275926285, 2.6179938779914944, 0.8726646259971648, 2.2689280275926285, 2.6179938779914944, 0.8726646259971648};
	public static final double[] JCEN = {0.13089969389957473, 0.1308996938995747, 0.1308996938995747, 0.0, 1.0471975511965979, 1.3089969389957472, 0.0, 1.0471975511965979, 1.3089969389957472, 0.7417649320975901, 1.3089969389957472, 0.08726646259971649, 0.7417649320975901, 1.3089969389957472, 0.08726646259971649};
	public static final int[][] SH = {{HEAD, HEAD, 0}, {NECK, CHEST, 1}, {CHEST, WAIST, 1}, {WAIST, PELVIS, 1},
		{LSH, LELB, 2}, {LELB, LHAND, 2}, {LHAND, LHAND, 3}, {RSH, RELB, 2}, {RELB, RHAND, 2}, {RHAND, RHAND, 3},
		{PELVIS, LKNEE, 4}, {LKNEE, LANK, 6}, {LHEEL, LTOE, 5}, {PELVIS, RKNEE, 4}, {RKNEE, RANK, 6}, {RHEEL, RTOE, 5},
		{LKNEE, LKNEE, 7}, {RKNEE, RKNEE, 7}};
	public static final double[] SR = {0.11, 0.13, 0.12, 0.13, 0.05, 0.045, 0.065, 0.05, 0.045, 0.065, 0.08, 0.05, 0.03, 0.08, 0.05, 0.03, 0.075, 0.075};
	public static final int[] SIDE = {0, 0, 0, 0, 1, 1, 1, 2, 2, 2, 1, 1, 1, 2, 2, 2, 1, 2};
	public static final String[] KNAME = {"head", "torso", "arm", "glove", "thigh", "foot", "shin", "knee"};
	public static final int NS = 18, N = 2 * NP;
	public static final int REAR_FOREARM_SH = 8, REAR_GLOVE_SH = 9;
	public static final boolean[] LEADLEG = new boolean[NS];
	static { LEADLEG[10] = LEADLEG[11] = LEADLEG[12] = LEADLEG[16] = true; }
	public static double zone(int kind) { return kind == 0 ? 2 : kind == 1 ? 1 : (kind == 4 || kind == 5 || kind == 6) ? 0.5 : 0; }
	public static boolean striker(int kind) { return kind == 3 || kind == 5 || kind == 7; }
	public static final int[] STRIKERS = {6, 9, 12, 15, 16, 17};
	public static final int SEP = 10;
	public static final double FOOTMIN = 0.30, STRIKEV = 1.0;
	public static final double BIAS = 0.0025;
	public static final double[] RADN = new double[N], GRAV = new double[N];
	static { for (int i = 0; i < N; i++) { RADN[i] = RAD[i % NP]; GRAV[i] = -G * MASS[i % NP]; } }
	public static final int[] FACING = {1, -1};
	static final int[] KIND = new int[NS];
	static final boolean[] STRIKES = new boolean[NS];
	static final boolean[][] PASS = new boolean[NS][NS];
	static {
		for (int i = 0; i < NS; i++) { KIND[i] = SH[i][2]; STRIKES[i] = striker(KIND[i]); }
		for (int i = 0; i < NS; i++) for (int j = 0; j < NS; j++) PASS[i][j] = passShapes(i, j) || (KIND[i] == 7 && KIND[j] == 7);
	}
	public final double[] px = new double[N], py = new double[N], vx = new double[N], vy = new double[N];
	final double[] fx = new double[N], fy = new double[N], sx = new double[N], sy = new double[N], w = new double[N], dep = new double[N];
	public final boolean[] touch = new boolean[N];
	final boolean[] wasTouch = new boolean[N];
	public final double[][] target = new double[2][NJ], strength = new double[2][NJ];
	public final int[] hp = {100, 100};
	public final int[] damage = new int[2];
	public final boolean[] down = new boolean[2], out = new boolean[2];
	public final int[] downPt = {-1, -1};
	public final int[] fallStep = {-1, -1};
	public final double[] fatigue = {1, 1};
	public final int[] apart = new int[2 * NS];
	public final boolean[] roundLeg = new boolean[2];
	public final int[] blockedBy = new int[2];
	public int contactSteps, stepNo;
	public boolean record = true;
	public final List<double[]> hitEvents = new ArrayList<>();
	public final List<double[]> blockEvents = new ArrayList<>();
	boolean near, contactNow;
	final boolean[] touching = new boolean[2 * NS];
	final double[] bestHit = new double[2 * NS], bestSpeed = new double[2 * NS], bestCX = new double[2 * NS], bestCY = new double[2 * NS];
	final int[] bestTarget = new int[2 * NS];
	public Physics() {
		Arrays.fill(apart, SEP);
		for (int i = 0; i < N; i++) w[i] = 1 / MASS[i % NP];
	}
	public void copyFrom(Physics o) {
		System.arraycopy(o.px, 0, px, 0, N); System.arraycopy(o.py, 0, py, 0, N);
		System.arraycopy(o.vx, 0, vx, 0, N); System.arraycopy(o.vy, 0, vy, 0, N);
		System.arraycopy(o.touch, 0, touch, 0, N);
		for (int f = 0; f < 2; f++) {
			System.arraycopy(o.target[f], 0, target[f], 0, NJ); System.arraycopy(o.strength[f], 0, strength[f], 0, NJ);
			hp[f] = o.hp[f]; damage[f] = o.damage[f]; fatigue[f] = o.fatigue[f]; roundLeg[f] = o.roundLeg[f];
			down[f] = o.down[f]; out[f] = o.out[f]; downPt[f] = o.downPt[f]; fallStep[f] = o.fallStep[f]; blockedBy[f] = o.blockedBy[f];
		}
		System.arraycopy(o.apart, 0, apart, 0, apart.length);
		contactSteps = o.contactSteps; stepNo = o.stepNo;
	}
	public void stand(int f, int pelvisX) {
		int o = f * NP, s = FACING[f];
		for (int i = 0; i < NP; i++) {
			px[o + i] = (pelvisX + s * Moves.STANCE_X[i]) / 1000.0; py[o + i] = Moves.STANCE_Y[i] / 1000.0;
			vx[o + i] = 0; vy[o + i] = 0;
		}
	}
	public void setMotors(int f, int[] t, int[] s) {
		for (int j = 0; j < NJ; j++) { target[f][j] = t[j] / 100.0; strength[f][j] = s[j] / 100.0; }
	}
	public double angle(int f, int j) {
		int o = f * NP;
		double rx = px[o + JB[j]] - px[o + JA[j]], ry = py[o + JB[j]] - py[o + JA[j]];
		double cx = px[o + JC[j]] - px[o + JJ[j]], cy = py[o + JC[j]] - py[o + JJ[j]];
		double d = FACING[f] * JK[j] * atan2(rx * cy - ry * cx, rx * cx + ry * cy) - JOFF[j] - JCEN[j];
		if (d > PI) d -= 2 * PI; else if (d < -PI) d += 2 * PI;
		return JCEN[j] + d;
	}
	public double angSpeed(int f, int j) {
		int o = f * NP, A = o + JA[j], B = o + JB[j], J = o + JJ[j], C = o + JC[j];
		double rx = px[B] - px[A], ry = py[B] - py[A], cx = px[C] - px[J], cy = py[C] - py[J];
		double wc = (cx * (vy[C] - vy[J]) - cy * (vx[C] - vx[J])) / (cx * cx + cy * cy);
		double wr = (rx * (vy[B] - vy[A]) - ry * (vx[B] - vx[A])) / (rx * rx + ry * ry);
		return FACING[f] * JK[j] * (wc - wr) * RAD2DEG;
	}
	void motors(int f) {
		int o = f * NP, s = FACING[f];
		for (int j = 0; j < NJ; j++) {
			double S = strength[f][j];
			if (S <= 0) continue;
			int A = o + JA[j], B = o + JB[j], J = o + JJ[j], C = o + JC[j], k = JK[j];
			double rx = px[B] - px[A], ry = py[B] - py[A], cx = px[C] - px[J], cy = py[C] - py[J];
			double r2 = rx * rx + ry * ry, c2 = cx * cx + cy * cy;
			double th = angle(f, j);
			double wc = (cx * (vy[C] - vy[J]) - cy * (vx[C] - vx[J])) / c2;
			double wr = (rx * (vy[B] - vy[A]) - ry * (vx[B] - vx[A])) / r2;
			double om = s * k * (wc - wr);
			boolean flat = false;
			if (j == LA) flat = wasTouch[o + LHEEL] && wasTouch[o + LTOE];
			else if (j == RA) flat = wasTouch[o + RHEEL] && wasTouch[o + RTOE];
			double tau = KP[j] * (target[f][j] * DEG2RAD - th) - (flat ? KDFLAT[j] : KD[j]) * om;
			double lim = S * TMAX[j] * fatigue[f];
			if (tau * om > 0) lim *= Math.max(0, 1 - Math.abs(om) / WMAX[j]);
			if (tau > lim) tau = lim; else if (tau < -lim) tau = -lim;
			double T = s * k * tau;
			double fcx = -T * cy / c2, fcy = T * cx / c2, frx = T * ry / r2, fry = -T * rx / r2;
			fx[C] += fcx; fy[C] += fcy; fx[J] -= fcx; fy[J] -= fcy;
			fx[B] += frx; fy[B] += fry; fx[A] -= frx; fy[A] -= fry;
		}
	}
	void bones(int f) {
		int o = f * NP;
		for (int b = 0; b < BONE.length; b++) {
			int a = o + BONE[b][0], c = o + BONE[b][1];
			double dx = px[c] - px[a], dy = py[c] - py[a], d = Math.sqrt(dx * dx + dy * dy);
			double C = (d - BLEN[b]) / (d * (w[a] + w[c]));
			px[a] += dx * C * w[a]; py[a] += dy * C * w[a];
			px[c] -= dx * C * w[c]; py[c] -= dy * C * w[c];
		}
	}
	final int[] lp = new int[4];
	final double[] lgx = new double[4], lgy = new double[4];
	void limits(int f) {
		int o = f * NP, s = FACING[f];
		for (int j = 0; j < NJ; j++) {
			double th = angle(f, j), Cv;
			if (th > JHI[j]) Cv = th - JHI[j]; else if (th < JLO[j]) Cv = th - JLO[j]; else continue;
			int A = o + JA[j], B = o + JB[j], J = o + JJ[j], C = o + JC[j];
			double rx = px[B] - px[A], ry = py[B] - py[A], cx = px[C] - px[J], cy = py[C] - py[J];
			double r2 = rx * rx + ry * ry, c2 = cx * cx + cy * cy, sk = s * JK[j];
			lp[0] = C; lgx[0] = -sk * cy / c2; lgy[0] = sk * cx / c2;
			lp[1] = J; lgx[1] = -lgx[0]; lgy[1] = -lgy[0];
			lp[2] = B; lgx[2] = sk * ry / r2; lgy[2] = -sk * rx / r2;
			lp[3] = A; lgx[3] = -lgx[2]; lgy[3] = -lgy[2];
			for (int i = 1; i < 4; i++) for (int q = 0; q < i; q++) if (lp[q] == lp[i]) { lgx[q] += lgx[i]; lgy[q] += lgy[i]; lgx[i] = 0; lgy[i] = 0; }
			double W = 0;
			for (int i = 0; i < 4; i++) W += w[lp[i]] * (lgx[i] * lgx[i] + lgy[i] * lgy[i]);
			if (W < 1e-12) continue;
			double lam = Cv / W;
			for (int i = 0; i < 4; i++) { px[lp[i]] -= lam * w[lp[i]] * lgx[i]; py[lp[i]] -= lam * w[lp[i]] * lgy[i]; }
		}
	}
	void floor() {
		for (int i = 0; i < N; i++) {
			double r = RADN[i];
			if (py[i] < r) { dep[i] += r - py[i]; py[i] = r; touch[i] = true; }
			if (touch[i]) {
				double lim = MU * dep[i], dx = px[i] - sx[i];
				px[i] = Math.abs(dx) <= lim ? sx[i] : px[i] - Math.signum(dx) * lim;
			}
		}
	}
	static boolean low(int k) { return k == 5 || k == 6; }
	static boolean leg(int k) { return k == 4 || k == 6 || k == 7; }
	static boolean pass(int a, int b) { return (low(a) && low(b)) || (leg(a) && leg(b)); }
	public static boolean passShapes(int i, int j) { return pass(SH[i][2], SH[j][2]) || (SIDE[i] != 0 && SIDE[i] == SIDE[j]); }
	boolean guardHigh(int f) { return py[f * NP + RHAND] > py[f * NP + NECK] - 0.10; }
	static boolean roundMeets(int b, boolean guard) { return b == 0 || ((b == REAR_FOREARM_SH || b == REAR_GLOVE_SH) && guard); }
	boolean legInside(int g) {
		int o = g * NP, p = (1 - g) * NP;
		boolean guard = guardHigh(1 - g);
		for (int a = 0; a < NS; a++) if (LEADLEG[a]) for (int b = 0; b < NS; b++) {
			if (PASS[a][b] || roundMeets(b, guard)) continue;
			closest(px, py, o + SH[a][0], o + SH[a][1], p + SH[b][0], p + SH[b][1]);
			double rr = SR[a] + SR[b];
			if (cdx * cdx + cdy * cdy < rr * rr) return true;
		}
		return false;
	}
	boolean boxesOverlap() {
		double ax0 = 1e9, ax1 = -1e9, ay0 = 1e9, ay1 = -1e9, bx0 = 1e9, bx1 = -1e9, by0 = 1e9, by1 = -1e9;
		for (int i = 0; i < NP; i++) {
			ax0 = Math.min(ax0, px[i]); ax1 = Math.max(ax1, px[i]); ay0 = Math.min(ay0, py[i]); ay1 = Math.max(ay1, py[i]);
			int k = NP + i;
			bx0 = Math.min(bx0, px[k]); bx1 = Math.max(bx1, px[k]); by0 = Math.min(by0, py[k]); by1 = Math.max(by1, py[k]);
		}
		double m = 0.32;
		return bx0 - ax1 < m && ax0 - bx1 < m && by0 - ay1 < m && ay0 - by1 < m;
	}
	final int[] candI = new int[NS * NS], candJ = new int[NS * NS], cand1I = new int[NS * NS], cand1J = new int[NS * NS];
	final boolean[] candOn = new boolean[NS * NS];
	int nCand;
	final double[] bx0 = new double[2 * NS], bx1 = new double[2 * NS], by0 = new double[2 * NS], by1 = new double[2 * NS];
	void buildCandidates() {
		nCand = 0;
		if (!near) return;
		for (int f = 0; f < 2; f++) for (int i = 0; i < NS; i++) {
			int a = f * NP + SH[i][0], b = f * NP + SH[i][1], q = f * NS + i;
			double r = SR[i] + 0.015;
			bx0[q] = Math.min(px[a], px[b]) - r; bx1[q] = Math.max(px[a], px[b]) + r;
			by0[q] = Math.min(py[a], py[b]) - r; by1[q] = Math.max(py[a], py[b]) + r;
		}
		boolean g0 = guardHigh(0), g1 = guardHigh(1);
		for (int i = 0; i < NS; i++) for (int j = 0; j < NS; j++) {
			int q = NS + j;
			boolean on = !PASS[i][j] && !(roundLeg[0] && LEADLEG[i] && !roundMeets(j, g1)) && !(roundLeg[1] && LEADLEG[j] && !roundMeets(i, g0))
				&& bx1[i] >= bx0[q] && bx1[q] >= bx0[i] && by1[i] >= by0[q] && by1[q] >= by0[i];
			candOn[i * NS + j] = on;
			if (on) { candI[nCand] = i; candJ[nCand++] = j; }
		}
		if (nCand == 0) return;
		int m = 0;
		for (int j = 0; j < NS; j++) for (int i = 0; i < NS; i++) if (candOn[i * NS + j]) { cand1I[m] = i; cand1J[m++] = j; }
	}
	double cs, ct, cdx, cdy;
	void closest(double[] X, double[] Y, int a0, int a1, int b0, int b1) {
		double p1x = X[a0], p1y = Y[a0], d1x = X[a1] - p1x, d1y = Y[a1] - p1y;
		double p2x = X[b0], p2y = Y[b0], d2x = X[b1] - p2x, d2y = Y[b1] - p2y;
		double rx = p1x - p2x, ry = p1y - p2y;
		double a = d1x * d1x + d1y * d1y, e = d2x * d2x + d2y * d2y, f = d2x * rx + d2y * ry, s, t;
		if (a <= 1e-12 && e <= 1e-12) { s = 0; t = 0; }
		else if (a <= 1e-12) { s = 0; t = clamp01(f / e); }
		else {
			double c = d1x * rx + d1y * ry;
			if (e <= 1e-12) { t = 0; s = clamp01(-c / a); }
			else {
				double b = d1x * d2x + d1y * d2y, den = a * e - b * b;
				s = den > 1e-12 ? clamp01((b * f - c * e) / den) : 0;
				t = (b * s + f) / e;
				if (t < 0) { t = 0; s = clamp01(-c / a); }
				else if (t > 1) { t = 1; s = clamp01((b - c) / a); }
			}
		}
		cs = s; ct = t;
		cdx = p2x + d2x * t - (p1x + d1x * s); cdy = p2y + d2y * t - (p1y + d1y * s);
	}
	public static double atan2(double y, double x) {
		double ax = Math.abs(x), ay = Math.abs(y);
		if (ax == 0 && ay == 0) return 0;
		boolean swap = ay > ax;
		double z = swap ? ax / ay : ay / ax, base = 0;
		if (z > 0.41421356237309503) { z = (z - 1) / (z + 1); base = 0.7853981633974483; }
		double z2 = z * z;
		double r = base + z * (1 + z2 * (-1.0 / 3 + z2 * (1.0 / 5 + z2 * (-1.0 / 7 + z2 * (1.0 / 9 + z2 * (-1.0 / 11 + z2 * (1.0 / 13 + z2 * (-1.0 / 15))))))));
		if (swap) r = 1.5707963267948966 - r;
		if (x < 0) r = 3.141592653589793 - r;
		return y < 0 ? -r : r;
	}
	static double clamp01(double x) { return x < 0 ? 0 : x > 1 ? 1 : x; }
	static boolean aims(int striker, int target) { return (striker != 7 && striker != 3) || target <= 1; }
	void detectHits() {
		Arrays.fill(touching, false);
		if (nCand > 0 && !down[0] && !down[1]) {
			Arrays.fill(bestHit, 0);
			for (int c = 0; c < nCand; c++) {
				int i = candI[c], j = candJ[c];
				if (STRIKES[i]) strike(0, i, j);
				if (STRIKES[j]) strike(1, j, i);
			}
			for (int q = 0; q < 2 * NS; q++) if (bestHit[q] > 0) {
				int att = q < NS ? 0 : 1, sh = q % NS, dmg = (int) Math.floor(bestHit[q] + 0.5);
				damage[1 - att] += dmg;
				if (record) hitEvents.add(new double[]{stepNo, att, sh, bestTarget[q], bestSpeed[q], dmg, bestCX[q], bestCY[q]});
			}
		}
		for (int q = 0; q < 2 * NS; q++) apart[q] = touching[q] ? 0 : Math.min(apart[q] + 1, SEP);
	}
	void strike(int g, int a, int b) {
		int ka = KIND[a], kb = KIND[b], o = g * NP, p = (1 - g) * NP, q = g * NS + a;
		int a0 = o + SH[a][0], a1 = o + SH[a][1], b0 = p + SH[b][0], b1 = p + SH[b][1];
		double rr = SR[a] + SR[b];
		closest(px, py, a0, a1, b0, b1);
		double d2 = cdx * cdx + cdy * cdy;
		if (d2 >= rr * rr) return;
		if (kb != 3) touching[q] = true;
		if (apart[q] < SEP) return;
		double s = cs, t = ct;
		if (ka == 5 && py[a0] + s * (py[a1] - py[a0]) < FOOTMIN) return;
		boolean hit = zone(kb) > 0 && aims(ka, kb), block = kb == 2;
		if (!hit && !block) return;
		double d = Math.sqrt(d2), nx = d > 1e-9 ? cdx / d : FACING[g], ny = d > 1e-9 ? cdy / d : 0;
		double vax = (1 - s) * vx[a0] + s * vx[a1], vay = (1 - s) * vy[a0] + s * vy[a1];
		double vbx = (1 - t) * vx[b0] + t * vx[b1], vby = (1 - t) * vy[b0] + t * vy[b1];
		double vn = (vax - vbx) * nx + (vay - vby) * ny;
		if (vn < 2) return;
		if (block) {
			blockedBy[g]++;
			if (record) blockEvents.add(new double[]{stepNo, g, px[a0] + s * (px[a1] - px[a0]) + nx * SR[a], py[a0] + s * (py[a1] - py[a0]) + ny * SR[a]});
			return;
		}
		if (vax * nx + vay * ny < STRIKEV) return;
		double dmg = zone(kb) * (Math.min(vn, 12) - 2);
		if (dmg > bestHit[q]) {
			bestHit[q] = dmg; bestSpeed[q] = vn; bestTarget[q] = b;
			bestCX[q] = px[a0] + s * (px[a1] - px[a0]) + nx * SR[a]; bestCY[q] = py[a0] + s * (py[a1] - py[a0]) + ny * SR[a];
		}
	}
	final double[] qx = new double[N], qy = new double[N];
	void collisions() {
		if (nCand == 0) return;
		System.arraycopy(px, 0, qx, 0, N); System.arraycopy(py, 0, qy, 0, N);
		collide(0);
		for (int i = 0; i < N; i++) { double t = px[i]; px[i] = qx[i]; qx[i] = t; t = py[i]; py[i] = qy[i]; qy[i] = t; }
		collide(1);
		for (int i = 0; i < N; i++) { px[i] = (px[i] + qx[i]) * 0.5; py[i] = (py[i] + qy[i]) * 0.5; }
	}
	void collide(int g) {
		int o = g * NP, p = (1 - g) * NP;
		int[] ca = g == 0 ? candI : cand1J, cb = g == 0 ? candJ : cand1I;
		for (int c = 0; c < nCand; c++) {
			int a = ca[c], b = cb[c];
			if (KIND[a] == 7 || KIND[b] == 7) continue;
			int a0 = o + SH[a][0], a1 = o + SH[a][1], b0 = p + SH[b][0], b1 = p + SH[b][1];
			double rr = SR[a] + SR[b];
			closest(px, py, a0, a1, b0, b1);
			double d2 = cdx * cdx + cdy * cdy;
			if (d2 >= rr * rr) continue;
			double d = Math.sqrt(d2), nx = d > 1e-9 ? cdx / d : FACING[g], ny = d > 1e-9 ? cdy / d : 0, s = cs, t = ct;
			contactNow = true;
			closest(sx, sy, a0, a1, b0, b1);
			double keep = rr - Math.sqrt(cdx * cdx + cdy * cdy) - BIAS, push = keep > 0 ? rr - d - keep : rr - d;
			if (push <= 0) continue;
			double WA = a0 == a1 ? w[a0] : (1 - s) * (1 - s) * w[a0] + s * s * w[a1];
			double WB = b0 == b1 ? w[b0] : (1 - t) * (1 - t) * w[b0] + t * t * w[b1];
			double lam = push / (WA + WB);
			if (a0 == a1) { px[a0] -= nx * lam * w[a0]; py[a0] -= ny * lam * w[a0]; }
			else {
				px[a0] -= nx * lam * (1 - s) * w[a0]; py[a0] -= ny * lam * (1 - s) * w[a0];
				px[a1] -= nx * lam * s * w[a1]; py[a1] -= ny * lam * s * w[a1];
			}
			if (b0 == b1) { px[b0] += nx * lam * w[b0]; py[b0] += ny * lam * w[b0]; }
			else {
				px[b0] += nx * lam * (1 - t) * w[b0]; py[b0] += ny * lam * (1 - t) * w[b0];
				px[b1] += nx * lam * t * w[b1]; py[b1] += ny * lam * t * w[b1];
			}
		}
	}
	public void step() {
		for (int i = 0; i < N; i++) { fx[i] = 0; fy[i] = GRAV[i]; wasTouch[i] = touch[i]; touch[i] = false; dep[i] = 0; }
		motors(0); motors(1);
		for (int i = 0; i < N; i++) {
			vx[i] = (vx[i] + fx[i] * w[i] * DT) * DRAG; vy[i] = (vy[i] + fy[i] * w[i] * DT) * DRAG;
			sx[i] = px[i]; sy[i] = py[i];
			px[i] += vx[i] * DT; py[i] += vy[i] * DT;
		}
		near = boxesOverlap();
		buildCandidates();
		detectHits();
		bones(0); bones(1); limits(0); limits(1); collisions(); floor();
		for (int i = 0; i < N; i++) { vx[i] = (px[i] - sx[i]) / DT; vy[i] = (py[i] - sy[i]) / DT; }
		for (int f = 0; f < 2; f++) for (int i = 0; i < NP; i++) {
			int k = f * NP + i;
			if (!touch[k]) continue;
			if (!FOOTPT[i] && !down[f]) { down[f] = true; downPt[f] = i; if (fallStep[f] < 0) fallStep[f] = stepNo; }
			if (Math.abs(px[k]) > RING && !out[f]) { out[f] = true; if (fallStep[f] < 0) fallStep[f] = stepNo; }
		}
		if (contactNow && !down[0] && !down[1]) contactSteps++;
		contactNow = false;
		stepNo++;
	}
	public void beginTurn(boolean round0, boolean round1) {
		down[0] = down[1] = out[0] = out[1] = false;
		downPt[0] = downPt[1] = -1; fallStep[0] = fallStep[1] = -1;
		blockedBy[0] = blockedBy[1] = 0;
		damage[0] = damage[1] = 0;
		contactSteps = 0; stepNo = 0;
		hitEvents.clear();
		blockEvents.clear();
		for (int i = 0; i < N; i++) touch[i] = py[i] <= RADN[i];
		boolean in0 = !round0 && roundLeg[0] && legInside(0), in1 = !round1 && roundLeg[1] && legInside(1);
		roundLeg[0] = round0 || in0; roundLeg[1] = round1 || in1;
	}
	public void roundState() {
		for (int i = 0; i < N; i++) {
			px[i] = Math.rint(px[i] * 1000) / 1000; py[i] = Math.rint(py[i] * 1000) / 1000;
			vx[i] = Math.rint(vx[i] * 1000) / 1000; vy[i] = Math.rint(vy[i] * 1000) / 1000;
		}
	}
	public double comX(int f) { double m = 0, x = 0; for (int i = 0; i < NP; i++) { m += MASS[i]; x += MASS[i] * px[f * NP + i]; } return x / m; }
	public double comY(int f) { double m = 0, y = 0; for (int i = 0; i < NP; i++) { m += MASS[i]; y += MASS[i] * py[f * NP + i]; } return y / m; }
	public double comVX(int f) { double m = 0, v = 0; for (int i = 0; i < NP; i++) { m += MASS[i]; v += MASS[i] * vx[f * NP + i]; } return v / m; }
}

final class Game {
	public static final int MAX_TURNS = 250;
	public static final int DISTANCE_MIN = 1600, DISTANCE_MAX = 2400;
	public static final int FALL = 20;
	public static final int PUSH_FALL = 10;
	public static final int WINDOW = 20;
	public static final int STALE = 150;
	public static final int FULL = 100000;
	public static final int REGEN = 4840, HIT_DRAIN = 1000;
	public static final int[] EFFORT = {4, 4, 4, 6, 8, 8, 6, 8, 8, 4, 4, 4, 4, 4, 4};
	public static final double RKP = 60, RKD = 40, RMAX = 15, RREST = 0.035;
	public static final double LAND_PAST = 0.06, COME_IN = 0.7, SPLIT = 0.8, SPLIT_V = 0.15, FIRM = 0.035;
	public static final double WIDE = 0.28, REST_V = 0.05, ONTO = 0.02;
	static final int NP = Physics.NP, NJ = Physics.NJ, NS = Physics.NS;
	public static final int XOFF = 5000, YTOP = 3000;
	public static final int INPUT_LINES = 4;
	public static final int PER_POINT = 8;
	public static final int[] JOINT_AT = new int[NP];
	public static final int[] STRIKER_AT = new int[NP];
	static {
		Arrays.fill(JOINT_AT, -1);
		Arrays.fill(STRIKER_AT, -1);
		for (int j = 0; j < NJ; j++) JOINT_AT[Physics.JC[j]] = j;
		for (int k = 0; k < Physics.STRIKERS.length; k++) STRIKER_AT[Physics.SH[Physics.STRIKERS[k]][1]] = k;
	}
	public final Physics w = new Physics();
	public final boolean staminaOn, jointsOn;
	public final int startDistance;
	public final int[] startX = new int[2];
	public final int[] move = {Moves.STANCE, Moves.STANCE}, frame = {0, 0}, rise = {0, 0}, stamina = {FULL, FULL};
	public final int[][] target = new int[2][NJ], strength = new int[2][NJ];
	public int turn, sinceContact = WINDOW + 1, sinceHit = WINDOW + 1;
	public final boolean[] roundAsked = new boolean[2];
	public final boolean[] down = new boolean[2], out = new boolean[2], pushFall = new boolean[2];
	public final int[] hitLoss = new int[2], fallStep = {-1, -1};
	public final boolean[] split = new boolean[2];
	public static final double SPLIT_LOW = 0.45;
	public boolean stoodUp;
	public final int[][] look = {{2, 1}, {5, 0}};
	public final String[] message = {"", ""};
	public final String[] warning = new String[2];
	public int[][] film;
	public Game(int startDistance, boolean staminaOn, boolean jointsOn) {
		this.startDistance = startDistance; this.staminaOn = staminaOn; this.jointsOn = jointsOn;
		startX[0] = -(startDistance / 2); startX[1] = startDistance - startDistance / 2;
		standUp();
	}
	void standUp() {
		for (int f = 0; f < 2; f++) {
			w.stand(f, startX[f]);
			System.arraycopy(Moves.T[Moves.STANCE][0], 0, target[f], 0, NJ); System.arraycopy(Moves.S[Moves.STANCE][0], 0, strength[f], 0, NJ);
			w.setMotors(f, target[f], strength[f]);
			move[f] = Moves.STANCE; frame[f] = 0; rise[f] = 0; w.roundLeg[f] = false; catching[f] = false;
		}
		Arrays.fill(w.apart, Physics.SEP);
		sinceContact = sinceHit = WINDOW + 1;
	}
	public String apply(int f, String line) {
		Command c = Command.parse(line, turn == 0, jointsOn);
		warning[f] = c.warning;
		if (c.error != null) return c.error;
		if (c.skin >= 0) { look[f][0] = c.skin; look[f][1] = c.hair; }
		message[f] = c.message;
		command(f, c);
		return null;
	}
	public void setRaw(int f, int[] t, int[] s, boolean round) {
		System.arraycopy(t, 0, target[f], 0, NJ); System.arraycopy(s, 0, strength[f], 0, NJ);
		move[f] = Moves.NONE; frame[f] = 0; rise[f] = 0; roundAsked[f] = round; walking[f] = 0;
	}
	public void command(int f, Command c) {
		int was = move[f];
		afterHold[f] = was != Moves.NONE && was != Moves.STANCE
			&& (Moves.HELD[was] || Moves.LUNGE[was] != null || (was == Moves.STEP_FORWARD || was == Moves.STEP_BACK) && frame[f] == 0);
		walking[f] = 0;
		if (c.type == Command.JOINTS) {
			for (int j = 0; j < NJ; j++) {
				if (!c.keepTarget[j]) target[f][j] = c.target[j];
				if (!c.keepStrength[j]) strength[f][j] = c.strength[j];
			}
			move[f] = Moves.NONE; frame[f] = 0; rise[f] = 0; roundAsked[f] = c.round;
			return;
		}
		boolean wait = c.type == Command.WAIT;
		if (wait && move[f] == Moves.NONE) { roundAsked[f] = false; return; }
		int m = wait ? move[f] : c.move;
		if (Moves.HELD[m]) {
			move[f] = m; frame[f] = 0;
			System.arraycopy(Moves.T[m][0], 0, target[f], 0, NJ); System.arraycopy(Moves.S[m][0], 0, strength[f], 0, NJ);
			rise[f] = m == Moves.DUCK ? Math.min(Moves.RISE, rise[f] + 1) : Math.max(0, rise[f] - 1);
			double up = 1 - rise[f] / (double) Moves.RISE;
			stand(f, DUCK_X * (1 - up), HIGH[0] + up * (HIGH[1] - HIGH[0]), LEAN[0] + up * (LEAN[1] - LEAN[0]));
			roundAsked[f] = false;
			return;
		}
		if (m != move[f]) { move[f] = m; frame[f] = 0; }
		System.arraycopy(Moves.T[m][frame[f]], 0, target[f], 0, NJ); System.arraycopy(Moves.S[m][frame[f]], 0, strength[f], 0, NJ);
		if (m == Moves.STEP_FORWARD || m == Moves.STEP_BACK) {
			walking[f] = m == Moves.STEP_FORWARD ? 1 : -1;
			poseX[f] = 0; poseH[f] = HIGH[1]; poseLean[f] = LEAN[1];
			strength(f, Physics.LH, STEP_S); strength(f, Physics.RH, STEP_S);
		} else if (Moves.LUNGE[m] != null) {
			double[] l = Moves.LUNGE[m][frame[f]];
			double up = 1 - Math.max(0, rise[f] - 1) / (double) Moves.RISE;
			if (up == 1) stand(f, l[0], l[1], l[2]);
			else stand(f, DUCK_X + up * (l[0] - DUCK_X), HIGH[0] + up * (l[1] - HIGH[0]), LEAN[0] + up * (l[2] - LEAN[0]));
		}
		if (Moves.LUNGE[m] == null) rise[f] = 0;
		else if (rise[f] > 0) rise[f]--;
		roundAsked[f] = Moves.ROUND[m];
	}
	void stand(int f, double x, double h, double lean) {
		poseX[f] = x; poseH[f] = h; poseLean[f] = lean;
		walking[f] = 2;
		aim(f);
	}
	void aim(int f) {
		stance(f);
		strength(f, Physics.LH, STEP_S); strength(f, Physics.RH, STEP_S);
		double r = reflex(f);
		target[f][Physics.LA] = clampC(target[f][Physics.LA] / 100.0 + r, Physics.LA); target[f][Physics.RA] = clampC(target[f][Physics.RA] / 100.0 + r, Physics.RA);
	}
	final double[] poseX = new double[2], poseH = {HIGH[1], HIGH[1]}, poseLean = {LEAN[1], LEAN[1]};
	public final int[] stepDir = new int[2];
	final boolean[] catching = new boolean[2];
	final int[] lastCatch = {-99, -99};
	static final double LEG = 0.45, FOOT = 0.17, REACH = 0.896;
	public static final double SPACING = 0.42, LEAD_X = 0.20;
	static final double[] HIGH = {0.727, 0.850}, LEAN = {30.5, 5.4};
	public static final double DUCK_X = -0.08, DUCK_TOL = 0.15;
	static final int D = 0, PUSH = 1, EDGE = 2, LIFT = 3, LIFT_K = 4, G_PUSH = 5, G_HOME = 6, AIM = 7, LAND = 8, REACH_X = 9, SLOW = 10, BRAKE = 11, BOOST = 12, RISE_V = 13, AHEAD = 14;
	public static final double[] FWD = {0.25, 0.08, 0.06, 0.09, 1.0, 1.5, 1.0, -0.03, 0.01, 0.08, 1.0, 0.04, 3.0, 0.15, 0.07}, BACK = {0.25, 0.08, 0.06, 0.09, 1.0, 1.5, 1.0, 0, 0.01, 0.08, 1.0, 0.04, 3.0, 0.15, 0.07}, HOLD = {0, 0.08, 0.06, 0.09, 1.0, 1.5, 1.0, -0.03, 0.01, 0.08, 1.0, 0.04, 3.0, 0.15, 0.07};
	static final int TICK = 4;
	public final int[] walking = new int[2];
	public final int[] stage = new int[2];
	static final int STEP_S = 100;
	static final int STEP_START = 80, STEP_FIRST = 140;
	static final double HEEL = 0.05, GROUND = 0.045, ANKLE_Y = 0.09, COM_X = 0.06, TOL = 0.06, DOWN = 0.03, STEP_RISE = 0.03, FLOOR = 0.03, CLEAR = 0.02, START_MIN = 0.08, NEAR = 0.04, LATE = 0.10;
	static final double RISE = 0.06, DROP = 0.02;
	public static final double CAP_M = 0.02, CAP_AHEAD = 0.08, CATCH = 0.08;
	public static final double WALK_PAST = 0.10, WALK_INSIDE = 0.13, WALK_FAST = 1.3, WALK_FAST_BACK = 0.7, WALK_LEAN = 30, WALK_AGAINST = 0.3;
	final boolean[] braked = new boolean[2];
	final boolean[] afterHold = new boolean[2];
	boolean brakes(int f) {
		int d = walking[f];
		if (d != 1 && d != -1) return false;
		if (d * Physics.FACING[f] * w.comVX(f) > (d > 0 ? WALK_FAST : WALK_FAST_BACK)) return true;
		double xl = ankleX(f, true), xr = ankleX(f, false), xw = weightX(f);
		double behind = Math.min(xl, xr) - HEEL - xw, ahead = xw - Math.max(xl, xr) - FOOT;
		if (frame[f] == 0 ? behind > -WALK_INSIDE || d < 0 && ahead > -WALK_INSIDE : (d > 0 ? behind : ahead) > WALK_PAST) return true;
		if (-d * Physics.FACING[f] * w.comVX(f) > WALK_AGAINST && (d > 0 ? xr - HEEL - xw : xw - xl - FOOT) > 0) return true;
		if (!afterHold[f]) return false;
		hold(f);
		if (catching[f] || footY(f, true) > GROUND || footY(f, false) > GROUND || d > 0 && leanNow(f) > WALK_LEAN) return true;
		calm[f] = false;
		return false;
	}
	final boolean[] calm = new boolean[2];
	double ankleX(int f, boolean lead) { return Physics.FACING[f] * w.px[f * NP + (lead ? Physics.LTOE : Physics.RTOE)] - FOOT; }
	double pelvisX(int f) { return Physics.FACING[f] * w.px[f * NP + Physics.PELVIS]; }
	double pelvisV(int f) { return Physics.FACING[f] * w.vx[f * NP + Physics.PELVIS]; }
	public double weightX(int f) { return Physics.FACING[f] * (w.comX(f) + w.comVX(f) * Math.sqrt(w.comY(f) / Physics.G)); }
	void leg(int f, boolean lead, double dx, double dy, double lean, int strength) {
		if (dy < 0.3) dy = 0.3;
		double len = Math.sqrt(dx * dx + dy * dy);
		double c = Math.min(len, REACH) / (2 * LEG);
		double half = Physics.atan2(Math.sqrt(1 - c * c), c) * Physics.RAD2DEG, a = Physics.atan2(dx, dy) * Physics.RAD2DEG;
		int j = lead ? Physics.LH : Physics.RH;
		target[f][j] = clampC(lean - (a - half), j);
		target[f][j + 1] = clampC(2 * half, j + 1);
		target[f][j + 2] = clampC(-(a + half), j + 2);
		if (strength > 0) strength(f, j, strength);
	}
	void strength(int f, int j, int v) { strength[f][j] = strength[f][j + 1] = strength[f][j + 2] = v; }
	static int clampC(double deg, int j) { int c = (int) Math.rint(deg * 100); return c < Physics.JMINC[j] ? Physics.JMINC[j] : c > Physics.JMAXC[j] ? Physics.JMAXC[j] : c; }
	void stance(int f) {
		double xl = ankleX(f, true), xr = ankleX(f, false), ln = leanNow(f);
		double half = (xl - xr) / 2, mid = SPACING / 2 - LEAD_X + poseX[f], far = Math.abs(half) + Math.abs(mid), h = poseH[f];
		if (far * far + h * h > REACH * REACH) h = far < REACH ? Math.sqrt(REACH * REACH - far * far) : 0;
		h = Math.min(h, w.py[f * NP + Physics.PELVIS] - ANKLE_Y + STEP_RISE);
		leg(f, true, mid - half, h, footY(f, true) > GROUND ? ln : poseLean[f], 0);
		leg(f, false, mid + half, h, footY(f, false) > GROUND ? ln : poseLean[f], 0);
	}
	double footY(int f, boolean lead) {
		int o = f * NP;
		return lead ? Math.min(w.py[o + Physics.LHEEL], w.py[o + Physics.LTOE]) : Math.min(w.py[o + Physics.RHEEL], w.py[o + Physics.RTOE]);
	}
	static double clamp(double v, double m) { return v < -m ? -m : v > m ? m : v; }
	double height(int f, double px, double a, double b, double now, double[] n, double high) {
		double far = Math.max(Math.abs(px - a), Math.abs(px - b));
		double top = far < REACH ? Math.sqrt(REACH * REACH - far * far) : 0.3;
		return Math.max(now - DROP, Math.min(top, Math.min(now + RISE - n[RISE_V] * w.vy[f * NP + Physics.PELVIS], high)));
	}
	double leanNow(int f) {
		int o = f * NP;
		return Physics.atan2(Physics.FACING[f] * (w.px[o + Physics.NECK] - w.px[o + Physics.PELVIS]), w.py[o + Physics.NECK] - w.py[o + Physics.PELVIS]) * Physics.RAD2DEG;
	}
	void swing(int f, boolean lead, int travel, double foot, double y, double place, double p, double hip, double[] n) {
		double left = travel * (place - foot);
		double v = Physics.FACING[f] * w.vx[f * NP + (lead ? Physics.LTOE : Physics.RTOE)];
		double clear = Math.max(0, Math.min(1, (y - FLOOR) / CLEAR));
		p += clear * n[AHEAD] * pelvisV(f);
		if (left <= DOWN) { leg(f, lead, p - foot + clamp(n[BRAKE] * v, n[REACH_X]), hip + n[LAND], leanNow(f), STEP_S); return; }
		double lift = Math.min(n[LIFT], n[LIFT_K] * (left - DOWN));
		double reach = n[REACH_X] * (1 + n[BOOST] * clear);
		leg(f, lead, p - foot - clamp(travel * n[SLOW] * left - n[BRAKE] * v, reach), hip - lift, leanNow(f), STEP_S);
	}
	void step(int f, int dir, double d, double[] n, int t, double cap) { step(f, dir, d, n, t, cap, 0); }
	void step(int f, int dir, double d, double[] n, int t, double cap, int toward) {
		double xl = ankleX(f, true), xr = ankleX(f, false), p = pelvisX(f), xi = weightX(f), lean = poseLean[f];
		double now = w.py[f * NP + Physics.PELVIS] - ANKLE_Y;
		boolean leadFirst = dir > 0;
		double first = leadFirst ? xl : xr, second = leadFirst ? xr : xl;
		double y1 = footY(f, leadFirst), y2 = footY(f, !leadFirst);
		boolean up1 = y1 > GROUND, up2 = y2 > GROUND;
		double wide = dir * (first - second) - SPACING;
		double edge = leadFirst ? first - HEEL : first + FOOT;
		boolean apart = Math.abs(wide) > TOL;
		boolean split = Math.abs(xl - xr) > SPLIT;
		int go;
		boolean capture = !Double.isNaN(cap);
		boolean ahead = !capture && d > 0 && dir * (xi - (leadFirst ? first + FOOT : first - HEEL)) > CAP_M;
		double stay = leadFirst ? second - HEEL : second + FOOT;
		boolean onto = !capture && d > 0 && !up1 && !up2 && t < STEP_FIRST && dir * (first - second) < 0 && dir * (xi - stay) < ONTO;
		if (capture) go = 2;
		else if (onto) go = 4;
		else if (!up2 && (ahead || t < STEP_FIRST && (up1 || (d - wide > NEAR && (t < STEP_START || wide > START_MIN))))) go = 1;
		else if (apart) {
			boolean stalled = (split || wide > WIDE) && dir * (xi - (first + second) / 2) >= 0 && dir * pelvisV(f) < SPLIT_V;
			go = !up1 && (stalled || dir * (xi - edge) >= -(n[EDGE] + (up2 || split ? LATE : 0))) ? 2 : 3;
		}
		else go = 0;
		stage[f] = go;
		if (go != 0) calm[f] = false;
		if (go == 1) {
			double place = second + dir * (SPACING + d), rest = second + (leadFirst ? SPACING : 0) - LEAD_X + COM_X;
			double done = d > 0 ? Math.max(0, Math.min(1, 1 - dir * (place - first) / d)) : 1;
			double under = xi + (leadFirst ? HEEL : -FOOT) + dir * CATCH;
			double placed = dir * Math.max(dir * place, dir * under);
			double px = p + clamp(n[G_PUSH] * (rest + done * (place + (leadFirst ? -HEEL : FOOT) + dir * n[AIM] - rest) - xi), n[PUSH]);
			double h = height(f, px, second, second, now, n, 1);
			leg(f, !leadFirst, px - second, h, lean, STEP_S);
			swing(f, leadFirst, dir, first, y1, placed, p, now, n);
		} else if (go == 2) {
			double place = capture ? cap : first - dir * SPACING, home = leadFirst ? xl - LEAD_X : xr + SPACING - LEAD_X;
			double px = capture ? p : p + clamp(n[G_HOME] * (home + COM_X - xi), n[PUSH]);
			double h = height(f, px, first, first, now, n, poseH[f]);
			leg(f, leadFirst, px - first, h, lean, STEP_S);
			int travel = capture ? (place >= second ? 1 : -1) : wide > 0 ? dir : -dir;
			if (!capture && travel != dir && dir * (xi - (leadFirst ? first + FOOT : first - HEEL)) > CAP_M) place = second;
			if (!capture && up2 && Math.abs(pelvisV(f)) < REST_V && Math.abs(w.vx[f * NP + (leadFirst ? Physics.RTOE : Physics.LTOE)]) < REST_V) place = second;
			swing(f, !leadFirst, travel, second, y2, place, p, now, n);
		} else if (go == 4) {
			double px = p + clamp(n[G_PUSH] * (stay + dir * (ONTO + n[AIM]) - xi), n[PUSH]);
			double h = height(f, px, xl, xr, now, n, poseH[f]);
			leg(f, true, px - xl, h, lean, STEP_S); leg(f, false, px - xr, h, lean, STEP_S);
		} else if (go == 3) {
			double px = p + clamp(n[G_PUSH] * (edge + dir * n[AIM] - xi), n[PUSH]);
			double h = split ? height(f, px, first, first, now, n, poseH[f]) : height(f, px, xl, xr, now, n, poseH[f]), ln = leanNow(f);
			double hl = leadFirst ? Math.max(h, now + n[LAND]) : h, hr = leadFirst ? h : Math.max(h, now + n[LAND]);
			leg(f, true, px - xl, hl, leadFirst ? ln : lean, STEP_S); leg(f, false, px - xr, hr, leadFirst ? lean : ln, STEP_S);
		} else if (!calm[f]) {
			calm[f] = true;
			aim(f);
		}
	}
	void hold(int f) {
		double xl = ankleX(f, true), xr = ankleX(f, false), xi = weightX(f);
		boolean upL = footY(f, true) > GROUND, upR = footY(f, false) > GROUND;
		double dl = Math.max(0, Math.max(xl - HEEL - xi, xi - xl - FOOT)), dr = Math.max(0, Math.max(xr - HEEL - xi, xi - xr - FOOT));
		int dir = upL != upR ? (upL ? -1 : 1) : dl <= dr ? 1 : -1;
		double cap = Double.NaN;
		int toward = 0;
		if (upL != upR) {
			double xs = upL ? xr : xl, home = upL ? xs + SPACING : xs - SPACING;
			int s = xi > xs + FOOT + CAP_M ? 1 : xi < xs - HEEL - CAP_M ? -1 : 0;
			if (footY(f, upR) > FIRM) s = 0;
			if (s != 0 && (s * (home - xs) < 0 || s * (xi - TOL + s * CAP_AHEAD - home) > 0)) { toward = s; dir = upR ? 1 : -1; }
		} else if (!upL) {
			if (xi > Math.max(xl, xr) + FOOT + CAP_M) { toward = 1; dir = xl >= xr ? 1 : -1; }
			else if (xi < Math.min(xl, xr) - HEEL - CAP_M) { toward = -1; dir = xl >= xr ? -1 : 1; }
		}
		if (toward != 0) cap = xi - TOL + toward * CAP_AHEAD;
		if (toward != 0 && upL != upR) {
			double xa = upL ? xl : xr, past = toward > 0 ? xa + FOOT - xi : xi - (xa - HEEL);
			if (past >= LAND_PAST) cap = Math.abs((upL ? xr : xl) - xa) <= COME_IN ? xa : toward > 0 ? Math.min(xa, cap) : Math.max(xa, cap);
		}
		boolean capture = toward != 0;
		if (capture && !catching[f] && turn - lastCatch[f] > 2) stepDir[f] = toward;
		catching[f] = capture;
		if (capture) lastCatch[f] = turn;
		double wide = xl - xr - SPACING;
		if (!capture && (Math.abs(wide) <= (poseH[f] < HIGH[1] - 0.02 ? DUCK_TOL : TOL))) {
			stage[f] = 0;
			if (!calm[f]) { calm[f] = true; aim(f); }
			return;
		}
		step(f, dir, 0, HOLD, STEP_FIRST, cap, toward);
	}
	public int offFeet(int f, double margin) {
		int o = f * NP, s = Physics.FACING[f];
		double xi = s * (w.comX(f) + w.comVX(f) * Math.sqrt(w.comY(f) / Physics.G));
		double back = Math.min(s * w.px[o + Physics.RHEEL], s * w.px[o + Physics.LHEEL]), front = Math.max(s * w.px[o + Physics.LTOE], s * w.px[o + Physics.RTOE]);
		return xi < back - margin ? -1 : xi > front + margin ? 1 : 0;
	}
	public double reflex(int f) {
		int o = f * NP, s = Physics.FACING[f];
		double e = s * (w.comX(f) - (w.px[o + Physics.RHEEL] + w.px[o + Physics.LTOE]) / 2), v = s * w.comVX(f);
		e -= Math.max(0, Math.min(RREST * (1 - rise[f] / (double) Moves.RISE), e));
		return Math.max(-RMAX, Math.min(RMAX, RKP * e + RKD * v));
	}
	public void simulate() {
		for (int f = 0; f < 2; f++) {
			stepDir[f] = 0;
			if (walking[f] != 2) catching[f] = false;
			if (staminaOn) {
				int[] st = move[f] == Moves.NONE ? strength[f] : Moves.S[move[f]][frame[f]];
				int cost = 0;
				for (int j = 0; j < NJ; j++) cost += EFFORT[j] * st[j];
				stamina[f] = Math.max(0, Math.min(FULL, stamina[f] + REGEN - cost));
				w.fatigue[f] = 0.4 + 0.6 * stamina[f] / 100000.0;
			} else w.fatigue[f] = 1;
			w.setMotors(f, target[f], strength[f]);
		}
		int hp0 = w.hp[0], hp1 = w.hp[1];
		calm[0] = calm[1] = false;
		for (int f = 0; f < 2; f++) braked[f] = brakes(f);
		w.beginTurn(roundAsked[0], roundAsked[1]);
		for (int s = 0; s < Physics.STEPS; s++) {
			if (s % TICK == 0) for (int f = 0; f < 2; f++) if (walking[f] != 0) {
				if (walking[f] == 2) hold(f);
				else if (braked[f]) hold(f);
				else { double[] n = walking[f] > 0 ? FWD : BACK; step(f, walking[f], n[D], n, frame[f] * Physics.STEPS + s, Double.NaN); }
				w.setMotors(f, target[f], strength[f]);
			}
			w.step();
			if (film != null && s % 10 == 9) {
				int[] fr = film[s / 10];
				for (int i = 0; i < Physics.N; i++) { fr[2 * i] = (int) Math.rint(w.px[i] * 1000); fr[2 * i + 1] = (int) Math.rint(w.py[i] * 1000); }
			}
		}
		for (int f = 0; f < 2; f++) w.hp[f] -= w.damage[f];
		hitLoss[0] = hp0 - w.hp[0]; hitLoss[1] = hp1 - w.hp[1];
		sinceContact = w.contactSteps > 0 ? 0 : Math.min(sinceContact + 1, STALE);
		sinceHit = hitLoss[0] > 0 || hitLoss[1] > 0 ? 0 : Math.min(sinceHit + 1, WINDOW + 1);
		if (staminaOn) for (int f = 0; f < 2; f++) stamina[f] = Math.max(0, stamina[f] - HIT_DRAIN * hitLoss[f]);
		boolean push = sinceContact <= WINDOW && sinceHit > WINDOW;
		stoodUp = false;
		for (int f = 0; f < 2; f++) {
			split[f] = !w.down[f] && !w.out[f] && Math.abs(w.px[f * NP + Physics.LANK] - w.px[f * NP + Physics.RANK]) > SPLIT && w.py[f * NP + Physics.PELVIS] < SPLIT_LOW;
			if (split[f]) { w.down[f] = true; w.downPt[f] = Physics.PELVIS; if (w.fallStep[f] < 0) w.fallStep[f] = Physics.STEPS - 1; }
		}
		for (int f = 0; f < 2; f++) {
			down[f] = w.down[f]; out[f] = w.out[f]; fallStep[f] = w.fallStep[f];
			boolean fell = down[f] || out[f];
			pushFall[f] = fell && push;
			if (fell) w.hp[f] -= push ? PUSH_FALL : FALL;
			stoodUp |= fell;
		}
		if (stoodUp) standUp();
		else {
			w.roundState();
			for (int f = 0; f < 2; f++) if (move[f] != Moves.NONE && !Moves.HELD[move[f]]) {
				if (braked[f]) frame[f] = 0;
				else if (++frame[f] >= Moves.LENGTH[move[f]]) { move[f] = Moves.STANCE; frame[f] = 0; }
			}
		}
		roundAsked[0] = roundAsked[1] = false;
		turn++;
	}
	public boolean over() { return w.hp[0] <= 0 || w.hp[1] <= 0 || turn >= MAX_TURNS || stale(); }
	public boolean stale() { return sinceContact >= STALE; }
	public int winner() {
		if (w.hp[0] <= 0 && w.hp[1] <= 0) return -1;
		return w.hp[0] > w.hp[1] ? 0 : w.hp[1] > w.hp[0] ? 1 : -1;
	}
	public int score(int f) { return Math.max(0, w.hp[f]); }
	public String initLine(int p) { return String.valueOf(startDistance); }
	public List<String> inputLines(int p) {
		List<String> out = new ArrayList<>(INPUT_LINES);
		int o = 1 - p;
		long s = p == 0 ? 1 : -1;
		for (int f : new int[]{p, o}) out.add(w.hp[f] + " " + stamina[f] + " " + moveName(f) + " " + frame[f] + " " + rise[f] + " " + (w.roundLeg[f] ? 1 : 0));
		StringBuilder b = new StringBuilder(2 * NP * PER_POINT * 6);
		for (int f : new int[]{p, o}) for (int i = 0; i < NP; i++) {
			int q = f * NP + i, j = JOINT_AT[i], k = STRIKER_AT[i];
			if (b.length() > 0) b.append(' ');
			b.append(XOFF + s * mm(w.px[q])).append(' ').append(YTOP - mm(w.py[q])).append(' ').append(s * mm(w.vx[q])).append(' ').append(-mm(w.vy[q]))
				.append(' ').append(k < 0 ? Physics.SEP : w.apart[f * NS + Physics.STRIKERS[k]]);
			if (j < 0) b.append(" 0.00 0.00 0");
			else b.append(' ').append(deg(angleC(f, j))).append(' ').append(deg(target[f][j])).append(' ').append(strength[f][j]);
		}
		out.add(b.toString());
		out.add(sinceContact + " " + sinceHit);
		return out;
	}
	public String moveName(int f) { return move[f] == Moves.NONE ? "JOINTS" : Moves.NAME[move[f]]; }
	static long mm(double x) { return (long) Math.rint(x * 1000); }
	public int angleC(int f, int j) { return (int) Math.rint(w.angle(f, j) * Physics.RAD2DEG * 100); }
	public long speed(int f, int j) { return (long) Math.rint(w.angSpeed(f, j)); }
	public static String deg(int c) { int a = Math.abs(c); return (c < 0 ? "-" : "") + a / 100 + "." + (a % 100 < 10 ? "0" : "") + a % 100; }
	static int hundredths(String s) {
		boolean neg = s.startsWith("-");
		int dot = s.indexOf('.');
		int v = Integer.parseInt(s.substring(neg ? 1 : 0, dot)) * 100 + Integer.parseInt(s.substring(dot + 1));
		return neg ? -v : v;
	}
	public static Game fromInput(String initLine, List<String> lines, boolean staminaOn, boolean jointsOn) {
		Game g = new Game(Integer.parseInt(initLine.trim()), staminaOn, jointsOn);
		for (int f = 0; f < 2; f++) {
			String[] q = lines.get(f).trim().split(" ");
			g.w.hp[f] = Integer.parseInt(q[0]); g.stamina[f] = Integer.parseInt(q[1]);
			g.move[f] = q[2].equals("JOINTS") ? Moves.NONE : Moves.index(q[2]); g.frame[f] = Integer.parseInt(q[3]);
			g.rise[f] = Integer.parseInt(q[4]); g.w.roundLeg[f] = q[5].equals("1");
		}
		String[] q = lines.get(2).trim().split(" ");
		for (int f = 0; f < 2; f++) for (int i = 0; i < NP; i++) {
			int idx = f * NP + i, j = JOINT_AT[i], k = STRIKER_AT[i], b = PER_POINT * idx;
			g.w.px[idx] = (Integer.parseInt(q[b]) - XOFF) / 1000.0; g.w.py[idx] = (YTOP - Integer.parseInt(q[b + 1])) / 1000.0;
			g.w.vx[idx] = Integer.parseInt(q[b + 2]) / 1000.0; g.w.vy[idx] = -Integer.parseInt(q[b + 3]) / 1000.0;
			if (k >= 0) g.w.apart[f * NS + Physics.STRIKERS[k]] = Integer.parseInt(q[b + 4]);
			if (j >= 0) { g.target[f][j] = hundredths(q[b + 6]); g.strength[f][j] = Integer.parseInt(q[b + 7]); }
		}
		q = lines.get(3).trim().split(" ");
		g.sinceContact = Integer.parseInt(q[0]); g.sinceHit = Integer.parseInt(q[1]);
		return g;
	}
	public void copyFrom(Game g) {
		w.copyFrom(g.w);
		for (int f = 0; f < 2; f++) {
			move[f] = g.move[f]; frame[f] = g.frame[f]; rise[f] = g.rise[f]; stamina[f] = g.stamina[f];
			System.arraycopy(g.target[f], 0, target[f], 0, NJ); System.arraycopy(g.strength[f], 0, strength[f], 0, NJ);
			roundAsked[f] = g.roundAsked[f];
			walking[f] = g.walking[f]; poseX[f] = g.poseX[f]; poseH[f] = g.poseH[f]; poseLean[f] = g.poseLean[f]; stepDir[f] = g.stepDir[f]; lastCatch[f] = g.lastCatch[f]; catching[f] = g.catching[f]; afterHold[f] = g.afterHold[f];
			down[f] = g.down[f]; out[f] = g.out[f]; pushFall[f] = g.pushFall[f]; split[f] = g.split[f]; hitLoss[f] = g.hitLoss[f]; fallStep[f] = g.fallStep[f];
		}
		turn = g.turn; sinceContact = g.sinceContact; sinceHit = g.sinceHit; stoodUp = g.stoodUp;
	}
}

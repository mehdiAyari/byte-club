package com.codingame.game.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One line written by a bot.
 *
 *   STANCE | GUARD | RELAX | DUCK | JAB | CROSS | OVERHAND | KICK | HIGH_KICK | STEP_FORWARD | STEP_BACK | JUMP   [message]
 *   WAIT   [message]
 *   JOINTS t1 s1 t2 s2 ... t15 s15 [ROUND]   [message]        each value is a number or * (keep the current value)
 *   LOOK skin hair [command]                                   the look is set on the first turn; later, or with other values, it is ignored
 *
 * A line that is not a command at all (empty, or an unknown name) is refused: the player is eliminated (error). A command that
 * is recognized but wrong (JOINTS outside its leagues or with wrong values, a LOOK that cannot be used) is not played as written:
 * the referee shows why in the console (warning) and plays WAIT instead, or plays the closest valid values (out of range).
 */
public final class Command {
    public static final int WAIT = 0, MOVE = 1, JOINTS = 2;
    public static final int SKINS = 8, HAIRS = 10, MESSAGE_MAX = 40;

    public int type = WAIT, move = Moves.NONE;
    /** JOINTS: targets in hundredths of a degree, already inside the range of each joint; strengths in percent, 0 to 100. */
    public final int[] target = new int[Physics.NJ], strength = new int[Physics.NJ];
    public final boolean[] keepTarget = new boolean[Physics.NJ], keepStrength = new boolean[Physics.NJ];
    public boolean round;
    public int skin = -1, hair = -1;
    public String message = "";
    /** Why the line is refused (the player is eliminated), or null. */
    public String error;
    /** What was not played as written (the player is not eliminated), or null. */
    public String warning;

    private Command fail(String why) { error = why; return this; }
    private void note(String why) { warning = warning == null ? why : warning + "; " + why; }
    /** A recognized command that cannot be played: WAIT is played instead. */
    private Command waitInstead(String why) { note(why + ": WAIT is played instead"); type = WAIT; move = Moves.NONE; round = false; return this; }

    public static Command move(int m) { Command c = new Command(); c.type = MOVE; c.move = m; return c; }

    public static Command parse(String line, boolean firstTurn, boolean jointsAllowed) {
        Command c = new Command();
        if (line == null) return c.fail("no output");
        String[] q = line.trim().split("\\s+");
        if (q.length == 0 || q[0].isEmpty()) return c.fail("empty output");
        int i = 0;
        if (q[0].equals("LOOK")) {
            // the look is cosmetic: it never costs the match. LOOK takes up to two numbers; on the first turn a valid pair sets the look,
            // anything else (a later turn, other values) is ignored, and the rest of the line is played as usual
            i = 1;
            String skin = i < q.length && numberLike(q[i]) ? q[i++] : null;
            String hair = skin != null && i < q.length && numberLike(q[i]) ? q[i++] : null;
            if (!firstTurn) c.note("LOOK is ignored: the look is chosen on the first turn only");
            else if (hair != null && isIndex(skin, SKINS) && isIndex(hair, HAIRS)) { c.skin = skin.charAt(0) - '0'; c.hair = hair.charAt(0) - '0'; }
            else c.note("LOOK is ignored: it takes a skin from 0 to " + (SKINS - 1) + " and a hair from 0 to " + (HAIRS - 1));
            if (i == q.length) return c;                                     // LOOK and nothing else: a WAIT
        }
        String name = q[i++];
        if (name.equals("JOINTS")) {
            if (!jointsAllowed) return c.waitInstead("JOINTS is not available in this league");
            int n = Physics.NJ;
            if (q.length < i + 2 * n) return c.waitInstead("JOINTS takes " + 2 * n + " values (a target and a strength for each joint), found " + (q.length - i));
            StringBuilder out = new StringBuilder();                         // the values brought back into their range
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

    /** A number: an optional minus sign, 1 to 6 digits, and optionally a point followed by 1 to 6 digits. */
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

    /** Looks like one value too many rather than like a message. */
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

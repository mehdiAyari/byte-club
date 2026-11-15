package com.codingame.game.view;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.codingame.game.engine.Game;
import com.codingame.game.engine.Moves;
import com.codingame.game.engine.Physics;
import com.codingame.gameengine.core.AbstractPlayer;
import com.codingame.gameengine.core.GameManager;
import com.codingame.gameengine.core.Module;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * What the viewer is given.
 *
 * Once, at the start ("global"): the ring, the league switches, the skeleton, the collision shapes and where every point starts
 * ("pos": x and y of the 19 points of the left fighter, then of the right one, in millimetres).
 * Every turn: the 38 points after each quarter of the turn, packed (see pack), and what happened.
 *
 *   p   the points: 4 quarter-turns x 38 points x (x, y), in millimetres, each as the change since the quarter before
 *   h   HP of both fighters after the turn
 *   s   stamina of both fighters in tenths of a percent (only when stamina is on)
 *   m   the move of each fighter during the turn (index in "moves", -1 when it drives its joints), f its frame
 *   t   the messages (only when one is not empty)
 *   e   the events, in order:
 *         [0, step, attacker, striker kind, target kind, damage, x, y, speed]   a strike that touched (damage can be 0; speed in tenths of m/s)
 *         [1, step, fighter, 0 fall | 1 ring-out, 1 when it costs nothing, point]  point: the first point on the floor (index in "points"), -1 for a ring-out
 *         [2, fighter, -1 back | 1 forward]                                     a recovery step starts with this turn
 *         [3, step, attacker, x, y]                                             a strike stopped by the arms
 *       step is the physics step inside the turn, 0 to 39; x and y are in millimetres.
 *   r   1 when both fighters went back to their starting places at the end of the turn: the next turn starts from "pos"
 *   l   the looks [[skin, hair], [skin, hair]], on the first turn
 *   x   on the last turn: [winner or -1 for a draw, 0 time | 1 knockout | 2 a player was eliminated (that turn was not played: no p)]
 */
@Singleton
public class FightModule implements Module {
    public static final String NAME = "fight";
    /** The 86 characters numbers are packed with: nothing that JSON or HTML escapes. */
    public static final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!#$%()*+,-./:;?@[]^_{|}~";
    static final int BASE = DIGITS.length(), HALF = BASE / 2, LIMIT = HALF + (BASE - HALF) * BASE;

    private final GameManager<AbstractPlayer> gameManager;
    private Map<String, Object> global, frame;
    private final int[] start = new int[2 * Physics.N], last = new int[2 * Physics.N];
    private final int[] moveNow = new int[2], frameNow = new int[2];
    private final List<int[]> events = new ArrayList<>();

    @Inject
    FightModule(GameManager<AbstractPlayer> gameManager) {
        this.gameManager = gameManager;
        gameManager.registerModule(this);
    }

    /**
     * Packs whole numbers: each one is written as its sign folded into the low bit (0, -1, 1, -2, 2 ... become 0, 1, 2, 3, 4 ...),
     * then as one character when that is under 43, or as two characters otherwise (the first one from the upper half of DIGITS).
     * A number too large for two characters is cut: the caller keeps what was really written.
     */
    static int pack(StringBuilder sb, int v) {
        int z = v >= 0 ? 2 * v : -2 * v - 1;
        if (z >= LIMIT) { z = LIMIT - 1 - ((LIMIT - 1 - z) & 1); v = (z & 1) == 0 ? z / 2 : -(z + 1) / 2; }
        if (z < HALF) sb.append(DIGITS.charAt(z));
        else { z -= HALF; sb.append(DIGITS.charAt(HALF + z / BASE)).append(DIGITS.charAt(z % BASE)); }
        return v;
    }

    /** The start of the match. */
    public void start(Game g, int league) {
        for (int i = 0; i < Physics.N; i++) { start[2 * i] = (int) Math.rint(g.w.px[i] * 1000); start[2 * i + 1] = (int) Math.rint(g.w.py[i] * 1000); }
        System.arraycopy(start, 0, last, 0, last.length);
        global = new LinkedHashMap<>();
        global.put("ring", (int) Math.rint(Physics.RING * 1000));
        global.put("league", league);
        global.put("stamina", g.staminaOn ? 1 : 0);
        global.put("joints", g.jointsOn ? 1 : 0);
        global.put("turns", Game.MAX_TURNS);
        global.put("distance", g.startDistance);
        global.put("pos", start.clone());
        global.put("moves", Moves.NAME);
        global.put("kinds", Physics.KNAME);
        global.put("points", Physics.PNAME);
        global.put("bones", Physics.BONE);
        int[][] shapes = new int[Physics.NS][];
        for (int s = 0; s < Physics.NS; s++) shapes[s] = new int[]{Physics.SH[s][0], Physics.SH[s][1], (int) Math.rint(Physics.SR[s] * 1000), Physics.SH[s][2]};
        global.put("shapes", shapes);
    }

    /** Called when both commands are in, before the turn is simulated. */
    public void commands(Game g) {
        events.clear();
        for (int f = 0; f < 2; f++) {
            moveNow[f] = g.move[f]; frameNow[f] = g.frame[f];
        }
    }

    /** Called when the turn has been simulated, with the points filmed by Game.simulate. */
    public void turn(Game g, int[][] film, boolean firstTurn) {
        frame = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder(4 * 2 * Physics.N * 2);
        for (int[] quarter : film) for (int i = 0; i < quarter.length; i++) last[i] += pack(sb, quarter[i] - last[i]);
        frame.put("p", sb.toString());
        frame.put("h", new int[]{g.w.hp[0], g.w.hp[1]});
        if (g.staminaOn) frame.put("s", new int[]{(g.stamina[0] + 50) / 100, (g.stamina[1] + 50) / 100});
        frame.put("m", moveNow.clone());
        frame.put("f", frameNow.clone());
        if (!g.message[0].isEmpty() || !g.message[1].isEmpty()) frame.put("t", g.message.clone());
        for (int f = 0; f < 2; f++) if (g.stepDir[f] != 0) events.add(new int[]{2, f, g.stepDir[f]});
        for (double[] e : g.w.hitEvents)
            events.add(new int[]{0, (int) e[0], (int) e[1], Physics.SH[(int) e[2]][2], Physics.SH[(int) e[3]][2], (int) e[5], (int) Math.rint(e[6] * 1000), (int) Math.rint(e[7] * 1000), (int) Math.rint(e[4] * 10)});
        for (double[] e : g.w.blockEvents) events.add(new int[]{3, (int) e[0], (int) e[1], (int) Math.rint(e[2] * 1000), (int) Math.rint(e[3] * 1000)});
        for (int f = 0; f < 2; f++) {
            if (g.down[f] || g.out[f]) events.add(new int[]{1, g.fallStep[f], f, g.down[f] ? 0 : 1, g.freeFall[f] ? 1 : 0, g.down[f] ? g.w.downPt[f] : -1});
        }
        if (!events.isEmpty()) frame.put("e", new ArrayList<>(events));
        if (g.stoodUp) { frame.put("r", 1); System.arraycopy(start, 0, last, 0, last.length); }
        if (firstTurn) frame.put("l", new int[][]{g.look[0].clone(), g.look[1].clone()});
    }

    /** How a match ends, as the viewer reads it. */
    public static final int END_TIME = 0, END_KO = 1, END_ELIMINATED = 2, END_STALE = 3, END_ERROR = 4;

    /** The match ends with the turn that was just given: END_TIME, END_KO or END_STALE. */
    public void end(int winner, int how) {
        frame.put("x", new int[]{winner, how});
    }

    /** The referee met an error: the match stops, as a draw. */
    public void error() {
        if (frame == null) frame = new LinkedHashMap<>();       // the error may come before anything of this turn was given to the viewer
        frame.put("x", new int[]{-1, END_ERROR});
    }

    /** The match stops before the turn is played: a player sent an invalid command or nothing. */
    public void stopped(Game g, int winner) {
        frame = new LinkedHashMap<>();
        frame.put("h", new int[]{g.w.hp[0], g.w.hp[1]});
        frame.put("x", new int[]{winner, END_ELIMINATED});
    }

    @Override
    public void onGameInit() {
        gameManager.setViewGlobalData(NAME, global);
    }

    @Override
    public void onAfterGameTurn() {
        if (frame != null) gameManager.setViewData(NAME, frame);
        frame = null;
    }

    @Override
    public void onAfterOnEnd() {
    }
}

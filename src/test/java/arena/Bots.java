package arena;

import com.codingame.game.engine.Game;
import com.codingame.game.engine.Moves;
import com.codingame.game.engine.Physics;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** The bots of the test bench: the 13 named-move bots of the prototype, the search bot, and scripts that try to abuse the rules. */
public final class Bots {
    public static final String[] NAMED = {"Idle", "Turtle", "Boxer", "Kicker", "Counter", "Rusher", "Flailer", "Mixed", "Backer", "Smart", "Brawler", "Jumper", "Ducker"};
    static final Map<String, int[]> LOOKS = new HashMap<String, int[]>();
    static {
        String[] n = {"Idle", "Turtle", "Boxer", "Kicker", "Counter", "Rusher", "Flailer", "Mixed", "Backer", "Smart", "Brawler", "Jumper", "Ducker", "Searcher"};
        int[][] l = {{0, 5}, {4, 2}, {3, 0}, {1, 7}, {6, 9}, {5, 0}, {2, 3}, {7, 9}, {2, 8}, {4, 1}, {6, 0}, {1, 5}, {3, 6}, {0, 7}};
        for (int i = 0; i < n.length; i++) LOOKS.put(n[i], l[i]);
    }
    public static final String ZERO = zero();
    static String zero() { StringBuilder sb = new StringBuilder("JOINTS"); for (int j = 0; j < Physics.NJ; j++) sb.append(" 0 0"); return sb.toString(); }
    static final double LIMP_D = 0.8;

    public static Bot make(final String name, final long seed, final Arena.Opt opt) {
        Bot b = build(name, seed, opt);
        b.rnd = new Random(seed);
        if (b.look == null) b.look = LOOKS.get(name);
        return b;
    }

    static Bot build(final String name, final long seed, final Arena.Opt opt) {
        if (name.equals("Idle")) return new Bot(name) { public String act(Obs o) { return "STANCE"; } };
        if (name.equals("Turtle")) return new Bot(name) { public String act(Obs o) { return "GUARD"; } };
        if (name.equals("Boxer")) return new Bot(name) {          // closes to punching range, jabs and crosses, guards against punches
            int rest = 0;
            public String act(Obs o) {
                if (o.busy()) { if (!o.myMove.startsWith("STEP")) rest = 2; return o.same(); }
                double d = o.dist();
                if (o.oppHandApproach() > 2.5 && d < 1.0) return "GUARD";
                if (rest > 0) { rest--; return "GUARD"; }
                if (d > 0.85) return "STEP_FORWARD";
                if (d < 0.60 && o.roomBehind() > 0.6) return "STEP_BACK";
                if (o.oppGuardUp() && rnd.nextInt(2) == 0) return "OVERHAND";
                return rnd.nextInt(3) == 0 ? "CROSS" : "JAB";
            }
        };
        if (name.equals("Kicker")) return new Bot(name) {         // keeps kicking range: kicks, high kick on a low guard
            public String act(Obs o) {
                if (o.busy()) return o.same();
                double d = o.dist();
                if (o.oppHandApproach() > 2.5 && d < 0.9) return "GUARD";
                if (d > 0.75 && d < 0.92 && o.oppHandsLow()) return "HIGH_KICK";
                if (d < 0.72 && o.roomBehind() > 0.6) return "STEP_BACK";
                if (d > 1.0) return "STEP_FORWARD";
                return "KICK";
            }
        };
        if (name.equals("Counter")) return new Bot(name) {        // waits in guard, counters a spent punch, steps away from kicks
            public String act(Obs o) {
                if (o.busy()) return o.same();
                double d = o.dist();
                if (o.oppFootUp() && d < 1.3 && o.roomBehind() > 0.6) return "STEP_BACK";
                if (o.oppHandOut() > 0.45 && o.oppHandApproach() < 0.5 && d < 0.88) return "CROSS";
                if (d > 1.6) return "STEP_FORWARD";
                return "GUARD";
            }
        };
        if (name.equals("Backer")) return new Bot(name) {         // like Counter, and steps back as soon as it is pushed
            public String act(Obs o) {
                double d = o.dist();
                if ((o.pushedBack() || d < 0.60) && o.roomBehind() > 0.6 && !o.running("STEP_BACK")) return "STEP_BACK";
                if (o.busy()) return o.same();
                if (o.oppHandOut() > 0.45 && o.oppHandApproach() < 0.5 && d < 0.88) return "CROSS";
                if (d > 1.6) return "STEP_FORWARD";
                return "GUARD";
            }
        };
        if (name.equals("Smart")) return new Bot(name) {          // steps out of pushes and kicks, guards punches, strikes in range
            int rest = 0;
            public String act(Obs o) {
                double d = o.dist();
                if ((o.pushedBack() || d < 0.55) && o.roomBehind() > 0.6 && !o.running("STEP_BACK")) return "STEP_BACK";
                if (o.busy()) { if (!o.myMove.startsWith("STEP")) rest = 1; return o.same(); }
                if (o.oppHandApproach() > 2.5 && d < 1.0) return "GUARD";
                if (o.oppFootUp() && d < 1.2 && o.roomBehind() > 0.6) return "STEP_BACK";
                if (rest > 0) { rest--; return "GUARD"; }
                if (d > 1.05) return "STEP_FORWARD";
                if (d > 0.86) return rnd.nextInt(2) == 0 ? "KICK" : "STEP_FORWARD";
                return rnd.nextInt(3) == 0 ? "CROSS" : "JAB";
            }
        };
        if (name.equals("Brawler")) return new Bot(name) {        // loose: relaxed between punches
            public String act(Obs o) {
                if (o.busy()) return o.same();
                double d = o.dist();
                if (d > 0.85) return rnd.nextInt(3) == 0 ? "STEP_FORWARD" : "RELAX";
                return rnd.nextInt(3) == 0 ? (rnd.nextBoolean() ? "CROSS" : "JAB") : "RELAX";
            }
        };
        if (name.equals("Jumper")) return new Bot(name) {         // jumps in when the opponent leans back
            public String act(Obs o) {
                if (o.busy()) return o.same();
                double d = o.dist();
                if (o.oppLean() < -0.07 && d > 0.8 && d < 1.35) return "JUMP";
                if (o.oppHandOut() > 0.45 && o.oppHandApproach() < 0.5 && d < 0.88) return "CROSS";
                if (d > 1.6) return "STEP_FORWARD";
                return "GUARD";
            }
        };
        if (name.equals("Rusher")) return new Bot(name) {
            public String act(Obs o) {
                if (o.busy()) return o.same();
                if (o.dist() > 0.85) return "STEP_FORWARD";
                return o.oppGuardUp() && rnd.nextBoolean() ? "OVERHAND" : "CROSS";
            }
        };
        if (name.equals("Flailer")) return new Bot(name) {        // random joint targets
            public String act(Obs o) {
                StringBuilder sb = new StringBuilder("JOINTS");
                for (int j = 0; j < Physics.NJ; j++) {
                    if (rnd.nextDouble() < 0.3) sb.append(' ').append(Math.round(Physics.JMIN[j] + rnd.nextDouble() * (Physics.JMAX[j] - Physics.JMIN[j]))).append(" 100");
                    else sb.append(" * *");
                }
                return sb.toString();
            }
        };
        if (name.equals("Mixed")) return new Bot(name) {
            public String act(Obs o) {
                if (o.busy()) return o.same();
                double d = o.dist(), r = rnd.nextDouble();
                if (o.oppHandApproach() > 2.5 && d < 1.0 && r < 0.7) return "GUARD";
                if (d > 1.1) return r < 0.8 ? "STEP_FORWARD" : "GUARD";
                if (d < 0.92 && d > 0.75 && o.oppHandsLow() && r < 0.5) return "HIGH_KICK";
                if (d > 0.86) return r < 0.5 ? "KICK" : r < 0.8 ? "STEP_FORWARD" : "GUARD";
                if (d < 0.58 && o.roomBehind() > 0.6) return "STEP_BACK";
                if (o.oppGuardUp() && r < 0.3) return "OVERHAND";
                return r < 0.4 ? "JAB" : r < 0.6 ? "CROSS" : r < 0.75 ? "STEP_BACK" : "GUARD";
            }
        };
        if (name.equals("Ducker")) return new Bot(name) {         // ducks under head kicks and punches, punches on the way up
            int low = 0, rise = 0;
            public String act(Obs o) {
                if (o.busy()) return o.same();
                double d = o.dist();
                boolean steady = Math.abs(o.myComV()) < 0.3;
                if (low > 0) { low--; if (low > 0) return "DUCK"; rise = 3; return "GUARD"; }
                if (rise > 0) { rise--; if (rise > 0 || !steady) return "GUARD"; return d < 0.9 ? (rnd.nextBoolean() ? "CROSS" : "JAB") : "GUARD"; }
                if (steady && o.oppFootHigh() && d < 1.2) { low = 4; return "DUCK"; }
                if (steady && d < 0.95 && (o.oppHandApproach() > 1.0 || rnd.nextInt(4) == 0)) { low = 3; return "DUCK"; }
                if (d > 1.3 && steady) return "STEP_FORWARD";
                if (d < 0.6 && o.roomBehind() > 0.6 && steady) return "STEP_BACK";
                return "GUARD";
            }
        };
        if (name.equals("Script")) return new Bot(name) {         // plays opt.scripts[side], then STANCE
            public String act(Obs o) { String[] sc = opt.scripts[o.me]; return o.turn < sc.length ? sc[o.turn] : "STANCE"; }
        };
        if (name.equals("Limp")) return new Bot(name) {           // guards; close to the opponent and with no hit in the window, lets every muscle go
            public String act(Obs o) { return o.dist() < LIMP_D && o.sinceHit > Game.WINDOW ? ZERO : "GUARD"; }
        };
        if (name.equals("LeadLimp")) return new Bot(name) {       // Mixed until it leads, then Limp
            final Bot in = make("Mixed", seed, opt);
            public String act(Obs o) {
                if (o.hp[o.me] <= o.hp[o.opp] || o.busy()) return in.act(o);
                return o.dist() < LIMP_D && o.sinceHit > Game.WINDOW ? ZERO : "GUARD";
            }
        };
        if (name.equals("NanLead")) return new Bot(name) {        // Mixed until it leads, then sends NaN as a joint target
            final Bot in = make("Mixed", seed, opt);
            public String act(Obs o) {
                if (o.hp[o.me] <= o.hp[o.opp] || o.busy()) return in.act(o);
                return nan();
            }
        };
        if (name.equals("Runner")) return new Bot(name) {         // Mixed until it leads, then keeps away: steps back while there is room, fights when cornered
            final Bot in = make("Mixed", seed, opt);
            public String act(Obs o) {
                if (o.busy()) return o.same();
                if (o.hp[o.me] <= o.hp[o.opp]) return in.act(o);
                if (o.dist() < 1.5 && o.roomBehind() > 0.9) return "STEP_BACK";
                if (o.dist() < 1.1) return in.act(o);
                return "GUARD";
            }
        };
        if (name.startsWith("Hold:")) { final String pose = name.substring(5); return new Bot(name) { public String act(Obs o) { return pose; } }; }
        if (name.startsWith("Lead:")) {          // Mixed until it leads, then holds a pose for the rest of the match
            final String pose = name.substring(5); final Bot in = make("Mixed", seed, opt);
            return new Bot(name) { public String act(Obs o) { return (o.hp[o.me] <= o.hp[o.opp] || o.busy()) ? in.act(o) : pose; } };
        }
        if (name.startsWith("SLead:")) {         // the 50 ms search bot until it leads, then it stalls
            final String how = name.substring(6); final Bot in = make("S:20:6:0:1:1", seed, opt);
            return new Bot(name) {
                public String act(Obs o) { return o.hp[o.me] <= o.hp[o.opp] ? in.act(o) : stall(how, o, in); }
            };
        }
        if (name.startsWith("Late:")) {          // Late:N:how = the 50 ms search bot; in the last N turns, if it leads, it stalls
            final String[] q = name.split(":"); final int last = Integer.parseInt(q[1]); final String how = q[2]; final Bot in = make("S:20:6:0:1:1", seed, opt);
            return new Bot(name) {
                public String act(Obs o) { return o.turn < Game.MAX_TURNS - last || o.hp[o.me] <= o.hp[o.opp] ? in.act(o) : stall(how, o, in); }
            };
        }
        if (name.startsWith("Fling:")) {         // walks in, then plays the HIGH_KICK frames by hand with ROUND on for the first K frames only
            final int K = Integer.parseInt(name.substring(6));
            return new Bot(name) {
                int k = -1, rest = 0;
                public String act(Obs o) {
                    int n = Moves.LENGTH[Moves.HIGH_KICK];
                    if (k > 0 && !o.myMove.equals("JOINTS")) k = -1;       // a reset interrupted the kick
                    if (k < 0) {
                        if (o.busy()) return o.same();
                        if (rest > 0) { rest--; return "STANCE"; }
                        if (o.dist() > 0.92) return "STEP_FORWARD";
                        if (o.dist() < 0.74 && o.roomBehind() > 0.6) return "STEP_BACK";
                        k = 0;
                    }
                    String s = Searcher.line(Moves.T[Moves.HIGH_KICK][k], Moves.S[Moves.HIGH_KICK][k], k < K);
                    if (++k >= n) { k = -1; rest = 2; }
                    return s;
                }
            };
        }
        if (name.startsWith("Round:") || name.startsWith("Raw:")) {      // a named-move bot played through JOINTS, with ROUND always on (Round:) or never (Raw:)
            final boolean round = name.startsWith("Round:");
            final Bot in = make(name.substring(name.indexOf(':') + 1), seed, opt);
            return new Bot(name) {
                int mv = Moves.STANCE, fr = 0;
                public String act(Obs o) {
                    if (o.turn == 0 || !o.myMove.equals("JOINTS")) { mv = Moves.STANCE; fr = 0; }       // start, or back from a reset
                    if (Moves.HELD[mv] || fr >= Moves.LENGTH[mv]) {
                        String c = in.act(shadow(o, "STANCE", 0));
                        int m = Moves.index(c.split(" ")[0]);
                        mv = m < 0 ? Moves.STANCE : m; fr = 0;
                    }
                    int f = Moves.HELD[mv] ? 0 : fr++;
                    return Searcher.line(clampT(Moves.T[mv][f]), Moves.S[mv][f], round);
                }
            };
        }
        if (name.startsWith("S:")) {
            String[] q = name.split(":");            // S:plans:turns:model:balance:defence[:energy]
            Searcher sr = new Searcher(name, Integer.parseInt(q[1]), Integer.parseInt(q[2]), Integer.parseInt(q[3]), Double.parseDouble(q[4]), Double.parseDouble(q[5]), opt.stamina, opt.joints);
            if (q.length > 6) sr.WEN = Double.parseDouble(q[6]);
            sr.look = LOOKS.get("Searcher");
            return sr;
        }
        throw new IllegalArgumentException(name);
    }

    /** The ways of sitting on a lead. */
    static String stall(String how, Obs o, Bot in) {
        if (how.equals("NAN")) return nan();
        if (how.equals("LIMP")) return o.dist() < LIMP_D && o.sinceHit > Game.WINDOW ? ZERO : "GUARD";
        if (how.equals("FLOP")) {            // new rule: touch, then drop while no hit was landed in the window
            if (o.busy()) return o.same();
            if (o.sinceHit > Game.WINDOW && o.sinceContact <= 3) return ZERO;
            return o.dist() > 0.75 ? "STEP_FORWARD" : "GUARD";
        }
        if (how.equals("BACK")) {
            if (o.myMove.startsWith("STEP")) return o.myMove;
            if (o.dist() < 1.5 && o.roomBehind() > 0.9) return "STEP_BACK";
            return o.dist() < 1.1 ? in.act(o) : "GUARD";
        }
        return how;
    }

    static int[] clampT(int[] t) {
        int[] r = new int[Physics.NJ];
        for (int j = 0; j < Physics.NJ; j++) r[j] = Math.max(Physics.JMINC[j], Math.min(Physics.JMAXC[j], t[j]));
        return r;
    }
    /** The same observation with another current move (for bots wrapped by a bot that drives the joints). */
    static Obs shadow(Obs o, String move, int frame) { o.myMove = move; o.myFrame = frame; return o; }

    static String nan() {
        StringBuilder sb = new StringBuilder("JOINTS NaN 50");
        for (int j = 1; j < Physics.NJ; j++) sb.append(" 10 50");
        return sb.toString();
    }
}

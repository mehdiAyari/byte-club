import java.util.Properties;

import com.codingame.gameengine.runner.MultiplayerGameRunner;

/**
 * Plays one match between two bench bots and serves the viewer on http://localhost:8888.
 * Arguments: [bot A] [bot B] [league 1-3] [seed] [distance in mm]. Example: Mixed Boxer 3 7 1800
 * Switches: -Dport=8888 (the port of the viewer), -Dsay=true (the bots add a message to every command).
 */
public class Main {
    public static void main(String[] args) {
        String a = args.length > 0 ? args[0] : "Mixed", b = args.length > 1 ? args[1] : "Boxer";
        int league = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        long seed = args.length > 3 ? Long.parseLong(args[3]) : 1;
        MultiplayerGameRunner runner = new MultiplayerGameRunner();
        runner.setLeagueLevel(league);
        runner.setSeed(seed);
        if (args.length > 4) {
            Properties p = new Properties();
            p.setProperty("distance", args[4]);
            runner.setGameParameters(p);
        }
        boolean say = Boolean.getBoolean("say");
        runner.addAgent(arena.Agent.commandLine(a, seed * 31 + 1, league, say), a);
        runner.addAgent(arena.Agent.commandLine(b, seed * 31 + 2, league, say), b);
        runner.start(Integer.getInteger("port", 8888));
    }
}

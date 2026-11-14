package arena;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * A bench bot as a program that talks to the referee: java arena.Agent NAME [seed] [stamina true|false] [joints true|false] [say].
 * Two names exist only here, to test the referee: Sleep:N (Boxer that answers too late on turn N) and Quit:N (Boxer that stops on turn N).
 */
public final class Agent {
    /** The command line that runs a bench bot as a separate program, from the folder of the project. */
    public static String commandLine(String name, long seed, int league) {
        String cp = new java.io.File("target/test-classes").getAbsolutePath() + java.io.File.pathSeparator + new java.io.File("target/classes").getAbsolutePath();
        return "java -cp " + cp + " arena.Agent " + name + " " + seed + " " + (league >= 3) + " " + (league >= 2);
    }

    /** The same, with a message after every command when say is true (to test how the viewer shows messages). */
    public static String commandLine(String name, long seed, int league, boolean say) {
        return commandLine(name, seed, league) + (say ? " say" : "");
    }

    public static void main(String[] args) throws IOException {
        Arena.Opt opt = new Arena.Opt();
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 1;
        if (args.length > 2) opt.stamina = Boolean.parseBoolean(args[2]);
        if (args.length > 3) opt.joints = Boolean.parseBoolean(args[3]);
        boolean say = args.length > 4 && args[4].equals("say");
        int sleepAt = args[0].startsWith("Sleep:") ? Integer.parseInt(args[0].substring(6)) : -1, quitAt = args[0].startsWith("Quit:") ? Integer.parseInt(args[0].substring(5)) : -1;
        Bot bot = Bots.make(sleepAt >= 0 || quitAt >= 0 ? "Boxer" : args[0], seed, opt);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        Obs obs = new Obs();
        String first = in.readLine();
        if (first == null) return;
        obs.init(first);
        while (true) {
            List<String> lines = new ArrayList<String>(72);
            for (int i = 0; i < 72; i++) {
                String s = in.readLine();
                if (s == null) return;
                lines.add(s);
            }
            obs.read(lines);
            if (obs.turn == quitAt) return;
            if (obs.turn == sleepAt) try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
            String c = bot.act(obs);
            if (obs.turn == 0 && bot.look != null) c = "LOOK " + bot.look[0] + " " + bot.look[1] + " " + c;
            if (say) c += obs.turn % 50 >= 20 && obs.turn % 50 < 30 ? " a long message, cut by the referee at 40 characters" : " " + args[0].toLowerCase() + " t" + obs.turn;
            System.out.println(c);
            System.out.flush();
        }
    }
}

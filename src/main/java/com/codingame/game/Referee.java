package com.codingame.game;

import java.util.List;
import java.util.Locale;
import java.util.Properties;

import com.codingame.game.engine.Game;
import com.codingame.game.engine.Moves;
import com.codingame.game.engine.Physics;
import com.codingame.game.view.FightModule;
import com.codingame.gameengine.core.AbstractPlayer.TimeoutException;
import com.codingame.gameengine.core.AbstractReferee;
import com.codingame.gameengine.core.GameManager;
import com.codingame.gameengine.core.MultiplayerGameManager;
import com.codingame.gameengine.module.endscreen.EndScreenModule;
import com.google.inject.Inject;

/**
 * Runs a match: gives each player its input, reads one line from each, and lets the engine play the turn.
 * League 1: the named moves. League 2: + JOINTS. League 3: + stamina.
 */
public class Referee extends AbstractReferee {
    static final int FIRST_TURN_MS = 1000, TURN_MS = 50, FRAME_MS = 100, SUMMARY_MAX = 760;
    static final String[] VERB = {"", "", "", "punches", "", "kicks", "", "knees"};       // by kind of the striking part

    @Inject private MultiplayerGameManager<Player> gameManager;
    @Inject private EndScreenModule endScreen;
    @Inject private FightModule view;

    private Game game;
    private final String[] lost = new String[2];          // why a player was eliminated, or null
    private int summaryLeft;

    @Override
    public void init() {
        int league = gameManager.getLeagueLevel();
        int distance = Game.DISTANCE_MIN + 2 * gameManager.getRandom().nextInt((Game.DISTANCE_MAX - Game.DISTANCE_MIN) / 2 + 1);
        Properties params = gameManager.getGameParameters();
        String asked = params.getProperty("distance");
        if (asked != null) {
            try {
                distance = Math.max(Game.DISTANCE_MIN, Math.min(Game.DISTANCE_MAX, Integer.parseInt(asked.trim()))) / 2 * 2;
            } catch (NumberFormatException e) {
                // not a number: the distance drawn from the seed is kept
            }
        }
        params.setProperty("distance", String.valueOf(distance));
        game = new Game(distance, league >= 3, league >= 2);
        view.start(game, league);
        gameManager.setMaxTurns(Game.MAX_TURNS);
        gameManager.setFirstTurnMaxTime(FIRST_TURN_MS);
        gameManager.setTurnMaxTime(TURN_MS);
        gameManager.setFrameDuration(FRAME_MS);
    }

    @Override
    public void gameTurn(int turn) {
        summaryLeft = SUMMARY_MAX;
        boolean first = game.turn == 0;
        for (Player p : gameManager.getPlayers()) {
            int i = p.getIndex();
            if (first) p.sendInputLine(game.initLine(i));
            for (String line : game.inputLines(i)) p.sendInputLine(line);
            p.execute();
        }
        for (Player p : gameManager.getPlayers()) {
            int i = p.getIndex();
            try {
                List<String> out = p.getOutputs();
                lost[i] = game.apply(i, out.isEmpty() ? null : out.get(0));
            } catch (TimeoutException e) {
                lost[i] = "no command received within " + (first ? FIRST_TURN_MS : TURN_MS) + " ms";
            }
        }
        if (lost[0] != null || lost[1] != null) {         // an invalid command or a timeout loses the match at once; both: a draw
            for (Player p : gameManager.getPlayers()) {
                int i = p.getIndex();
                if (lost[i] == null) { p.setScore(game.score(i)); continue; }
                String why = p.getNicknameToken() + " is eliminated: " + lost[i];
                p.setScore(-1);
                p.deactivate(why);                            // also shows the reason as a tooltip on the player
                say(GameManager.formatErrorMessage(why));
            }
            view.stopped(game, lost[0] == null ? 0 : lost[1] == null ? 1 : -1);
            gameManager.endGame();
            return;
        }
        view.commands(game);
        int[][] film = new int[4][2 * Physics.N];
        game.film = film;
        game.simulate();
        view.turn(game, film, first);
        tell();
        for (Player p : gameManager.getPlayers()) p.setScore(game.score(p.getIndex()));
        if (game.over()) {
            int w = game.winner();
            boolean ko = game.w.hp[0] <= 0 || game.w.hp[1] <= 0;
            view.end(w, ko);
            if (w < 0) say(ko ? "Double knockout: a draw." : "Time. A draw: " + game.score(0) + " HP each.");
            else {
                Player p = gameManager.getPlayer(w);
                String how = ko ? p.getNicknameToken() + " wins by knockout." : "Time. " + p.getNicknameToken() + " wins with " + game.score(w) + " HP against " + game.score(1 - w) + ".";
                say(GameManager.formatSuccessMessage(how));
                if (ko) gameManager.addTooltip(p, how);
            }
            gameManager.endGame();
        }
    }

    /** The summary of the turn that was just played. */
    private void tell() {
        String[] name = {gameManager.getPlayer(0).getNicknameToken(), gameManager.getPlayer(1).getNicknameToken()};
        for (int f = 0; f < 2; f++) if (game.stepDir[f] != 0) say(name[f] + " takes a step " + (game.stepDir[f] < 0 ? "back" : "forward") + " to keep its balance.");
        for (double[] e : game.w.hitEvents) {
            int att = (int) e[1], dmg = (int) e[5];
            if (dmg <= 0) continue;
            say(name[att] + " " + VERB[Physics.SH[(int) e[2]][2]] + " " + name[1 - att] + " on the " + Physics.KNAME[Physics.SH[(int) e[3]][2]]
                + String.format(Locale.ROOT, " at %.1f m/s: -%d HP.", e[4], dmg));
        }
        for (int f = 0; f < 2; f++) if (game.w.blockedBy[f] > 0) say(name[1 - f] + " stops a strike of " + name[f] + " with its arms.");
        for (int f = 0; f < 2; f++) {
            if (!game.down[f] && !game.out[f]) continue;
            String what = game.down[f] ? name[f] + " falls (" + Physics.PNAME[game.w.downPt[f]] + " on the floor)" : name[f] + " steps out of the ring";
            if (game.freeFall[f]) say(what + ": no HP lost, the fighters were pushing each other and no strike had scored for " + Game.WINDOW + " turns.");
            else {
                say(what + ": -" + Game.FALL + " HP.");
                gameManager.addTooltip(gameManager.getPlayer(f), what + ": -" + Game.FALL + " HP");
            }
        }
        if (game.stoodUp && !game.over()) say("Both fighters go back to their starting places.");
    }

    private void say(String line) {
        if (line.length() + 1 > summaryLeft) return;
        summaryLeft -= line.length() + 1;
        gameManager.addToGameSummary(line);
    }

    @Override
    public void onEnd() {
        int[] scores = new int[2];
        String[] texts = new String[2];
        for (Player p : gameManager.getPlayers()) {
            int i = p.getIndex();
            scores[i] = p.getScore();
            texts[i] = lost[i] != null ? "eliminated" : scores[i] + " HP";
        }
        endScreen.setScores(scores, texts);
    }
}

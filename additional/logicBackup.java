package sc.player2019.logic;

import java.util.ArrayList;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sc.player2019.Starter;
import sc.plugin2019.Field;
import sc.plugin2019.GameState;
import sc.plugin2019.IGameHandler;
import sc.plugin2019.Move;
import sc.plugin2019.Player;
import sc.shared.GameResult;
import sc.shared.InvalidGameStateException;
import sc.shared.InvalidMoveException;
import sc.shared.PlayerColor;

/**
 * Das Herz des Clients: Eine sehr simple Logik, die ihre Zuege zufaellig
 * waehlt, aber gueltige Zuege macht. Ausserdem werden zum Spielverlauf
 * Konsolenausgaben gemacht.
 */
public class Logic implements IGameHandler {

	private Starter client;
	private GameState gameState;
	private Player currentPlayer;

	private static final Logger log = LoggerFactory.getLogger(Logic.class);

	/**
	 * Erzeugt ein neues Strategieobjekt, das zufaellige Zuege taetigt.
	 *
	 * @param client Der zugrundeliegende Client, der mit dem Spielserver
	 *               kommuniziert.
	 */
	public Logic(Starter client) {
		this.client = client;
	}

	/**
	 * {@inheritDoc}
	 */
	public void gameEnded(GameResult data, PlayerColor color, String errorMessage) {
		log.info("Das Spiel ist beendet.");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onRequestAction() {
		long startTime = System.nanoTime();
		log.info("Es wurde ein Zug angefordert.");
		ArrayList<Move> possibleMoves = gameState.getPossibleMoves();

		// Wenn ohnehin nur ein Move möglich ist, dann mache diesen ohne weitere
		// Berechnungen, da wir diesen sowieso machen müssen, um nicht zu verlieren.
		if (possibleMoves.size() == 1) {
			sendAction(possibleMoves.get(0));
			return;
		}
		// überprüfe, ob wir mit dem nächsten Zug gewinnen können:

		Move winningMove = getWinningMove(possibleMoves);
		if (winningMove != null) {
			System.out.println("Wir haben einen Winning-Move gefunden!");
			sendAction(winningMove);
			return;
		}

		Set<Field> fs = gameState.getOwnFields(currentPlayer);
		ArrayList<Field> fields = new ArrayList<Field>(fs);
		System.out.println("Es existieren " + fields.size() + " Fische!");
		/*
		 * for (Field f : fields) { System.out.println("X-Location: " + f.getX() +
		 * " Y-Location: " + f.getY()); }
		 */
		ArrayList<ArrayList<Field>> swarms = getSwarms(fields);
		System.out.println("Es sind " + swarms.size() + " Schwärme gefunden worden.");

		sendAction(possibleMoves.get((int) (Math.random() * possibleMoves.size())));
		long endTime = System.nanoTime();
		long duration = endTime - startTime;
		System.out.println("Das Programm hat " + (double) duration / Math.pow(10, 9) + " Sekunden (" + duration
				+ " Nanosekunden) gedauert.");
	}

	/**
	 * 
	 * @param possibleMoves ArrayList mit allen Möglichen Moves.
	 * @return Move, mit dem gewonnen werden kann oder null, wenn es keinen Winning
	 *         move gibt.
	 */
	private Move getWinningMove(ArrayList<Move> possibleMoves) {
		GameState newGS = gameState.clone();
		for (Move m : possibleMoves) {
			try {
				m.perform(newGS);
				int swarmSize = newGS.greatestSwarmSize(currentPlayer.getPlayerColor());
				int numFish = newGS.getOwnFields(currentPlayer).size();
				if (swarmSize == numFish)
					return m;
			} catch (InvalidGameStateException e) {
				e.printStackTrace();
			} catch (InvalidMoveException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	private ArrayList<ArrayList<Field>> getSwarms(ArrayList<Field> allFish) {
		ArrayList<ArrayList<Field>> output = new ArrayList<ArrayList<Field>>();
		output.add(new ArrayList<Field>());
		for (Field fish : allFish) {
			for (Field f : allFish) {
				if (f.equals(fish))
					continue;
			}
		}

		/*
		 * for (Field fish : allFish) { boolean foundAdjacent = false; for
		 * (ArrayList<Field> al : output) { for (Field f : al) { if (foundAdjacent =
		 * isAdjacent(f, fish)) { al.add(fish); break; } } if (foundAdjacent) break; }
		 * if (!foundAdjacent) { output.add(getNewSwarm(fish)); }
		 * 
		 * }
		 */
		return output;
	}

	public ArrayList<Field> getNewSwarm(Field f) {
		ArrayList<Field> swarm = new ArrayList<Field>();
		swarm.add(f);
		return swarm;
	}

	public boolean isAdjacent(Field a, Field b) {
		return (Math.abs(a.getX() - b.getX()) <= 1 && Math.abs(a.getY() - b.getY()) <= 1);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onUpdate(Player player, Player otherPlayer) {
		currentPlayer = player;
		log.info("Spielerwechsel: " + player.getPlayerColor());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onUpdate(GameState gameState) {
		this.gameState = gameState;
		currentPlayer = gameState.getCurrentPlayer();
		log.info("Zug: {} Spieler: {}", gameState.getTurn(), currentPlayer.getPlayerColor());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sendAction(Move move) {
		client.sendMove(move);
	}

}


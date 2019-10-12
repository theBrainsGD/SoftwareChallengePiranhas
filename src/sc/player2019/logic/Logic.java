package sc.player2019.logic;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sc.framework.plugins.Player;
import sc.player2019.Starter;
import sc.plugin2019.GameState;
import sc.plugin2019.IGameHandler;
import sc.plugin2019.Move;
import sc.plugin2019.util.GameRuleLogic;
import sc.shared.GameResult;
import sc.shared.InvalidGameStateException;
import sc.shared.InvalidMoveException;
import sc.shared.PlayerColor;

/**
 * <h1>Taktik:</h1>
 * <ol style="list-style-type:upper-roman;">
 * <li>Lass dir alle möglichen Moves geben.</li>
 * <li>Wenn nur ein Move möglich ist, dann mache diesen Move.</li>
 * <li>Wenn mehrere Moves möglich sind, filtere die Moves heraus, die den Gegner
 * gewinnen lassen, sodass nur noch die Moves übrig sind, die wirklich
 * vorteilhaft sind.</li>
 * <li>Wenn nur noch ein Move übrig ist, dann mache diesen Move.</li>
 * <li>Überprüfe, ob ein Move den Sieg ermglicht. Wenn ja, dann mache diesen
 * Move.</li>
 * <li>
 * <ul>
 * <li>Suche den Move, der für uns die meisten Punkte bringt.</li>
 * <li>Alternativ dazu könnte auch der Move genommen werden, der den Pirahna
 * nimmt, welcher am weitesten außen ist und diesen soweit wie möglich in die
 * Mitte befördert.</li>
 * <li>Oder eine Mischung daraus. Man versucht die ersten 5 (empirische
 * Forschungen müssen noch getätigt werden) Moves die Pirahnas in die Mitte zu
 * befördern und den Rest der Zeit wird versucht ein Schwarm daraus zu
 * bilden.</li>
 * <li>Des Weiteren kannn eine destruktiver Ansatz gewählt werden. Dabei wird
 * immer der Move gewählt, der die aktuellen Punkte des Gegeners minimiert.</li>
 * <li>Daraus resultieren könnte dann eine Mischung aus den Ansätzen, die die
 * meisten Punkte für uns zu suchen oder die Punkte des Gegners minimieren.
 * Dabei kann eine Logik implementiert werden, die auf die eigenen Punkte achtet
 * und die Punkte des Gegners und entsprechend den besseren Ansatz wählt. Wenn
 * der destruktive Ansatz die Punkte des Gegners unter die unsrigen befördert
 * ist dieser der richtige, alternativ, wenn der positive Ansatz die eignen
 * Punkte über die des Gegners befördert, ist dieser der Richtige. Im Zweifel,
 * weil beide nicht funktionieren ist der Ansatz zu wählen, der Ansatz zu
 * wählen, der die Diskrepanz der Punkte minimiert. Umgekehrt, wenn beide
 * Ansätze die Eigene Punkte über die des Gegners befördern ist der Ansatz zu
 * wählen, der die größere Diskrepanz verursacht.</li>
 * </ul>
 * </li>
 * </ol>
 * 
 */
public class Logic implements IGameHandler {

	private Starter client;
	private GameState gameState;
	private Player currentPlayer;
	private ArrayList<Move> possibleMoves;
	private boolean alreadySend = false;

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
		long startTime = System.currentTimeMillis();
		log.info("Es wurde ein Zug angefordert.");
		alreadySend = false;
		possibleMoves = GameRuleLogic.getPossibleMoves(gameState); // (I)
		SafetyTimer safetyTimer = new SafetyTimer();
		safetyTimer.start();
		// Wenn es nur einen Move gibt, dann mache keine weiteren Berechnungen! (II)
		if (possibleMoves.size() == 1) {
			sendAction(possibleMoves.get(0));
			printEndTime(startTime);
			safetyTimer.interrupt();
			return;
		}

		possibleMoves = getValidMoves(gameState, possibleMoves); // (III)

		// Wenn es keinen Move gibt, dann mache keine weiteren Berechnungen! (IV)
		if (possibleMoves.size() == 0) {
			sendAction(GameRuleLogic.getPossibleMoves(gameState).get(0));
			log.info(
					"Alle Moves wurde als \"loosing\" Move eingestuft und erzwingen ein verlieren. Infolgedessen ist eine Niederlage unvermeintlich und der erstmögliche Move wird gesendet!");
			printEndTime(startTime);
			safetyTimer.interrupt();
			return;
		} else if (possibleMoves.size() == 1) {
			// es gibt nur einen mögliche Move, wir können nur diesen ausführen.
			sendAction(possibleMoves.get(0));
			log.info("Nur dieser Move kann die Niederlage noch verhindern!");
			printEndTime(startTime);
			safetyTimer.interrupt();
			return;
		}

		// Checke, ob es einen Move gibt, der uns gewinnen lässt. (V)
		if (performWinningMove(possibleMoves)) {
			safetyTimer.interrupt();
			return;
		}

		// suche den bestmöglichen Move nach bestimmten Kriterien aus (VI)
		Move m = null;
		do {
			m = getWeightedMove(possibleMoves);
			possibleMoves.remove(m);
		} while (combinedSwarm(m) && possibleMoves.size() > 1);

		sendAction(m);
		printEndTime(startTime);
		safetyTimer.interrupt();
	}

	private boolean performWinningMove(ArrayList<Move> moves) {
		GameState gs;
		for (Move m : moves) {
			try {
				gs = gameState.clone();
				m.perform(gs);
				if (isWinning(gs, currentPlayer.getColor())) {
					sendAction(m);
					return true;
				}
			} catch (InvalidMoveException | InvalidGameStateException e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	private boolean isWinning(GameState gs, PlayerColor pc) {
		return gs.getPointsForPlayer(pc) == countFishsOfPlayer(gs, pc);
	}

	/**
	 * <h2>(VI.1)</h2> Findet den Move, der die meisten Punkte (Score) ermöglicht.
	 * Hierbei werden alle möglichen Moves ausprobiert und der Move mit den meisten
	 * Punkten für die nächste Runde wird zurückgegeben.
	 * 
	 * 
	 * @param possibleMoves
	 * @return Der Move mit der höchsten Bewertung. Wenn alle mit 0 Punkten bewertet
	 *         werden wird der erste Move zurückgegeben.
	 */
	@SuppressWarnings("unused")
	private Move getBestMove(ArrayList<Move> possibleMoves) {
		int bestPoints = 0;
		Move bestMove = possibleMoves.get((int) (Math.random() * possibleMoves.size()));

		for (Move m : possibleMoves) {
			GameState gs = gameState.clone();
			try {
				m.perform(gs);
				int points = gs.getPointsForPlayer(currentPlayer.getColor());
				if (points > bestPoints) {
					bestPoints = points;
					bestMove = m;
				}
			} catch (InvalidGameStateException | InvalidMoveException e) {
				log.info("Es ist ein Fehler in der Funktion \"getBestMove()\" aufgetreten.");
			}
		}
		return bestMove;

	}

	/**
	 * <h2>(VI.4)</h2> Mit dieser Funktion wird der Move gesucht, der die Punkte des
	 * Gegners bestmöglich minimiert.
	 * 
	 *
	 * 
	 * @param possibleMoves
	 * @return
	 */
	@SuppressWarnings("unused")
	private Move getBestDestructiveMove(ArrayList<Move> possibleMoves) {
		int smallestPoints = 100;
		Move bestMove = possibleMoves.get((int) (Math.random() * possibleMoves.size()));

		for (Move m : possibleMoves) {
			GameState gs = gameState.clone();
			try {
				m.perform(gs);
				int points = gs.getPointsForPlayer(
						currentPlayer.getColor() == PlayerColor.BLUE ? PlayerColor.RED : PlayerColor.BLUE);
				if (points < smallestPoints) {
					smallestPoints = points;
					bestMove = m;
				}
			} catch (InvalidGameStateException | InvalidMoveException e) {
				log.info("Es ist ein Fehler in der Funktion \"getBestMove()\" aufgetreten.");
			}
		}
		return bestMove;
	}

	/**
	 * Diese Funktion überprüft, ob der der nächste Spieler, der den übergebenen
	 * Move nicht ausführt durch diesen Move in der nächsten Runde einen
	 * zusammenhägenden Schwarm hat. <br>
	 * Dies bietet sich an, wenn man prüfen möchte, ob ein Move erlaubt sein sollte.
	 * 
	 * @return true, wenn es einen zusammenhängenden Schwarm gibt, false, wenn
	 *         nicht.
	 */
	private boolean combinedSwarm(Move move) {
		GameState gs = gameState.clone();
		try {
			move.perform(gs);
			ArrayList<Move> moves = GameRuleLogic.getPossibleMoves(gs);
			for (Move m : moves) {
				GameState gs_ = gs.clone();
				m.perform(gs_);
				if (isWinning(gs_, gs.getCurrentPlayerColor())) {
					log.info("Oh oh! Der Move würde dem Gegner einen Sieg ermöglichen!");
					return true;
				}
			}
		} catch (InvalidGameStateException | InvalidMoveException e) {
			e.printStackTrace();
		}
		log.info("Dieser Move scheint ok zu sein!");
		return false;
	}

	/**
	 * <h1>Methode für Logik 1.1.0</h1> Wenn ich meine Schwarm vergrößern kann, dann
	 * wird dieser Move gewählt. Wenn ein anderer Move auch diese Größe für den
	 * Schwarm erreichen kann, dann wird der Move ausgewählt, der den Schwarm des
	 * Gegners am meisten verkleinert.
	 * 
	 * @param possibleMoves
	 * @return
	 *//*
		 * private Move getWeightedMove(ArrayList<Move> possibleMoves) {
		 * 
		 * GameState gs = gameState.clone();
		 * 
		 * Move move = possibleMoves.get((int) (Math.random() * possibleMoves.size()));
		 * 
		 * int myPoints = gs.getPointsForPlayer(currentPlayer.getColor()); int oppPoints
		 * = gs .getPointsForPlayer(currentPlayer.getColor() == PlayerColor.BLUE ?
		 * PlayerColor.RED : PlayerColor.BLUE);
		 * 
		 * for (Move m : possibleMoves) { gs = gameState.clone(); try { m.perform(gs);
		 * 
		 * int mPoints = gs.getPointsForPlayer(currentPlayer.getColor()); int
		 * opponentPoints = gs.getPointsForPlayer( currentPlayer.getColor() ==
		 * PlayerColor.BLUE ? PlayerColor.RED : PlayerColor.BLUE); if (mPoints >
		 * myPoints) { myPoints = mPoints; oppPoints = opponentPoints; move = m; } else
		 * if (mPoints == myPoints) { if (opponentPoints < oppPoints) { myPoints =
		 * mPoints; oppPoints = opponentPoints; move = m; } } } catch
		 * (InvalidGameStateException | InvalidMoveException e) { e.printStackTrace(); }
		 * }
		 * 
		 * return move; }
		 */

	/**
	 * <h2>(VI.5)</h2>
	 * <h3>BEACHTE:</h3> Diese Funktion bedenkt nicht, dass ein zusammenhängender
	 * Schwarm evtl kleiner ist, als der größte Schwarm des Gegers. Entsprechend
	 * muss vorher sichergestellt werden, dass der Gegner keinen zusammenhägenden
	 * Schwarm erschaffen kann.
	 * 
	 * <br>
	 * Die Funktionslogik muss dringenst nochmal überarbeitet werden. Die Definition
	 * der Funktion ist schon im JavaDoc der Klasse beschrieben. Die Funktion setzt
	 * diese Logik allerdings nicht korrekt um, sodass die Funktion nur semioptimal
	 * arbeitet. Was einige Testmatches jedoch zeigen ist diese Logik gar nicht mal
	 * so schlecht.
	 * 
	 * @param possibleMoves
	 * @return
	 */

	private Move getWeightedMove(ArrayList<Move> possibleMoves) {
		int difference = -100;
		// wenn die if-Abfrage mit absoluten Zahlen genutzt wird, dann muss die
		// Differenz am Anfang auf +100 gesetzt werden!

		// Am Anfang wird ein zufälliger Move ausgewählt. Wenn es keinen besseren Move
		// gibt, dann ist der Algorithmus nicht vorersehbar
		Move move = possibleMoves.get((int) (Math.random() * possibleMoves.size()));

		for (Move m : possibleMoves) {
			GameState gs = gameState.clone();
			try {
				m.perform(gs);

				int myPoints = gs.getPointsForPlayer(currentPlayer.getColor());
				int opponentPoints = gs.getPointsForPlayer(
						currentPlayer.getColor() == PlayerColor.BLUE ? PlayerColor.RED : PlayerColor.BLUE);
				int diff = myPoints - opponentPoints;
				if (diff > difference) {
					difference = diff;
					move = m;
				}

			} catch (InvalidGameStateException | InvalidMoveException e) {
				e.printStackTrace();
			}
		}

		return move;
	}

	@SuppressWarnings("unused")
	private Move getWeightedMoveBackup(ArrayList<Move> possibleMoves) {
		int difference = -100;
		// wenn die if-Abfrage mit absoluten Zahlen genutzt wird, dann muss die
		// Differenz am Anfang auf +100 gesetzt werden!
		Move move = possibleMoves.get((int) (Math.random() * possibleMoves.size()));

		for (Move m : possibleMoves) {
			GameState gs = gameState.clone();
			try {
				m.perform(gs);

				int myPoints = gs.getPointsForPlayer(currentPlayer.getColor());
				int opponentPoints = gs.getPointsForPlayer(
						currentPlayer.getColor() == PlayerColor.BLUE ? PlayerColor.RED : PlayerColor.BLUE);
				int diff = myPoints - opponentPoints;
				// if (Math.abs(diff) < difference) { // ist das vielleicht sogar besser?
				if (diff > difference) {
					difference = diff;
					move = m;
				}

			} catch (InvalidGameStateException | InvalidMoveException e) {
				e.printStackTrace();
			}
		}

		return move;
	}

	/**
	 * Gebe in der Konsole die Dauer der Funktion aus, um abschätzen zu können, wie
	 * lange es gedauert hat, den Move zu finden.
	 * 
	 * @param startTime die Anfangszeit, beim Aufrufen der Funktion
	 */
	void printEndTime(long startTime) {
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;
		System.out.println("Das Programm hat " + (double) duration / 1000.0 + " Sekunden (" + duration
				+ " Millisekunden) gedauert!");
	}

	/**
	 * Diese Funktion filtert alle Moves aus einer Liste an Moves, die erlaubt sind.
	 * Das bedeutet, es werden alle Moves herausgefiltert, einen Sieg für den Gegner
	 * ermöglichen. Dies sind beispielsweise Moves, die einen Fisch des Gegners
	 * auffressen und somit einen zusammenhängenden Schwarm des Gegners
	 * zurücklassen, was uns sofort verlieren lassen würde.
	 * 
	 * @param gs    Der aktuelle GameState
	 * @param moves Eine Liste an Moves, die zu überprüfen sind
	 * @return eine Liste an übrigen Moves.
	 */
	ArrayList<Move> getValidMoves(GameState gs, ArrayList<Move> moves) {
		ArrayList<Move> tmp = new ArrayList<Move>();
		GameState gsCopy;
		PlayerColor opposite = gs.getOtherPlayerColor();
		for (Move m : moves) {
			try {
				gsCopy = gs.clone();
				m.perform(gsCopy);
				if (!isWinning(gsCopy, opposite))
					tmp.add(m);
			} catch (InvalidMoveException | InvalidGameStateException e) {
				e.printStackTrace();
			}
		}

		return tmp;
	}

	/**
	 * Diese Funktion probiert jeden Move der übergebenen Moves auf dem übergebenen
	 * GameState aus und gibt den Move zurück, der die meisten Punkt für die
	 * angegebenen PlayerColor ermöglicht.
	 * 
	 * @param gs    GameState, auf dem die Moves getestet werden sollen
	 * @param moves ArrayList der Moves, die getestet werden sollen.
	 * @param pc    PlayerColor des Spielers, für den der beste Move herausgefunden
	 *              werden soll.
	 * @return Der Move, der die meisten Punkte für die PlayerColor ermöglicht.
	 */
	Move findBestMove(GameState gs, ArrayList<Move> moves, PlayerColor pc) {
		int points = -1;
		int index = 0;
		for (int i = 0; i < moves.size(); i++) {
			try {
				GameState copy = gs.clone();
				Move m = moves.get(i);
				m.perform(copy);
				int ps = copy.getPointsForPlayer(pc);
				if (ps > points) {
					points = ps;
					index = i;
				}
			} catch (InvalidGameStateException | InvalidMoveException e) {
				e.printStackTrace();
			}
		}

		return moves.get(index);
	}

	/**
	 * Zählt die Anzahl aller Fische eines Spielers in einem bestimmten GameState
	 * 
	 * @param gameState   Das GameState, in dem die Fische gezählt werden sollen.
	 * @param playercolor Die Farbe des Spielers, dessen Fische gezählt werden
	 *                    sollen.
	 * @return Die Anzahl der Fische der PlayerColor in dem gegebenen GameState
	 */
	int countFishsOfPlayer(GameState gameState, PlayerColor playercolor) {
		int counter = 0;
		GameState copy = gameState.clone();
		for (int i = 0; i < 10; i++) {
			for (int j = 0; j < 10; j++) {
				if (copy.getField(i, j).getPiranha().isPresent()
						&& copy.getField(i, j).getPiranha().get() == playercolor)
					counter++;
			}
		}
		return counter;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onUpdate(Player player, Player otherPlayer) {
		currentPlayer = player;
		log.info("Spielerwechsel: " + player.getColor());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onUpdate(GameState gameState) {
		this.gameState = gameState;
		currentPlayer = gameState.getCurrentPlayer();
		log.info("Zug: {} Spieler: {}", gameState.getTurn(), currentPlayer.getColor());
	}

	/**
	 * Diese Methode sendet den übergebenen Move. Dabei wird nur beim ersten
	 * aufrufen der Funktion auch wirklich ein Move gesendet, andernfalls wird kein
	 * Move mehr gesendet. Dies liegt darin begründet, dass pro Request nur ein Move
	 * versendet werden darf. Werden mehrere Moves pro Request versendet, so hat man
	 * verloren, da man nicht am Zug ist. Dies gilt es hiermit zu vermeiden.
	 */
	@Override
	public void sendAction(Move move) {
		if (!alreadySend) {
			client.sendMove(move);
			alreadySend = true;
		}
	}

	/**
	 * Diese Klasse dient einem vorzeitigen Abbruch des Programms, falls aus
	 * irgendeinem unerfindlichen Grund in der maximal möglichen Zeit noch kein Move
	 * gesendet wurde.
	 * 
	 * @author JayPi4c
	 */
	class SafetyTimer extends Thread {
		@Override
		public void run() {
			try {
				Thread.sleep(800);
				sendAction(possibleMoves.get((int) (Math.random() * possibleMoves.size())));
				log.info(
						"Es wurde vorzeitig abgebrochen und ein zufälliger Move ausgewählt, da sonst die Zeit überschritten werden würde.");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

}

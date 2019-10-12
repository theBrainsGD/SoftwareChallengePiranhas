package sc.player2019.logic.Variation;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sc.framework.plugins.Player;
import sc.player2019.Starter;
import sc.plugin2019.Field;
import sc.plugin2019.GameState;
import sc.plugin2019.IGameHandler;
import sc.plugin2019.Move;
import sc.plugin2019.util.GameRuleLogic;
import sc.shared.GameResult;
import sc.shared.InvalidGameStateException;
import sc.shared.InvalidMoveException;
import sc.shared.PlayerColor;

/**
 * Das Herz des Clients: Eine sehr simple Logik, die ihre Zuege zufaellig
 * waehlt, aber gueltige Zuege macht. Ausserdem werden zum Spielverlauf
 * Konsolenausgaben gemacht.
 */
public class Logic_destructive implements IGameHandler {

	private Starter client;
	private GameState gameState;
	private Player currentPlayer;

	private static final Logger log = LoggerFactory.getLogger(Logic_destructive.class);

	/**
	 * Erzeugt ein neues Strategieobjekt, das zufaellige Zuege taetigt.
	 *
	 * @param client Der zugrundeliegende Client, der mit dem Spielserver
	 *               kommuniziert.
	 */
	public Logic_destructive(Starter client) {
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
		ArrayList<Move> possibleMoves = GameRuleLogic.getPossibleMoves(gameState);

		if (possibleMoves.size() == 1) {
			sendAction(possibleMoves.get(0));
			printEndTime(startTime);
			return;
		}
		// gucke ob der Gegner ein zusammenliegenden Schwarm hat:
		// Betrifft uns nur, wenn wir als zweiter Spieler dran sind (currentPlayerColor
		// == blue):
		if (gameState.getCurrentPlayerColor() == PlayerColor.BLUE) {

			// sammle die Informationen über die Schwärme des Gegners
			int biggestSwarm = gameState.getPointsForPlayer(PlayerColor.RED);
			int totalFishes = countFishsOfPlayer(gameState, PlayerColor.RED);
			log.info("other points: " + biggestSwarm + "; other fish count: " + totalFishes);
			if (biggestSwarm == totalFishes) {
				// Der Gegner hat nur einen Schwarm und würde gewinnen, wenn der Schwarm nicht
				// zerstört wird!
				log.info("Der Gegner hat einen zusammenhängenden Schwarm!");
				ArrayList<Field> fields = getDestroyFields(gameState, PlayerColor.RED);
				log.info("Es wurden " + fields.size() + " Felder gefunden, die den Schwarm wieder zerstören können");
				if (fields.size() == 0) {
					log.info("Das wars jetzt: Es gibt kein Feld, das den Schwarm trenne würde");
					sendAction(possibleMoves.get((int) (Math.random() * possibleMoves.size())));
					printEndTime(startTime);
					return;
				} else {
					// Suche nach einem Move, der auf das Field springt
					log.info("Es wurden " + fields.size() + " Felder gefunden, die den Schwarm zerstören können");
					// Für jedes Feld wird geguckt, ob es einen Move gibt, der dieses Feld
					// "angreift" und somit den Schwarm zerstört.
					for (Field f : fields) {
						for (Move m : possibleMoves) {
							if (m.x + m.direction.shift().getX() == f.getX()
									&& m.y + m.direction.shift().getY() == f.getY()) {
								printEndTime(startTime);
								sendAction(m);
								return;
							}
						}
					}
					log.info(
							"Es wurde kein Move gefunden, der den Sieg noch verhindern könnte. Es wird normal weitergemacht, auch wenn es jetzt nichts mehr bringt. ;(");

				}
			}
		}
		// Wenn wir bis hier hin kommen, dann ist der Gegner aktuell nicht am gewinnen,
		// also versuchen wir, unsere Chance zu erhöhen:
		Move m;
		do {
			m = findBestMove(gameState, possibleMoves, currentPlayer.getColor());
			// Teste, ob der Move dem Gegner einen Sieg beschert:
			try {
				GameState copy = gameState.clone();
				m.perform(copy);
				// Wenn der Move nicht zu einem Sieg des Gegners führt, dann kann der Move
				// verwendet werden und wir brechen aus der do-while-Schleife aus
				if (copy.getPointsForPlayer(gameState.getOtherPlayerColor()) != countFishsOfPlayer(copy,
						gameState.getOtherPlayerColor()))
					break;
				// sonst entferne den Move aus den möglichen Moves und beginne den ganzen Spaß
				// von vorne
				log.info("Dieser Move hätte den Gegner gewinnen lassen und wird deshalb nicht verwendet!");
				possibleMoves.remove(m);
			} catch (InvalidMoveException | InvalidGameStateException e) {
				e.printStackTrace();
			}
		} while (possibleMoves.size() > 0);

		sendAction(m);
		printEndTime(startTime);
	}

	void printEndTime(long startTime) {
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;
		System.out.println("Das Programm hat " + (double) duration / 1000.0 + " Sekunden (" + duration
				+ " Millisekunden) gedauert!");
	}

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
	 * Diese Funktion sucht die Felder des Schwarmes der Übergebenen
	 * {@link PlayerColor}, die, wenn sie aufgefressen werden wieder den Schwarm
	 * trennen. <br>
	 * Diese Funktion sollte nur aufgerufen werden, wenn die übergebene PlayerColor
	 * einen zusammenhängenden Schwarm hat.
	 * 
	 * @return Die Felder, die den Schwarm wieder trennen würden.
	 */
	ArrayList<Field> getDestroyFields(GameState gameState, PlayerColor playercolor) {
		GameState copy = gameState.clone();
		ArrayList<Field> fields = new ArrayList<Field>();
		for (int i = 0; i < 10; i++) {
			for (int j = 0; j < 10; j++) {
				// Da der Optionalwert evtl nicht besetzt ist, muss erst geprüft werden, ob der
				// Wert present ist
				if (copy.getField(i, j).getPiranha().isPresent()
						&& copy.getField(i, j).getPiranha().get() == playercolor) {
					log.info(i + " | " + j + " ist ein Piranha!");
					// Wenn das Feld ein Piranha ist, der die selbe Farbe hat, wie die Playercolor,
					// gucke, ob es anliegende Felder gibt, die diesen Kontakt überflüssig machen.
					ArrayList<Field> neighbors = new ArrayList<Field>();
					for (int k = Math.max(i - 1, 0); k < Math.min(10, i + 1); k++) {
						for (int l = Math.max(j - 1, 0); l < Math.min(10, j + 1); l++) {
							// Wenn das Feld mit dem aktuellen Feld übereinstimmt, überspringe dieses Feld
							if (i == k && j == l) {
								log.info("continue");
								continue;
							}
							// Wenn das Nachbarfeld mit der playercolor übereinstimmt, dann zähle den
							// Counter hoch
							if (copy.getField(k, l).getPiranha().isPresent()
									&& copy.getField(k, l).getPiranha().get() == playercolor) {
								neighbors.add(copy.getField(k, l));
								log.info("Ein Nachbar wurde gefunden!");
							}
						}
					}
					// Wenn das Feld zwei Nachbarfelder hat, die an dieses Feld angrenzen, dann
					// prüfe, ob diese Felder sich berühren oder ob das zerstören des Feldes den
					// Schwarm sprengen würde:
					// überprüfe, ob jeder der Nachbarn in der Liste der Nachbarn einen Nachbar hat
					if (!doAllHaveNeighbors(neighbors)) {
						log.info("Alle haben Nachbarn!");
						fields.add(copy.getField(i, j));
					}
				}
			}
		}
		return fields;
	}

	boolean doAllHaveNeighbors(ArrayList<Field> fields) {
		boolean answer;
		for (Field f1 : fields) {
			answer = false;
			for (Field f2 : fields) {
				if (f1 == f2)
					continue;
				if (Math.abs(f1.getX() - f2.getX()) <= 1 && Math.abs(f1.getY() - f2.getY()) <= 1)
					answer = true;
			}
			if (!answer)
				return answer;
		}
		return false;
	}

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
	 * {@inheritDoc}
	 */
	@Override
	public void sendAction(Move move) {
		client.sendMove(move);
	}

}

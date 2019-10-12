package sc.player2019.logic.Variation;

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
 * Das Herz des Clients: Eine sehr simple Logik, die ihre Zuege zufaellig
 * waehlt, aber gueltige Zuege macht. Ausserdem werden zum Spielverlauf
 * Konsolenausgaben gemacht.
 */
public class Logic_Heuristic implements IGameHandler {

	private Starter client;
	private GameState gameState;
	private Player currentPlayer;

	private static final Logger log = LoggerFactory.getLogger(Logic_Heuristic.class);

	/**
	 * Erzeugt ein neues Strategieobjekt, das zufaellige Zuege taetigt.
	 *
	 * @param client Der zugrundeliegende Client, der mit dem Spielserver
	 *               kommuniziert.
	 */
	public Logic_Heuristic(Starter client) {
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

		// Wenn es nur einen Move gibt, dann mache keine weiteren Berechnungen!
		if (possibleMoves.size() == 1) {
			sendAction(possibleMoves.get(0));
			return;
		}

		// Wenn der Gegner mit dem nächsten Move gewinnen kann, dann suche einen Move,
		// der den Sieg unterbinden kann

		// TODO: Sieg des Gengners herausfinden!

		sendAction(test());

		// sendAction(possibleMoves.get((int) (Math.random() * possibleMoves.size())));
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;
		System.out.println("Das Programm hat " + (double) duration / 1000.0 + " Sekunden (" + duration
				+ " Millisekunden) gedauert!");

	}

	Move test() {
		ArrayList<Move> possibleMoves = GameRuleLogic.getPossibleMoves(gameState);

		System.out.println(possibleMoves.size() + " Moves wurden ermittelt");
		MoveObject moveObjects[] = new MoveObject[possibleMoves.size()];
		for (int i = 0; i < moveObjects.length; i++) {
			System.out.println("erstelle moveObject #" + i);
			moveObjects[i] = new MoveObject(possibleMoves.get(i), gameState);
			System.out.println("berechne Punkte fuer moveObject #" + i);
			moveObjects[i].calcPoints(4);
		}
		System.out.println("moveObjects wurden erstellt");
		int index = 0;
		int best = 0;
		System.out.println("muss nur noch " + moveObjects.length + " Punkte überprüfen");
		for (int i = 0; i < moveObjects.length; i++) {
			int p = moveObjects[i].getPoints();

			if (best < p) {
				best = p;
				index = i;
			}

		}

		return moveObjects[index].getMove();
	}

	class MoveObject {
		Move m;
		int points;
		GameState gs;

		public MoveObject(Move m, GameState gs) {
			this.m = m;
			this.gs = gs.clone();
			points = 0;
		}

		Move getMove() {
			return m;
		}

		void setPoints(int points) {
			this.points = points;
		}

		int getPoints() {
			return this.points;
		}

		void calcPoints(int depth) {

			if (depth == 0) {
				points = gs.getPointsForPlayer(gs.getCurrentPlayerColor());
				return;
			}
			System.out.println("Tiefe: " + depth);
			GameState copy = gs.clone();
			try {
				m.perform(copy);
				ArrayList<Move> possibleMoves = GameRuleLogic.getPossibleMoves(copy);

				MoveObject moveObjects[];
				if (copy.getCurrentPlayerColor() != currentPlayer.getColor()) {
					int best = 0;
					int bestIndex = 0;
					for (int i = 0; i < possibleMoves.size(); i++) {
						GameState clone = copy.clone();
						possibleMoves.get(i).perform(clone);
						int points = clone.getPointsForPlayer(clone.getCurrentPlayerColor());
						if (points > best) {
							best = points;
							bestIndex = i;
						}
					}
					moveObjects = new MoveObject[1];
					moveObjects[0] = new MoveObject(possibleMoves.get(bestIndex), copy);
					moveObjects[0].calcPoints(depth - 1);

				} else {

					moveObjects = new MoveObject[possibleMoves.size()];
					for (int i = 0; i < moveObjects.length; i++) {
						moveObjects[i] = new MoveObject(possibleMoves.get(i), copy);
						moveObjects[i].calcPoints(depth - 1);
					}
				}
				int best = 0;
				for (int i = 0; i < moveObjects.length; i++) {
					int p = moveObjects[i].getPoints();
					if (best < p) {
						best = p;
					}

				}

				this.setPoints(best);

			} catch (InvalidGameStateException | InvalidMoveException e) {
				e.printStackTrace();
			}
		}

	}

	/*
	 * 
	 * // für den gegnerischen Move muss immer der Move gewählt werden, der die
	 * meisten // Punkte für den Gegner einbringt. Der eigene Move ist der, der am
	 * ende die // meisten Punkte hat: Es kann ja sein, dass ein "misserabler" Move
	 * im ersten // Zug einen Winning zug im zweiten Zug ermöglicht! Move
	 * getMove(GameState gs, int depth) throws InvalidGameStateException,
	 * InvalidMoveException { ArrayList<Move> moves =
	 * GameRuleLogic.getPossibleMoves(gs); int points = 0; Move BestMove = null; for
	 * (Move m : moves) { GameState copy = gs.clone(); m.perform(copy); int result =
	 * getPoints(copy, depth); if (result > points) { points = result; BestMove = m;
	 * } } return BestMove; }
	 * 
	 */

	/*
	 * int getPoints(GameState state, int depth) { if(state.getCurrentPlayerColor()
	 * == currentPlayer.getColor()) { // Das wäre mein Zug
	 * 
	 * } else { // jetzt ist der Gegner am Zug: Für den Gegner müssen wir immer Das
	 * Maximum Berechnen:
	 * 
	 * } }
	 */

	/**
	 * Gibt den Move zur&uumlck, der die meisten Punkte erm&oumlglicht.
	 * 
	 * @param gs    Das GameState, auf das ein Move angewendet werden kann.
	 * @param moves Eine ArrayList, welche alle Moves beinhaltet, die ausgetestet
	 *              werden sollen.
	 * @param color Die Farbe des Spielers, f&uumlr den der beste Move zu dem
	 *              GameState ermittelt werden soll
	 * @return Wenn es einen Besten Move gibt, den Move, sonst null
	 */
	public Move getBestMove(GameState gs, ArrayList<Move> moves, PlayerColor color) {
		int best = 0;
		int index = -1;
		for (int i = 0; i < moves.size(); i++) {
			GameState copy = gs.clone();
			try {
				moves.get(i).perform(copy);
				int points = copy.getPointsForPlayer(color);
				if (points > best) {
					best = points;
					index = i;
				}
			} catch (InvalidGameStateException | InvalidMoveException e) {
				e.printStackTrace();
			}
		}

		return index == -1 ? null : moves.get(index);
	}

	/**
	 * Gibt den besten Move zu einem GameState zur&uumlck.
	 * 
	 * @param gs    Der GameState
	 * @param color Die Farbe des Spielers, f&uumlr den der beste Move zu dem
	 *              GameState ermittelt werden soll
	 * 
	 * @return Wenn es einen Besten Move gibt, den Move, sonst null
	 */
	public Move getBestMove(GameState gs, PlayerColor color) {
		ArrayList<Move> moves = GameRuleLogic.getPossibleMoves(gs);
		int best = 0;
		int index = -1;
		for (int i = 0; i < moves.size(); i++) {
			GameState copy = gs.clone();
			try {
				moves.get(i).perform(copy);
				int points = copy.getPointsForPlayer(color);
				if (points > best) {
					best = points;
					index = i;
				}
			} catch (InvalidGameStateException | InvalidMoveException e) {
				e.printStackTrace();
			}
		}

		return index == -1 ? null : moves.get(index);
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

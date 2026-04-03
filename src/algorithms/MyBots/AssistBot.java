package algorithms.MyBots;

import java.util.ArrayList;

import characteristics.IRadarResult;
import characteristics.Parameters;

public class AssistBot extends BaseBot {

	private double rendezvousY;
	private boolean hasNearbyShooter = false;
	private boolean isFirstTurn = true;

	public AssistBot() {
		super();
	}

	@Override
	public void activate() {
		isTeamA = (getHeading() == Parameters.EAST);

		botId = NBOT;
		for (IRadarResult o : detectRadar()) {
			if (isSameDirection(o.getObjectDirection(), Parameters.NORTH))
				botId = SBOT;
		}

		if (botId.equals(NBOT)) {
			position = new Position(
					isTeamA ? Parameters.teamASecondaryBot1InitX : Parameters.teamBSecondaryBot1InitX,
					isTeamA ? Parameters.teamASecondaryBot1InitY : Parameters.teamBSecondaryBot1InitY);
		} else {
			position = new Position(
					isTeamA ? Parameters.teamASecondaryBot2InitX : Parameters.teamBSecondaryBot2InitX,
					isTeamA ? Parameters.teamASecondaryBot2InitY : Parameters.teamBSecondaryBot2InitY);
		}

		isApproachingRdv = true;
		state = State.FIRST_RDV;
		avoidanceBaseAngle = getNormalizedHeading();
		rendezvousY = botId.equals(SBOT) ? 1650 : 750;
	}

	@Override
	public void step() {
		scanRadar();
		readMessages();

		logDebugState();

		if (isApproachingRdv) {
			approachRdv();
			return;
		}

		hasNearbyShooter = isShooterNearby();

		if (getHealth() <= 0) {
			state = State.DEAD;
			allies.put(botId, new BotState(position.getX(), position.getY(), false));
			broadcast("DEAD " + botId);
			return;
		}

		if (evadeIncomingBullets())
			return;

		if (isFrozen || !hasNearbyShooter)
			return;

		try {
			switch (state) {
				case MOVING:
					tryMove(hasNearbyShooter);
					break;
				case MOVING_BACK:
					if (!hasReachedTarget(avoidTargetX, avoidTargetY, false)) {
						tryMove(false);
					} else {
						startAvoidance();
					}
					break;
				case TURNING_LEFT:
					turnLeft();
					break;
				case TURNING_RIGHT:
					turnRight();
					break;
				default:
					break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Moves the bot to its rendezvous position:
	 * NBOT goes north to rendezvousY then turns east/west.
	 * SBOT goes south to rendezvousY then turns east/west.
	 */
	private void approachRdv() {
		boolean isNBot = botId.equals(NBOT);
		double targetHeading = isNBot ? Parameters.NORTH : Parameters.SOUTH;

		// Step 1: turn to face vertical direction
		if (!isSameDirection(getHeading(), targetHeading) && isFirstTurn) {
			boolean turnLeft = isNBot ? isTeamA : !isTeamA;
			stepTurn(turnLeft ? Parameters.Direction.LEFT : Parameters.Direction.RIGHT);
			return;
		}

		// Step 2: move vertically to rendezvousY
		boolean needsToMove = isNBot ? (position.getY() > rendezvousY) : (position.getY() < rendezvousY);
		if (needsToMove) {
			isFirstTurn = false;
			tryMove(true);
			return;
		}

		// Step 3: turn to face east/west
		double finalHeading = isTeamA ? Parameters.EAST : Parameters.WEST;
		if (!isSameDirection(getHeading(), finalHeading)) {
			boolean turnRight = isNBot ? isTeamA : !isTeamA;
			stepTurn(turnRight ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
			return;
		}

		isApproachingRdv = false;
		state = State.MOVING;
	}

	@Override
	protected void scanRadar() {
		isFrozen = false;
		ArrayList<double[]> rawBullets = new ArrayList<>();

		for (IRadarResult o : detectRadar()) {
			double oX = position.getX() + o.getObjectDistance() * Math.cos(o.getObjectDirection());
			double oY = position.getY() + o.getObjectDistance() * Math.sin(o.getObjectDirection());

			if (o.getObjectType() == IRadarResult.Types.BULLET) {
				rawBullets.add(new double[] { oX, oY });
				continue;
			}

			if (allies.get(botId).isAlive()) {
				double checkHeading = (state == State.MOVING_BACK)
						? normalize(getHeading() + Math.PI)
						: getHeading();
				for (Position p : getObstacleCorners(o, position.getX(), position.getY())) {
					if (isPointInTrajectory(position.getX(), position.getY(), checkHeading, p.getX(), p.getY())) {
						isFrozen = true;
						startAvoidance();
					}
				}
			}

			switch (o.getObjectType()) {
				case OpponentMainBot:
				case OpponentSecondaryBot:
					broadcast("ENEMY " + o.getObjectDirection() + " " + o.getObjectDistance()
							+ " " + o.getObjectType() + " " + oX + " " + oY);
					if (o.getObjectDistance() < BOT_RADIUS * 4) {
						broadcast("MOVING_BACK " + botId + " " + oX + " " + oY);
						isFrozen = true;
					}
					break;
				case Wreck:
					broadcast("WRECK " + oX + " " + oY);
					break;
				default:
					break;
			}
		}
		updateTrackedBullets(rawBullets);
	}

	@Override
	protected void logDebugState() {
		String extra = "";
		if (isApproachingRdv) extra += " [rdv]";
		if (isFrozen) extra += " [frozen]";
		if (hasNearbyShooter) extra += " [shooter]";
		sendLogMessage("[" + botId + "] (" + (int) position.getX() + "," + (int) position.getY()
				+ ") " + (int) (getNormalizedHeading() * 180 / Math.PI) + "° | " + state + extra);
	}

	private void readMessages() {
		ArrayList<String> messages = fetchAllMessages();
		for (String msg : messages) {
			String[] parts = msg.split(" ");
			switch (parts[0]) {
				case "POS":
					updateAllyPosition(parts);
					break;
				case "DEAD":
					updateAllyDead(parts);
					break;
			}
		}
	}
}

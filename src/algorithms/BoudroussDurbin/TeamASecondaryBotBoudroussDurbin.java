package algorithms.BoudroussDurbin;

import characteristics.IRadarResult;
import characteristics.Parameters;

import java.util.ArrayList;
import java.util.Random;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import characteristics.IRadarResult.Types;
import robotsimulator.Brain;
public class TeamASecondaryBotBoudroussDurbin extends BaseBotBoudroussDurbin {

	private static final double MAX_MOVE_DISTANCE = 1500.0; // half of screen width
	private final Random random = new Random();
	private double distanceSinceLastTurn = 0.0;

	private double rendezvousY;
	private boolean hasNearbyShooter = false;
	private boolean isFirstTurn = true;

	@Override
	protected void startAvoidance() {
		// Random angle between 0.2π and 0.8π for varied obstacle avoidance
		avoidanceFraction = 0.2 + random.nextDouble() * 0.6;
		distanceSinceLastTurn = 0;
		super.startAvoidance();
	}

	public TeamASecondaryBotBoudroussDurbin() {
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
			positionDurbin = new PositionDurbin(
					isTeamA ? Parameters.teamASecondaryBot1InitX : Parameters.teamBSecondaryBot1InitX,
					isTeamA ? Parameters.teamASecondaryBot1InitY : Parameters.teamBSecondaryBot1InitY);
		} else {
			positionDurbin = new PositionDurbin(
					isTeamA ? Parameters.teamASecondaryBot2InitX : Parameters.teamBSecondaryBot2InitX,
					isTeamA ? Parameters.teamASecondaryBot2InitY : Parameters.teamBSecondaryBot2InitY);
		}

		isApproachingRdv = true;
		state = State.FIRST_RDV;
		avoidanceBaseAngle = getNormalizedHeading();
		rendezvousY = botId.equals(SBOT) ? 1250 : 780;
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
			allies.put(botId, new algorithms.BoudroussDurbin.BotStateDurbin(positionDurbin.getX(), positionDurbin.getY(), false));
			broadcast("DEAD " + botId);
			return;
		}

		if (evadeIncomingBullets())
			return;

		if (isFrozen || !hasNearbyShooter)
			return;

		if (state == State.MOVING) {
			distanceSinceLastTurn += getBotSpeed();
			if (distanceSinceLastTurn >= MAX_MOVE_DISTANCE) {
				distanceSinceLastTurn = 0;
				startAvoidance();
				return;
			}
		}

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
		boolean needsToMove = isNBot ? (positionDurbin.getY() > rendezvousY) : (positionDurbin.getY() < rendezvousY);
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
			double oX = positionDurbin.getX() + o.getObjectDistance() * Math.cos(o.getObjectDirection());
			double oY = positionDurbin.getY() + o.getObjectDistance() * Math.sin(o.getObjectDirection());

			if (o.getObjectType() == IRadarResult.Types.BULLET) {
				rawBullets.add(new double[] { oX, oY });
				continue;
			}

			if (allies.get(botId).isAlive()) {
				double checkHeading = (state == State.MOVING_BACK)
						? normalize(getHeading() + Math.PI)
						: getHeading();
				for (PositionDurbin p : getObstacleCorners(o, positionDurbin.getX(), positionDurbin.getY())) {
					if (isPointInTrajectory(positionDurbin.getX(), positionDurbin.getY(), checkHeading, p.getX(), p.getY())) {
						isFrozen = true;
						startAvoidance();
					}
				}
			}

			switch (o.getObjectType()) {
				case OpponentMainBot:
				case OpponentSecondaryBot:
					distanceSinceLastTurn = 0;
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
		sendLogMessage("[" + botId + "] (" + (int) positionDurbin.getX() + "," + (int) positionDurbin.getY()
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

class BulletDurbin {
    double x, y;
    double vx, vy;
    boolean hasVelocity;

    BulletDurbin(double x, double y) {
        this.x = x;
        this.y = y;
        this.hasVelocity = false;
    }
}

class BotStateDurbin {
    private PositionDurbin positionDurbin = new PositionDurbin(0, 0);
    private boolean isAlive = true;

    public BotStateDurbin() {
    }

    public BotStateDurbin(double x, double y, boolean alive) {
        positionDurbin.setX(x);
        positionDurbin.setY(y);
        isAlive = alive;
    }

    public void setPosition(double x, double y) {
        positionDurbin.setX(x);
        positionDurbin.setY(y);
    }

    public PositionDurbin getPosition() {
        return positionDurbin;
    }

    public void setAlive(boolean alive) {
        isAlive = alive;
    }

    public boolean isAlive() {
        return isAlive;
    }
}

class PositionDurbin {
    private double x;
    private double y;

    public PositionDurbin(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public String toString() {
        return "X : " + x + "; Y : " + y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PositionDurbin other = (PositionDurbin) obj;
        return Double.doubleToLongBits(x) == Double.doubleToLongBits(other.x)
                && Double.doubleToLongBits(y) == Double.doubleToLongBits(other.y);
    }
}

class EnemyDurbin {
    // xHistory/yHistory[0]=current, [1]=previous, [2]=prevPrevious
    double[] xHistory = new double[3];
    double[] yHistory = new double[3];
    double distance, direction;
    Types type;
    double speedX, speedY;
    boolean hasVelocityData;
    double predictedX, predictedY;

    public EnemyDurbin(double x, double y, double distance, double direction, Types type) {
        xHistory[0] = xHistory[1] = xHistory[2] = x;
        yHistory[0] = yHistory[1] = yHistory[2] = y;
        this.distance = distance;
        this.direction = direction;
        this.type = type;
        this.speedX = 0;
        this.speedY = 0;
        this.hasVelocityData = false;
        this.predictedX = x;
        this.predictedY = y;
    }

    public void updatePosition(double newX, double newY, double newDistance, double newDirection) {
        xHistory[2] = xHistory[1];
        yHistory[2] = yHistory[1];
        xHistory[1] = xHistory[0];
        yHistory[1] = yHistory[0];

        xHistory[0] = newX;
        yHistory[0] = newY;
        this.distance = newDistance;
        this.direction = newDirection;

        if (hasVelocityData) {
            this.speedX = xHistory[0] - xHistory[1];
            this.speedY = yHistory[0] - yHistory[1];
        } else if (xHistory[0] != xHistory[1] || yHistory[0] != yHistory[1]) {
            hasVelocityData = true;
        }
    }

    public void predictPosition(double bulletTravelTime) {
        if (!hasVelocityData) {
            this.predictedX = xHistory[0];
            this.predictedY = yHistory[0];
        } else {
            double prevDx = xHistory[1] - xHistory[2];
            double prevDy = yHistory[1] - yHistory[2];
            double currentDx = xHistory[0] - xHistory[1];
            double currentDy = yHistory[0] - yHistory[1];

            boolean isOscillatingX = (prevDx * currentDx < 0);
            boolean isOscillatingY = (prevDy * currentDy < 0);

            if (isOscillatingX || isOscillatingY) {
                this.predictedX = (xHistory[0] + xHistory[1]) / 2;
                this.predictedY = (yHistory[0] + yHistory[1]) / 2;
            } else {
                this.predictedX = xHistory[0] + speedX * bulletTravelTime;
                this.predictedY = yHistory[0] + speedY * bulletTravelTime;
            }
        }
    }

    public double getX() {
        return xHistory[0];
    }

    public double getY() {
        return yHistory[0];
    }

    public double getPredictedX() {
        return predictedX;
    }

    public double getPredictedY() {
        return predictedY;
    }
}



abstract class BaseBotBoudroussDurbin extends Brain {

    protected static final String NBOT = "s1";
    protected static final String SBOT = "s2";
    protected static final String MAIN1 = "1";
    protected static final String MAIN2 = "2";
    protected static final String MAIN3 = "3";

    protected static final double ANGLE_PRECISION = 0.001;
    protected static final double SCOUT_SHOOTER_DISTANCE = 1250;

    protected enum State {
        FIRST_RDV, MOVING, MOVING_BACK, TURNING_LEFT, TURNING_RIGHT, FIRE, DEAD
    };

    protected final double BOT_RADIUS = 50;

    protected String botId;

    protected PositionDurbin positionDurbin;
    protected boolean isFrozen;
    protected boolean isTeamA;
    protected boolean isApproachingRdv;
    protected State state;
    protected double avoidanceBaseAngle;
    protected double avoidTargetX, avoidTargetY;
    protected boolean isAvoiding;
    protected double avoidanceFraction = 0.5;
    protected double rdvXToReach, rdvYToReach;
    protected List<EnemyDurbin> enemies = new ArrayList<>();
    protected List<double[]> wrecks = new ArrayList<>();
    protected List<EnemyDurbin> positionsToAvoid = new ArrayList<>();
    protected List<BulletDurbin> trackedBullets = new ArrayList<>();

    protected Map<String, BotStateDurbin> allies = new HashMap<>();

    public BaseBotBoudroussDurbin() {
        super();
        allies.put(NBOT, new BotStateDurbin());
        allies.put(SBOT, new BotStateDurbin());
        allies.put(MAIN1, new BotStateDurbin());
        allies.put(MAIN2, new BotStateDurbin());
        allies.put(MAIN3, new BotStateDurbin());
    }

    protected abstract void scanRadar();

    // =========================================HELPERS=========================================

    protected boolean isScout() {
        return botId.equals(NBOT) || botId.equals(SBOT);
    }

    protected double getBotSpeed() {
        return isScout() ? Parameters.teamASecondaryBotSpeed : Parameters.teamAMainBotSpeed;
    }

    protected boolean isWithinBounds(double x, double y) {
        double margin = isScout() ? 150 : 100;
        return x > margin && x < (3000 - margin) && y > margin && y < (2000 - margin);
    }

    protected boolean isShooterNearby() {
        for (Map.Entry<String, BotStateDurbin> entry : allies.entrySet()) {
            if (entry.getValue().isAlive()
                    && distance(entry.getValue().getPosition(), positionDurbin) < SCOUT_SHOOTER_DISTANCE
                    && !entry.getKey().equals(NBOT)
                    && !entry.getKey().equals(SBOT)) {
                return true;
            }
        }
        return false;
    }

    protected void logDebugState() {
        sendLogMessage("[" + botId + "] (" + (int) positionDurbin.getX() + "," + (int) positionDurbin.getY()
                + ") " + (int) (getNormalizedHeading() * 180 / Math.PI) + "° | " + state);
    }

    /** Updates allies map from a parsed POS message: POS id x y heading */
    protected void updateAllyPosition(String[] parts) {
        double x = Double.parseDouble(parts[2]);
        double y = Double.parseDouble(parts[3]);
        BotStateDurbin bot = allies.get(parts[1]);
        if (bot == null) {
            allies.put(parts[1], new BotStateDurbin(x, y, true));
        } else {
            bot.setPosition(x, y);
        }
    }

    /** Marks an ally as dead from a parsed DEAD message: DEAD id */
    protected void updateAllyDead(String[] parts) {
        BotStateDurbin bot = allies.get(parts[1]);
        if (bot != null)
            bot.setAlive(false);
    }

    // =========================================NAVIGATION=========================================

    protected void moveToRdv(double tX, double tY) {
        double angleToTarget = Math.atan2(tY - positionDurbin.getY(), tX - positionDurbin.getX());
        double distanceToTarget = Math.sqrt(Math.pow(positionDurbin.getX() - tX, 2) + Math.pow(positionDurbin.getY() - tY, 2));

        if (isScout() || distanceToTarget > 450) {
            angleToTarget = getNearestAllowedDirection(angleToTarget);
            if (!isSameDirection(getHeading(), angleToTarget)) {
                turnTo(angleToTarget);
            } else {
                tryMove(true);
            }
        }
    }

    protected void turnTo(double targetAngle) {
        double diff = normalize(targetAngle - getHeading());
        if (diff > Math.PI)
            diff -= 2 * Math.PI;
        else if (diff < -Math.PI)
            diff += 2 * Math.PI;
        stepTurn(diff > ANGLE_PRECISION ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
    }

    protected void turnLeft() {
        if (!isSameDirection(getHeading(), avoidanceBaseAngle + (-avoidanceFraction * Math.PI))) {
            stepTurn(Parameters.Direction.LEFT);
        } else {
            state = State.MOVING;
            tryMove(true);
        }
    }

    protected void turnRight() {
        if (!isSameDirection(getHeading(), avoidanceBaseAngle + (avoidanceFraction * Math.PI))) {
            stepTurn(Parameters.Direction.RIGHT);
        } else {
            state = State.MOVING;
            tryMove(true);
        }
    }

    protected void tryMove(boolean forward) {
        double speed = getBotSpeed();
        double sign = forward ? 1 : -1;
        double predictedX = positionDurbin.getX() + sign * Math.cos(getHeading()) * speed;
        double predictedY = positionDurbin.getY() + sign * Math.sin(getHeading()) * speed;

        if (isWithinBounds(predictedX, predictedY)) {
            if (forward)
                move();
            else
                moveBack();
            positionDurbin.setX(predictedX);
            positionDurbin.setY(predictedY);
            sendMyPosition();
            return;
        }
        startAvoidance();
    }

    protected void startAvoidance() {
        isAvoiding = true;
        avoidanceBaseAngle = getNormalizedHeading();

        boolean obstacleInPathRight = false;
        boolean obstacleInPathLeft = false;

        for (IRadarResult o : detectRadar()) {
            if (!allies.get(botId).isAlive() || o.getObjectType() == Types.BULLET)
                continue;
            for (PositionDurbin p : getObstacleCorners(o, positionDurbin.getX(), positionDurbin.getY())) {
                if (!obstacleInPathRight) {
                    obstacleInPathRight = isPointInTrajectory(positionDurbin.getX(), positionDurbin.getY(),
                            getHeading() + avoidanceFraction * Math.PI, p.getX(), p.getY());
                }
                if (!obstacleInPathLeft && botId.equals(SBOT)) {
                    obstacleInPathLeft = isPointInTrajectory(positionDurbin.getX(), positionDurbin.getY(),
                            getHeading() - avoidanceFraction * Math.PI, p.getX(), p.getY());
                }
            }
        }

        if (!obstacleInPathRight) {
            state = State.TURNING_RIGHT;
            avoidTargetX = positionDurbin.getX() + Math.cos(getHeading() + avoidanceFraction * Math.PI) * 50;
            avoidTargetY = positionDurbin.getY() + Math.sin(getHeading() + avoidanceFraction * Math.PI) * 50;
        } else if (!obstacleInPathLeft) {
            state = State.TURNING_LEFT;
            avoidTargetX = positionDurbin.getX() + Math.cos(getHeading() - avoidanceFraction * Math.PI) * 50;
            avoidTargetY = positionDurbin.getY() + Math.sin(getHeading() - avoidanceFraction * Math.PI) * 50;
        } else {
            state = State.MOVING_BACK;
            avoidTargetX = positionDurbin.getX() - Math.cos(getHeading()) * 50;
            avoidTargetY = positionDurbin.getY() - Math.sin(getHeading()) * 50;
        }
    }

    protected boolean hasReachedTarget(double targetX, double targetY, boolean movingForward) {
        double sign = movingForward ? 1 : -1;
        boolean reachedX = reachedComponent(positionDurbin.getX(), targetX, Math.cos(getHeading()) * sign);
        boolean reachedY = reachedComponent(positionDurbin.getY(), targetY, Math.sin(getHeading()) * sign);
        return reachedX && reachedY;
    }

    private boolean reachedComponent(double current, double target, double component) {
        if (component > 0)
            return current >= target;
        if (component < 0)
            return current <= target;
        return true;
    }

    // =========================================MATH=========================================

    protected boolean isSameDirection(double dir1, double dir2) {
        double diff = Math.abs(normalize(dir1) - normalize(dir2));
        return diff < ANGLE_PRECISION || Math.abs(diff - 2 * Math.PI) < ANGLE_PRECISION;
    }

    protected boolean isApproximatelySameDirection(double dir1, double dir2) {
        double diff = Math.abs(normalize(dir1) - normalize(dir2));
        return diff < 0.5;
    }

    protected double normalize(double dir) {
        double res = dir;
        while (res < 0)
            res += 2 * Math.PI;
        while (res >= 2 * Math.PI)
            res -= 2 * Math.PI;
        return res;
    }

    protected double getNormalizedHeading() {
        return normalize(getHeading());
    }

    protected void sendMyPosition() {
        broadcast("POS " + botId + " " + positionDurbin.getX() + " " + positionDurbin.getY() + " " + getHeading());
    }

    protected double distance(PositionDurbin p1, PositionDurbin p2) {
        return Math.sqrt(Math.pow(p2.getX() - p1.getX(), 2) + Math.pow(p2.getY() - p1.getY(), 2));
    }

    /** Returns the nearest cardinal direction (N/S/E/W) to the given angle. */
    protected double getNearestAllowedDirection(double angle) {
        double[] allowedAngles = { 0, Math.PI / 2, Math.PI, -Math.PI / 2 };
        double bestAngle = allowedAngles[0];
        double minDiff = Math.abs(normalize(angle) - normalize(bestAngle));
        for (double allowed : allowedAngles) {
            double diff = Math.abs(normalize(angle) - normalize(allowed));
            if (diff < minDiff) {
                bestAngle = allowed;
                minDiff = diff;
            }
        }
        return bestAngle;
    }

    protected boolean isPointInTrajectory(double robotX, double robotY, double robotHeading,
            double pointX, double pointY) {
        double pathLength = BOT_RADIUS * 2;
        double halfWidth = BOT_RADIUS;
        double dirX = Math.cos(robotHeading);
        double dirY = Math.sin(robotHeading);
        double perpX = -dirY;
        double perpY = dirX;

        double frontX = robotX + pathLength * dirX;
        double frontY = robotY + pathLength * dirY;

        double ALX = robotX + halfWidth * perpX, ALY = robotY + halfWidth * perpY;
        double ARX = robotX - halfWidth * perpX, ARY = robotY - halfWidth * perpY;
        double PLX = frontX + halfWidth * perpX, PLY = frontY + halfWidth * perpY;
        double PRX = frontX - halfWidth * perpX, PRY = frontY - halfWidth * perpY;

        return isPointInRectangle(pointX, pointY, ALX, ALY, ARX, ARY, PLX, PLY, PRX, PRY);
    }

    protected boolean isPointInRectangle(double Px, double Py,
            double ALX, double ALY, double ARX, double ARY,
            double PLX, double PLY, double PRX, double PRY) {
        double APx = Px - ALX, APy = Py - ALY;
        double ABx = ARX - ALX, ABy = ARY - ALY;
        double ADx = PLX - ALX, ADy = PLY - ALY;
        double dotAB = APx * ABx + APy * ABy;
        double dotAD = APx * ADx + APy * ADy;
        double dotAB_AB = ABx * ABx + ABy * ABy;
        double dotAD_AD = ADx * ADx + ADy * ADy;
        return (0 <= dotAB && dotAB <= dotAB_AB) && (0 <= dotAD && dotAD <= dotAD_AD);
    }

    // =========================================BULLET EVASION=========================================

    protected void updateTrackedBullets(List<double[]> rawBullets) {
        List<BulletDurbin> updated = new ArrayList<>();
        for (double[] raw : rawBullets) {
            BulletDurbin match = null;
            double bestDist = 15.0;
            for (BulletDurbin prev : trackedBullets) {
                double predX = prev.hasVelocity ? prev.x + prev.vx : prev.x;
                double predY = prev.hasVelocity ? prev.y + prev.vy : prev.y;
                double d = Math.hypot(predX - raw[0], predY - raw[1]);
                if (d < bestDist) {
                    bestDist = d;
                    match = prev;
                }
            }
            BulletDurbin nb = new BulletDurbin(raw[0], raw[1]);
            if (match != null) {
                nb.vx = raw[0] - match.x;
                nb.vy = raw[1] - match.y;
                nb.hasVelocity = true;
            }
            updated.add(nb);
        }
        trackedBullets = updated;
    }

    protected boolean evadeIncomingBullets() {
        double danger = BOT_RADIUS + Parameters.bulletRadius + 10;
        for (BulletDurbin b : trackedBullets) {
            if (!b.hasVelocity)
                continue;
            if (isBulletThreat(b, positionDurbin.getX(), positionDurbin.getY(), danger)) {
                performDodge(b, danger);
                return true;
            }
        }
        return false;
    }

    private boolean isBulletThreat(BulletDurbin b, double px, double py, double danger) {
        double speed = Math.hypot(b.vx, b.vy);
        if (speed < 1.0)
            return false;
        double dirX = b.vx / speed, dirY = b.vy / speed;
        double dx = px - b.x, dy = py - b.y;
        double perp = Math.abs(dx * dirY - dy * dirX);
        if (perp >= danger)
            return false;
        return (dx * dirX + dy * dirY) > 0;
    }

    private void performDodge(BulletDurbin b, double danger) {
        double speed = getBotSpeed();
        double heading = getHeading();
        double lookahead = speed * 5;

        double fx = positionDurbin.getX() + Math.cos(heading) * lookahead;
        double fy = positionDurbin.getY() + Math.sin(heading) * lookahead;
        if (!isBulletThreat(b, fx, fy, danger)) {
            tryMove(true);
            return;
        }

        double bx = positionDurbin.getX() - Math.cos(heading) * lookahead;
        double by = positionDurbin.getY() - Math.sin(heading) * lookahead;
        if (!isBulletThreat(b, bx, by, danger)) {
            tryMove(false);
            return;
        }
        tryMove(false);
    }

    protected PositionDurbin[] getObstacleCorners(IRadarResult obstacle, double robotX, double robotY) {
        double oX = robotX + obstacle.getObjectDistance() * Math.cos(obstacle.getObjectDirection());
        double oY = robotY + obstacle.getObjectDistance() * Math.sin(obstacle.getObjectDirection());
        double r = obstacle.getObjectRadius();
        return new PositionDurbin[] {
                new PositionDurbin(oX - r, oY + r),
                new PositionDurbin(oX + r, oY + r),
                new PositionDurbin(oX - r, oY - r),
                new PositionDurbin(oX + r, oY - r)
        };
    }
}
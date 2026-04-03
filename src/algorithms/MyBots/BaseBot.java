package algorithms.MyBots;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import characteristics.IRadarResult;
import characteristics.IRadarResult.Types;
import characteristics.Parameters;
import robotsimulator.Brain;

abstract class BaseBot extends Brain {

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

    protected Position position;
    protected boolean isFrozen;
    protected boolean isTeamA;
    protected boolean isApproachingRdv;
    protected State state;
    protected double avoidanceBaseAngle;
    protected double avoidTargetX, avoidTargetY;
    protected boolean isAvoiding;
    protected double avoidanceFraction = 0.5;
    protected double rdvXToReach, rdvYToReach;
    protected List<Enemy> enemies = new ArrayList<>();
    protected List<double[]> wrecks = new ArrayList<>();
    protected List<Enemy> positionsToAvoid = new ArrayList<>();
    protected List<Bullet> trackedBullets = new ArrayList<>();

    protected Map<String, BotState> allies = new HashMap<>();

    public BaseBot() {
        super();
        allies.put(NBOT, new BotState());
        allies.put(SBOT, new BotState());
        allies.put(MAIN1, new BotState());
        allies.put(MAIN2, new BotState());
        allies.put(MAIN3, new BotState());
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
        for (Map.Entry<String, BotState> entry : allies.entrySet()) {
            if (entry.getValue().isAlive()
                    && distance(entry.getValue().getPosition(), position) < SCOUT_SHOOTER_DISTANCE
                    && !entry.getKey().equals(NBOT)
                    && !entry.getKey().equals(SBOT)) {
                return true;
            }
        }
        return false;
    }

    protected void logDebugState() {
        sendLogMessage("[" + botId + "] (" + (int) position.getX() + "," + (int) position.getY()
                + ") " + (int) (getNormalizedHeading() * 180 / Math.PI) + "° | " + state);
    }

    /** Updates allies map from a parsed POS message: POS id x y heading */
    protected void updateAllyPosition(String[] parts) {
        double x = Double.parseDouble(parts[2]);
        double y = Double.parseDouble(parts[3]);
        BotState bot = allies.get(parts[1]);
        if (bot == null) {
            allies.put(parts[1], new BotState(x, y, true));
        } else {
            bot.setPosition(x, y);
        }
    }

    /** Marks an ally as dead from a parsed DEAD message: DEAD id */
    protected void updateAllyDead(String[] parts) {
        BotState bot = allies.get(parts[1]);
        if (bot != null)
            bot.setAlive(false);
    }

    // =========================================NAVIGATION=========================================

    protected void moveToRdv(double tX, double tY) {
        double angleToTarget = Math.atan2(tY - position.getY(), tX - position.getX());
        double distanceToTarget = Math.sqrt(Math.pow(position.getX() - tX, 2) + Math.pow(position.getY() - tY, 2));

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
        double predictedX = position.getX() + sign * Math.cos(getHeading()) * speed;
        double predictedY = position.getY() + sign * Math.sin(getHeading()) * speed;

        if (isWithinBounds(predictedX, predictedY)) {
            if (forward)
                move();
            else
                moveBack();
            position.setX(predictedX);
            position.setY(predictedY);
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
            for (Position p : getObstacleCorners(o, position.getX(), position.getY())) {
                if (!obstacleInPathRight) {
                    obstacleInPathRight = isPointInTrajectory(position.getX(), position.getY(),
                            getHeading() + avoidanceFraction * Math.PI, p.getX(), p.getY());
                }
                if (!obstacleInPathLeft && botId.equals(SBOT)) {
                    obstacleInPathLeft = isPointInTrajectory(position.getX(), position.getY(),
                            getHeading() - avoidanceFraction * Math.PI, p.getX(), p.getY());
                }
            }
        }

        if (!obstacleInPathRight) {
            state = State.TURNING_RIGHT;
            avoidTargetX = position.getX() + Math.cos(getHeading() + avoidanceFraction * Math.PI) * 50;
            avoidTargetY = position.getY() + Math.sin(getHeading() + avoidanceFraction * Math.PI) * 50;
        } else if (!obstacleInPathLeft) {
            state = State.TURNING_LEFT;
            avoidTargetX = position.getX() + Math.cos(getHeading() - avoidanceFraction * Math.PI) * 50;
            avoidTargetY = position.getY() + Math.sin(getHeading() - avoidanceFraction * Math.PI) * 50;
        } else {
            state = State.MOVING_BACK;
            avoidTargetX = position.getX() - Math.cos(getHeading()) * 50;
            avoidTargetY = position.getY() - Math.sin(getHeading()) * 50;
        }
    }

    protected boolean hasReachedTarget(double targetX, double targetY, boolean movingForward) {
        double sign = movingForward ? 1 : -1;
        boolean reachedX = reachedComponent(position.getX(), targetX, Math.cos(getHeading()) * sign);
        boolean reachedY = reachedComponent(position.getY(), targetY, Math.sin(getHeading()) * sign);
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
        broadcast("POS " + botId + " " + position.getX() + " " + position.getY() + " " + getHeading());
    }

    protected double distance(Position p1, Position p2) {
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

    /**
     * Matches radar-detected bullets (as absolute positions) to previously tracked bullets
     * using predicted positions, computing velocity for matched ones.
     */
    protected void updateTrackedBullets(List<double[]> rawBullets) {
        List<Bullet> updated = new ArrayList<>();
        for (double[] raw : rawBullets) {
            Bullet match = null;
            double bestDist = 15.0; // a bit over bulletVelocity=10 to tolerate rounding
            for (Bullet prev : trackedBullets) {
                double predX = prev.hasVelocity ? prev.x + prev.vx : prev.x;
                double predY = prev.hasVelocity ? prev.y + prev.vy : prev.y;
                double d = Math.hypot(predX - raw[0], predY - raw[1]);
                if (d < bestDist) {
                    bestDist = d;
                    match = prev;
                }
            }
            Bullet nb = new Bullet(raw[0], raw[1]);
            if (match != null) {
                nb.vx = raw[0] - match.x;
                nb.vy = raw[1] - match.y;
                nb.hasVelocity = true;
            }
            updated.add(nb);
        }
        trackedBullets = updated;
    }

    /**
     * Checks all tracked bullets for threats and attempts a dodge.
     * Returns true if a dodge action was taken (step consumed).
     */
    protected boolean evadeIncomingBullets() {
        double danger = BOT_RADIUS + Parameters.bulletRadius + 10;
        for (Bullet b : trackedBullets) {
            if (!b.hasVelocity)
                continue;
            if (isBulletThreat(b, position.getX(), position.getY(), danger)) {
                performDodge(b, danger);
                return true;
            }
        }
        return false;
    }

    /** True if the bullet's trajectory passes within {@code danger} of point (px, py). */
    private boolean isBulletThreat(Bullet b, double px, double py, double danger) {
        double speed = Math.hypot(b.vx, b.vy);
        if (speed < 1.0)
            return false;
        double dirX = b.vx / speed, dirY = b.vy / speed;
        double dx = px - b.x, dy = py - b.y;
        // Perpendicular distance from point to bullet's trajectory line
        double perp = Math.abs(dx * dirY - dy * dirX);
        if (perp >= danger)
            return false;
        // Ensure the bullet is moving toward the point, not away
        return (dx * dirX + dy * dirY) > 0;
    }

    /**
     * Attempts to dodge bullet {@code b} by moving forward or backward along the
     * current heading. Looks 5 steps ahead to pick the direction that gets us clear.
     */
    private void performDodge(Bullet b, double danger) {
        double speed = getBotSpeed();
        double heading = getHeading();
        double lookahead = speed * 5;

        double fx = position.getX() + Math.cos(heading) * lookahead;
        double fy = position.getY() + Math.sin(heading) * lookahead;
        if (!isBulletThreat(b, fx, fy, danger)) {
            tryMove(true);
            return;
        }

        double bx = position.getX() - Math.cos(heading) * lookahead;
        double by = position.getY() - Math.sin(heading) * lookahead;
        if (!isBulletThreat(b, bx, by, danger)) {
            tryMove(false);
            return;
        }
        tryMove(false);
    }

    protected Position[] getObstacleCorners(IRadarResult obstacle, double robotX, double robotY) {
        double oX = robotX + obstacle.getObjectDistance() * Math.cos(obstacle.getObjectDirection());
        double oY = robotY + obstacle.getObjectDistance() * Math.sin(obstacle.getObjectDirection());
        double r = obstacle.getObjectRadius();
        return new Position[] {
                new Position(oX - r, oY + r),
                new Position(oX + r, oY + r),
                new Position(oX - r, oY - r),
                new Position(oX + r, oY - r)
        };
    }
}

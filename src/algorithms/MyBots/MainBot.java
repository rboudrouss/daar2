package algorithms.MyBots;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import characteristics.IRadarResult;
import characteristics.IRadarResult.Types;
import characteristics.Parameters;

public class MainBot extends BaseBot {
    private static final double BOT_RADIUS = Math.max(Parameters.teamAMainBotRadius, Parameters.teamBMainBotRadius);
    private static final double BULLET_RADIUS = Parameters.bulletRadius;
    private static final double BOT_BULLET_RADIUS = BOT_RADIUS + BULLET_RADIUS;

    private Enemy lastFiredTarget = null;
    private double rendezvousX = 0.0;
    private double rendezvousY = 0.0;
    private boolean isManeuvering = false;
    private Enemy currentTarget;
    private int fireCount = 0;
    private static final int MAX_FIRE_COUNT = 100;

    public MainBot() {
        super();
    }

    @Override
    public void activate() {
        isTeamA = (getHeading() == Parameters.EAST);
        botId = determineIdentity();
        position = new Position(getInitialX(), getInitialY());
        state = State.FIRST_RDV;
        avoidanceBaseAngle = getNormalizedHeading();
    }

    private String determineIdentity() {
        boolean top = false, bottom = false;
        for (IRadarResult o : detectRadar()) {
            if (isSameDirection(o.getObjectDirection(), Parameters.NORTH))
                top = true;
            else if (isSameDirection(o.getObjectDirection(), Parameters.SOUTH))
                bottom = true;
        }
        if (top && bottom)
            return MAIN2;
        if (!top && bottom)
            return MAIN1;
        return MAIN3;
    }

    private double getInitialX() {
        switch (botId) {
            case MAIN1:
                return isTeamA ? Parameters.teamAMainBot1InitX : Parameters.teamBMainBot1InitX;
            case MAIN2:
                return isTeamA ? Parameters.teamAMainBot2InitX : Parameters.teamBMainBot2InitX;
            default:
                return isTeamA ? Parameters.teamAMainBot3InitX : Parameters.teamBMainBot3InitX;
        }
    }

    private double getInitialY() {
        switch (botId) {
            case MAIN1:
                return isTeamA ? Parameters.teamAMainBot1InitY : Parameters.teamBMainBot1InitY;
            case MAIN2:
                return isTeamA ? Parameters.teamAMainBot2InitY : Parameters.teamBMainBot2InitY;
            default:
                return isTeamA ? Parameters.teamAMainBot3InitY : Parameters.teamBMainBot3InitY;
        }
    }

    @Override
    public void step() {
        scanRadar();
        readMessages();

        if (getHealth() <= 0) {
            state = State.DEAD;
            allies.put(botId, new BotState(position.getX(), position.getY(), false));
            return;
        }

        if (evadeIncomingBullets())
            return;

        currentTarget = selectTarget();
        if (currentTarget != null) {
            state = State.FIRE;
            if (fireCount == 0)
                lastFiredTarget = currentTarget;
        }

        logDebugState();

        try {
            switch (state) {
                case FIRE:
                    executeFire(currentTarget);
                    break;
                case FIRST_RDV:
                    if (rendezvousX != 0.0 && rendezvousY != 0.0)
                        moveToRdv(rendezvousX, rendezvousY);
                    break;
                case MOVING:
                    boolean following = allies.get(SBOT).isAlive() || allies.get(NBOT).isAlive();
                    if (following) {
                        if (!isAvoiding) {
                            moveToRdv(rendezvousX, rendezvousY);
                        } else if (!hasReachedTarget(avoidTargetX, avoidTargetY, true)) {
                            tryMove(true);
                        } else {
                            isAvoiding = false;
                        }
                    } else {
                        tryMove(true);
                    }
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

    @Override
    protected void scanRadar() {
        boolean enemyDetected = false;
        ArrayList<double[]> rawBullets = new ArrayList<>();

        for (IRadarResult o : detectRadar()) {
            double oX = position.getX() + o.getObjectDistance() * Math.cos(o.getObjectDirection());
            double oY = position.getY() + o.getObjectDistance() * Math.sin(o.getObjectDirection());

            if (o.getObjectType() == Types.BULLET) {
                rawBullets.add(new double[] { oX, oY });
                continue;
            }

            if (allies.get(botId).isAlive()) {
                double checkHeading = (state == State.MOVING_BACK)
                        ? normalize(getHeading() + Math.PI)
                        : getHeading();
                for (Position p : getObstacleCorners(o, position.getX(), position.getY())) {
                    if (isPointInTrajectory(position.getX(), position.getY(), checkHeading, p.getX(), p.getY())) {
                        startAvoidance();
                    }
                }
            }

            if (o.getObjectType() == Types.OpponentMainBot || o.getObjectType() == Types.OpponentSecondaryBot) {
                broadcast("ENEMY " + o.getObjectDirection() + " " + o.getObjectDistance()
                        + " " + o.getObjectType() + " " + oX + " " + oY);
                trackEnemy(oX, oY, o.getObjectDistance(), o.getObjectDirection(), true, o.getObjectType());
                enemyDetected = true;
            } else if (o.getObjectType() == Types.Wreck) {
                broadcast("WRECK " + oX + " " + oY);
                processWreckMessage(new String[] { "WRECK", String.valueOf(oX), String.valueOf(oY) });
            }
        }

        if (!enemyDetected && state != State.FIRE && !isAvoiding) {
            state = State.MOVING;
        }
        updateTrackedBullets(rawBullets);
    }

    private void readMessages() {
        ArrayList<String> messages = fetchAllMessages();
        ArrayList<String> enemyMessages = new ArrayList<>();

        for (String msg : messages) {
            String[] parts = msg.split(" ");
            switch (parts[0]) {
                case "ENEMY":
                    enemyMessages.add(msg);
                    break;
                case "WRECK":
                    processWreckMessage(parts);
                    break;
                case "POS":
                    processPosMessage(parts);
                    break;
                case "DEAD":
                    updateAllyDead(parts);
                    break;
            }
        }
        for (String msg : enemyMessages) {
            processEnemyMessage(msg.split(" "));
        }
    }

    private void processPosMessage(String[] parts) {
        updateAllyPosition(parts);
        if (state == State.FIRE)
            return;

        double botX = Double.parseDouble(parts[2]);
        double botY = Double.parseDouble(parts[3]);
        String senderId = parts[1];

        if (senderId.equals(SBOT) || (senderId.equals(NBOT) && !allies.get(SBOT).isAlive())) {
            rendezvousX = botX;
            rendezvousY = botY;
        }
    }

    private void processWreckMessage(String[] parts) {
        double wreckX = Double.parseDouble(parts[1]);
        double wreckY = Double.parseDouble(parts[2]);

        boolean exists = false;
        for (double[] wreck : wrecks) {
            if (Math.abs(wreck[0] - wreckX) < 20 && Math.abs(wreck[1] - wreckY) < 20) {
                exists = true;
                break;
            }
        }
        if (!exists)
            wrecks.add(new double[] { wreckX, wreckY });

        Enemy killed = null;
        for (Enemy enemy : enemies) {
            if (Math.abs(enemy.getX() - wreckX) < 50 && Math.abs(enemy.getY() - wreckY) < 50) {
                killed = enemy;
                break;
            }
        }
        enemies.remove(killed);
        positionsToAvoid.add(killed != null ? killed
                : new Enemy(wreckX, wreckY, 0, 0, Types.OpponentSecondaryBot));
    }

    private void processEnemyMessage(String[] parts) {
        double enemyX = Double.parseDouble(parts[4]);
        double enemyY = Double.parseDouble(parts[5]);
        double enemyDistance = Double.parseDouble(parts[2]);
        double enemyDirection = Double.parseDouble(parts[1]);
        Types enemyType = parts[3].contains("MainBot") ? Types.OpponentMainBot : Types.OpponentSecondaryBot;
        trackEnemy(enemyX, enemyY, enemyDistance, enemyDirection, false, enemyType);
    }

    private void executeFire(Enemy target) {
        if (target == null) {
            state = State.MOVING;
            return;
        }
        if (isManeuvering) {
            maneuverAfterFire(target);
            return;
        }

        double firingAngle = computeFiringAngle(position, target);
        if (firingAngle != Double.NaN) {
            fire(firingAngle);
            if (target.equals(lastFiredTarget))
                fireCount++;
            else
                fireCount = 0;
            if (fireCount >= MAX_FIRE_COUNT) {
                enemies.remove(target);
                fireCount = 0;
            }
            isManeuvering = true;
        } else {
            state = State.MOVING;
        }
    }

    private void maneuverAfterFire(Enemy target) {
        if (!isApproximatelySameDirection(target.direction, getHeading())) {
            turnTo(target.direction);
            isManeuvering = false;
            return;
        }

        boolean tooClose = distance(new Position(target.getX(), target.getY()), position) < 980;
        double checkHeading = tooClose ? normalize(getHeading() + Math.PI) : getHeading();

        for (IRadarResult o : detectRadar()) {
            if (!allies.get(botId).isAlive() || o.getObjectType() == Types.BULLET)
                continue;
            for (Position p : getObstacleCorners(o, position.getX(), position.getY())) {
                if (isPointInTrajectory(position.getX(), position.getY(), checkHeading, p.getX(), p.getY())) {
                    startAvoidance();
                    isManeuvering = false;
                    return;
                }
            }
        }
        tryMove(!tooClose);
        isManeuvering = false;
    }

    private double computeFiringAngle(Position from, Enemy to) {
        double dist = distance(new Position(to.getX(), to.getY()), from);
        to.predictPosition(dist / Parameters.bulletVelocity);

        double futureX = to.getPredictedX();
        double futureY = to.getPredictedY();
        double centerAngle = Math.atan2(futureY - from.getY(), futureX - from.getX());
        double vectorNorm = distance(new Position(futureX, futureY), from);
        double maxDeviation = Math.atan((BOT_BULLET_RADIUS - 1) / Math.max(vectorNorm, 1.0));

        for (double angle : new double[] { centerAngle, centerAngle - maxDeviation, centerAngle + maxDeviation }) {
            Position firingEnd = new Position(
                    from.getX() + Math.cos(angle) * Parameters.bulletRange,
                    from.getY() + Math.sin(angle) * Parameters.bulletRange);
            if (isLineClear(firingEnd))
                return angle;
        }
        return Double.NaN;
    }

    private boolean isLineClear(Position end) {
        for (BotState ally : allies.values()) {
            if (ally.getPosition().getX() == position.getX() && ally.getPosition().getY() == position.getY())
                continue;
            if (blocksFireLine(ally.getPosition(), end, BOT_RADIUS))
                return false;
        }
        for (double[] wreck : wrecks) {
            if (blocksFireLine(new Position(wreck[0], wreck[1]), end, BOT_RADIUS))
                return false;
        }
        return true;
    }

    private void trackEnemy(double x, double y, double dist, double direction,
            boolean isMyDetection, Types type) {
        if (!isMyDetection) {
            double dx = x - position.getX();
            double dy = y - position.getY();
            dist = Math.sqrt(dx * dx + dy * dy);
            direction = Math.atan2(dy, dx);
        }
        for (Enemy enemy : enemies) {
            if (Math.abs(enemy.getX() - x) < 50 && Math.abs(enemy.getY() - y) < 50) {
                enemy.updatePosition(x, y, dist, direction);
                return;
            }
        }
        enemies.add(new Enemy(x, y, dist, direction, type));
    }

    private boolean blocksFireLine(Position obstacle, Position target, double obstacleRadius) {
        double startX = position.getX(), startY = position.getY();
        double endX = target.getX(), endY = target.getY();
        double obsX = obstacle.getX(), obsY = obstacle.getY();

        double deltaX = endX - startX;
        double deltaY = endY - startY;
        double lineLength = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        if (lineLength < 1e-6)
            return false;

        double dirX = deltaX / lineLength;
        double dirY = deltaY / lineLength;
        double vecX = obsX - startX;
        double vecY = obsY - startY;
        double projection = vecX * dirX + vecY * dirY;

        if (projection < -1e-6 || projection > lineLength + 1e-6)
            return false;

        double perpendicularDist = Math.abs(vecX * dirY - vecY * dirX);
        return perpendicularDist < obstacleRadius + Parameters.bulletRadius + 1e-6;
    }

    private double bulletTravelTime(double tx, double ty) {
        double dx = tx - position.getX();
        double dy = ty - position.getY();
        return Math.sqrt(dx * dx + dy * dy) / Parameters.bulletVelocity;
    }

    private Enemy selectTarget() {
        Collections.sort(enemies, (e1, e2) -> Double.compare(e1.distance, e2.distance));
        for (Enemy enemy : enemies) {
            enemy.predictPosition(bulletTravelTime(enemy.getX(), enemy.getY()));
            Position predictedPos = new Position(enemy.getPredictedX(), enemy.getPredictedY());
            if (!isAllyInFireLine(predictedPos))
                return enemy;
        }
        return null;
    }

    private boolean isAllyInFireLine(Position predictedTarget) {
        for (Map.Entry<String, BotState> entry : allies.entrySet()) {
            BotState ally = entry.getValue();
            if (ally.getPosition().getX() == position.getX() && ally.getPosition().getY() == position.getY())
                continue;

            Position predictedAllyPos;
            if (entry.getKey().equals(NBOT) || entry.getKey().equals(SBOT)) {
                // Scouts move faster (speed 3) when a shooter is nearby, otherwise stay put
                double scoutSpeed = isShooterNearby() ? 3 : 0;
                predictedAllyPos = new Position(
                        ally.getPosition().getX() + Math.cos(getHeading()) * scoutSpeed,
                        ally.getPosition().getY() + Math.sin(getHeading()) * scoutSpeed);
            } else {
                predictedAllyPos = new Position(
                        ally.getPosition().getX() + Math.cos(getHeading()),
                        ally.getPosition().getY() + Math.sin(getHeading()));
            }

            if (blocksFireLine(predictedAllyPos, predictedTarget, BOT_RADIUS))
                return true;
        }
        return false;
    }
}

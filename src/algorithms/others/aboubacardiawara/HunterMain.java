package algorithms.others.aboubacardiawara;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

import algorithms.others.aboubacardiawara.brains.core.MainBotBaseBrain;
import algorithms.others.aboubacardiawara.brains.core.dto.Const;
import algorithms.others.aboubacardiawara.brains.core.dto.Position;
import algorithms.others.aboubacardiawara.brains.core.dto.RobotState;
import algorithms.others.aboubacardiawara.statemachine.impl.State;
import algorithms.others.aboubacardiawara.statemachine.interfaces.IState;
import characteristics.IRadarResult;
import characteristics.Parameters;

public class HunterMain extends MainBotBaseBrain {

    private double targetDirection;
    protected double randWalkDirection;
    protected int randWalkMoveCount;
    protected Position rendezVousPosition;
    protected final int teammateRadius = 55;
    protected double findALineOfFireStartAngle;
    protected double findALineOfFireMoveCounter;
    protected double rotationCount;
    private boolean bullet_detected = false;
    private boolean opponent_detected = false;
    boolean fire_opennet_detected = false;
    private int init_place_robot = 150;
    double initStateAngleTarget = 0;

    // Enemy position history for velocity-based prediction
    private Position lastKnownTargetPosition = null;
    private Position prevKnownTargetPosition = null;

    // Avoid wasting bullets on a stale/unreachable target indefinitely
    private int fireStrikeCount = 0;
    private static final int MAX_FIRE_STRIKE = 80;

    Random rn = new Random();
    int detect_openent = -1;
    protected Map<Robots, RobotState> teammatesPositions = new HashMap<>();

    @Override
    protected IState buildStateMachine() {
        IState initState = new State();
        initState.setDescription("Init State");
        IState MoveState = new State();
        MoveState.setDescription("Let's move !");
        IState TurnTowardEnemies = new State();
        TurnTowardEnemies.setDescription("Turn Right");
        IState STMoveEast = new State();
        STMoveEast.setDescription("Move East");
        IState STStartFire = new State();
        STStartFire.setDescription("Start Fire");
        IState STStopFire = new State();
        IState StFirebyRadar = new State();
        STStopFire.setDescription("Stop Fire");
        StFirebyRadar.setDescription("Fire A-R");
        IState STMoveWest = new State();
        STMoveWest.setDescription("Move West");
        IState STTurnWest = new State();
        STTurnWest.setDescription("Turn West");
        IState STTurnEast = new State();
        STTurnEast.setDescription("Turn East");
        IState DeblocState1 = new State();
        DeblocState1.setDescription("Debloc State1");
        IState DeblocState2 = new State();
        DeblocState2.setDescription("Debloc State2");
        IState FireByradar = new State();
        FireByradar.setDescription("Fire by radar");

        // Emplacement des robots
        if (this.leftSide) {
            if (this.currentRobot == Robots.MRUP) {
                initStateAngleTarget = this.getHeading() - (Math.PI / 4);
            } else {
                if (this.currentRobot == Robots.MRBOTTOM) {
                    initStateAngleTarget = this.getHeading() + (Math.PI / 4);
                }
            }
        } else {
            if (this.currentRobot == Robots.MRUP) {
                initStateAngleTarget = this.getHeading() + (Math.PI / 4);
            } else {
                if (this.currentRobot == Robots.MRBOTTOM) {
                    initStateAngleTarget = this.getHeading() - (Math.PI / 4);
                } else {
                    initStateAngleTarget = Math.PI;
                }
            }
        }

        initState.addNext(MoveState, () -> isSameDirection(getHeading(), initStateAngleTarget));
        initState.setStateAction(() -> {
            if (leftSide) {
                if (this.currentRobot == Robots.MRUP) {
                    turnLeft();
                } else {
                    turnRight();
                }
            } else {
                if (this.currentRobot == Robots.MRUP) {
                    turnRight();
                } else {
                    turnLeft();
                }
            }
        });

        MoveState.addNext(TurnTowardEnemies, () -> init_place_robot == 0);
        MoveState.setStateAction(() -> {
            move();
            init_place_robot--;
        });

        double turnTowardEnemiesTargetDirection;
        if (this.leftSide) {
            turnTowardEnemiesTargetDirection = Parameters.EAST;
        } else {
            turnTowardEnemiesTargetDirection = Parameters.WEST;
        }
        TurnTowardEnemies.addNext(STMoveEast, () -> isSameDirection(getHeading(), turnTowardEnemiesTargetDirection));
        TurnTowardEnemies.setStateAction(() -> {
            if (this.leftSide) {
                if (this.currentRobot == Robots.MRUP) {
                    turnRight();
                } else {
                    turnLeft();
                }
            } else {
                if (this.currentRobot == Robots.MRUP) {
                    turnLeft();
                } else {
                    turnRight();
                }
            }
        });

        STMoveEast.addNext(STStartFire, () -> opponent_detected);
        STMoveEast.setStateAction(() -> {
            for (IRadarResult radar : detectRadar()) {
                // FIX: bullet and opponent are separate checks — && would always be false
                if (radar.getObjectType() == IRadarResult.Types.BULLET) {
                    bullet_detected = true;
                }
                if (radar.getObjectType() == IRadarResult.Types.OpponentMainBot
                        || radar.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
                    opponent_detected = true;
                    break;
                }
            }
            // FIX: actually evade — don't reset bullet_detected before acting
            if (bullet_detected) {
                moveBack();
                bullet_detected = false;
            } else {
                move();
            }
        });

        // Fire using positions broadcast by secondary bots
        STMoveEast.addNext(FireByradar, () -> detectOpponentBis());
        FireByradar.setStateAction(() -> {
            double angle = predictedFireAngle();
            // FIX: friendly fire check before shooting
            if (!isTeammateInLineOfFire(angle)) {
                fire(normalize(angle));
                fireStrikeCount++;
            }
            // FIX: drop stale target after MAX_FIRE_STRIKE consecutive shots
            if (fireStrikeCount >= MAX_FIRE_STRIKE) {
                fireStrikeCount = 0;
                lastKnownTargetPosition = null;
                prevKnownTargetPosition = null;
            }
        });
        FireByradar.addNext(STMoveEast, () -> !detectOpponentBis());

        // Fire using the robot's own radar
        STStartFire.addNext(STMoveEast, () -> !opponent_detected && !fire_opennet_detected);
        STStartFire.setStateAction(() -> {
            fire_opennet_detected = false;
            for (IRadarResult radar : detectRadar()) {
                // FIX: separate bullet check
                if (radar.getObjectType() == IRadarResult.Types.BULLET) {
                    bullet_detected = true;
                }
                if (radar.getObjectType() == IRadarResult.Types.OpponentMainBot
                        || radar.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
                    double oppX = robotX + radar.getObjectDistance() * Math.cos(radar.getObjectDirection());
                    double oppY = robotY + radar.getObjectDistance() * Math.sin(radar.getObjectDirection());
                    // FIX: use velocity-based predicted position instead of current position
                    double firingAngle = computePredictedAngle(Position.of(oppX, oppY));
                    // FIX: check friendly fire before shooting
                    if (!isTeammateInLineOfFire(firingAngle)) {
                        fire(normalize(firingAngle));
                        fire_opennet_detected = true;
                        fireStrikeCount++;
                    }
                }
            }
            // FIX: actually evade bullets
            if (bullet_detected) {
                moveBack();
                bullet_detected = false;
            }
            opponent_detected = false;
            // FIX: abandon target if we've been firing without kill for too long
            if (fireStrikeCount >= MAX_FIRE_STRIKE) {
                fireStrikeCount = 0;
                fire_opennet_detected = false;
                lastKnownTargetPosition = null;
                prevKnownTargetPosition = null;
            }
        });

        // FIX: wall avoidance uses fastWayToTurn so it always picks the shortest arc,
        // instead of always turning right (which could be 270° the wrong way)
        STMoveEast.addNext(STTurnWest, () -> detectWall() && isSameDirection(getHeading(), Parameters.EAST));
        STMoveEast.addNext(STTurnWest, () -> detectWall() && isSameDirection(getHeading(), Parameters.NORTH));
        STMoveEast.addNext(STTurnEast, () -> detectWall() && isSameDirection(getHeading(), Parameters.SOUTH));
        STMoveEast.addNext(STTurnEast, () -> detectWall() && isSameDirection(getHeading(), Parameters.WEST));
        STTurnWest.addNext(STMoveEast, () -> isSameDirection(getHeading(), Parameters.WEST));
        STTurnWest.setStateAction(() -> {
            stepTurn(fastWayToTurn(Parameters.WEST));
        });
        STTurnEast.addNext(STMoveEast, () -> isSameDirection(getHeading(), Parameters.EAST));
        STTurnEast.setStateAction(() -> {
            stepTurn(fastWayToTurn(Parameters.EAST));
        });

        return initState;
    }

    /**
     * Computes the angle to fire at an opponent's predicted future position.
     * Falls back to current position if no velocity history is available.
     */
    private double computePredictedAngle(Position current) {
        if (prevKnownTargetPosition != null && lastKnownTargetPosition != null) {
            double distance = current.distanceTo(Position.of(robotX, robotY));
            double travelTime = distance / Parameters.bulletVelocity;
            double vx = lastKnownTargetPosition.getX() - prevKnownTargetPosition.getX();
            double vy = lastKnownTargetPosition.getY() - prevKnownTargetPosition.getY();
            // Detect oscillation: if velocity reversed, aim at average position instead
            double prevVx = prevKnownTargetPosition.getX() - current.getX();
            double prevVy = prevKnownTargetPosition.getY() - current.getY();
            boolean oscillatingX = (prevVx * vx < 0);
            boolean oscillatingY = (prevVy * vy < 0);
            double predictedX, predictedY;
            if (oscillatingX || oscillatingY) {
                predictedX = (current.getX() + lastKnownTargetPosition.getX()) / 2;
                predictedY = (current.getY() + lastKnownTargetPosition.getY()) / 2;
            } else {
                predictedX = current.getX() + vx * travelTime;
                predictedY = current.getY() + vy * travelTime;
            }
            return Math.atan2(predictedY - robotY, predictedX - robotX);
        }
        return Math.atan2(current.getY() - robotY, current.getX() - robotX);
    }

    /**
     * Returns the predicted firing angle toward the last tracked target,
     * accounting for its velocity. Used by FireByradar state.
     */
    private double predictedFireAngle() {
        if (lastKnownTargetPosition != null && prevKnownTargetPosition != null) {
            double distance = lastKnownTargetPosition.distanceTo(Position.of(robotX, robotY));
            double travelTime = distance / Parameters.bulletVelocity;
            double vx = lastKnownTargetPosition.getX() - prevKnownTargetPosition.getX();
            double vy = lastKnownTargetPosition.getY() - prevKnownTargetPosition.getY();
            double predictedX = lastKnownTargetPosition.getX() + vx * travelTime;
            double predictedY = lastKnownTargetPosition.getY() + vy * travelTime;
            return Math.atan2(predictedY - robotY, predictedX - robotX);
        }
        return targetDirection;
    }

    /**
     * Returns true if any known teammate lies within teammateRadius of the
     * bullet trajectory (line from robot to bullet range end at firingAngle).
     */
    private boolean isTeammateInLineOfFire(double firingAngle) {
        double endX = robotX + Math.cos(firingAngle) * Parameters.bulletRange;
        double endY = robotY + Math.sin(firingAngle) * Parameters.bulletRange;
        double dx = endX - robotX;
        double dy = endY - robotY;
        double segLen = Math.sqrt(dx * dx + dy * dy);
        if (segLen < 1e-6) return false;

        for (RobotState teammate : teammatesPositions.values()) {
            Position pos = teammate.getPosition();
            double px = pos.getX() - robotX;
            double py = pos.getY() - robotY;
            // Projection along the firing direction
            double along = (px * dx + py * dy) / segLen;
            if (along < 0 || along > segLen) continue;
            // Perpendicular distance to the firing line
            double perp = Math.abs(px * dy - py * dx) / segLen;
            if (perp < teammateRadius) {
                return true;
            }
        }
        return false;
    }

    protected boolean isSameDirection(double heading, double expectedDirection, boolean log) {
        return super.isSameDirection(heading, expectedDirection);
    }

    private ArrayList<String> filterMessages(Predicate<String> f) {
        return this.receivedMessages.stream().filter(f).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    private List<Position> positionsOfOpponents(Map<String, List<Position>> opponents) {
        List<Position> positions = new ArrayList<>();
        positions.addAll(opponents.get("main"));
        positions.addAll(opponents.get("secondary"));
        return positions;
    }

    /**
     * Check if there is any opponent in the radar
     *
     * @return 0 if there is no opponent in the radar or they are out of line of
     *         fire
     *         1 if there is at least one opponent in the radar and it is in the
     *         line of fire
     *         2 if the opponents are out of line of fire because of teammates
     *         being in line of fire.
     */
    private int detectOpponent() {
        Map<String, List<Position>> opponents = getOpponentsPosEnhanced();
        List<Position> positions = positionsOfOpponents(opponents);
        Optional<Position> optionalPosition = candidatEnemyToShot(opponents);

        if (optionalPosition.isPresent()) {
            Position closestPos = optionalPosition.get();
            double distance = closestPos.distanceTo(Position.of(robotX, robotY));
            // Update velocity history for predictive aiming
            prevKnownTargetPosition = lastKnownTargetPosition;
            lastKnownTargetPosition = closestPos;
            this.targetDirection = Math.atan2(closestPos.getY() - robotY, closestPos.getX() - robotX);
            if (distance > Parameters.bulletRange) {
                return DetectionResultCode.OPPONENT_OUT_OF_LINE_OF_FIRE;
            } else {
                return DetectionResultCode.OPPONENT_IN_LINE_OF_FIRE;
            }
        } else {
            boolean noOpponent = positions.size() == 0;
            if (noOpponent)
                return DetectionResultCode.ANY_OPPONENT;
            else {
                return DetectionResultCode.TEAMMATES_IN_LINE_OF_FIRE;
            }
        }
    }

    private boolean detectOpponentBis() {
        Map<String, List<Position>> opponents = getOpponentsPosEnhanced();
        Optional<Position> optionalPosition = candidatEnemyToShot(opponents);
        if (optionalPosition.isPresent()) {
            Position closestPos = optionalPosition.get();
            double distance = closestPos.distanceTo(Position.of(robotX, robotY));
            this.targetDirection = Math.atan2(closestPos.getY() - robotY, closestPos.getX() - robotX);
            if (distance < Parameters.bulletRange) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pick a candidate to shoot among the list of positions.
     * main has priority over secondary; closest wins within each category.
     */
    private Optional<Position> candidatEnemyToShot(Map<String, List<Position>> opponents) {
        List<Position> mainPositions = opponents.get("main");
        List<Position> secondaryPositions = opponents.get("secondary");
        if (mainPositions.size() > 0) {
            return mainPositions.stream().min((p1, p2) -> {
                double d1 = p1.distanceTo(Position.of(robotX, robotY));
                double d2 = p2.distanceTo(Position.of(robotX, robotY));
                return Double.compare(d1, d2);
            });
        } else if (secondaryPositions.size() > 0) {
            return secondaryPositions.stream().min((p1, p2) -> {
                double d1 = p1.distanceTo(Position.of(robotX, robotY));
                double d2 = p2.distanceTo(Position.of(robotX, robotY));
                return Double.compare(d1, d2);
            });
        } else {
            return Optional.empty();
        }
    }

    private Map<String, List<Position>> getOpponentsPosEnhanced() {
        Predicate<String> isOpponentLocationMessage = msg -> msg.startsWith(Const.OPPONENT_POS_MSG_SIGN, 0);
        List<String> opponentLocationMsgs = filterMessages(isOpponentLocationMessage);
        // init with main -> [], and secondary -> []
        Map<String, List<Position>> opponents = new HashMap<>();
        opponents.put("main", new ArrayList<>());
        opponents.put("secondary", new ArrayList<>());
        // group by "secondary" and "main"
        opponentLocationMsgs.forEach(msg -> {
            String[] elements = parseOpponentsPosMessage(msg);
            double y = Double.valueOf(elements[1]);
            double x = Double.valueOf(elements[2]);
            String type = elements[4];
            opponents.get(type).add(new Position(x, y));
        });

        return opponents;
    }

    private String[] parseOpponentsPosMessage(String msg) {
        String[] elements = msg.split(Const.MSG_SEPARATOR);
        return elements;
    }

    private void updateTeammatesPositions() {
        ArrayList<String> messages = filterMessages(
                msg -> msg.startsWith(Const.TEAM_POS_MSG_SIGN, 0));
        messages.forEach(msg -> {
            RobotState state = RobotState.of(msg);
            this.teammatesPositions.put(state.getRobotName(), state);
        });
    }

    @Override
    protected void beforeEachStep() {
        super.beforeEachStep();
        detectOpponent();
        updateTeammatesPositions();
        logRobotPosition();
    }

    @Override
    protected void afterEachStep() {
        super.afterEachStep();
        sendLogMessage(this.currentState.toString() + " " + this.targetDirection);
    }

    @Override
    protected void exportGraphset() {
        super.exportGraphset();
        String fileName = "mainBotStateMachine.dot";
        String graph = currentState.dotify();
        writeToFile(fileName, graph);
    }

    private void writeToFile(String fileName, String graph) {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(fileName)) {
            writer.println(graph);
            writer.close();
        } catch (java.io.FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}

/**
 * DetectionResultCode
 */
class DetectionResultCode {
    public static final int ANY_OPPONENT = 0;
    public static final int OPPONENT_IN_LINE_OF_FIRE = 1;
    public static final int OPPONENT_OUT_OF_LINE_OF_FIRE = 2;
    public static final int TEAMMATES_IN_LINE_OF_FIRE = 3;
}

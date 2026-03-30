package algorithms.others.aboubacardiawara.brains.core;

import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;
import characteristics.Parameters;
import robotsimulator.Brain;
import characteristics.IFrontSensorResult.Types;
import characteristics.Parameters.Direction;

import static characteristics.IFrontSensorResult.Types.WALL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import algorithms.others.aboubacardiawara.statemachine.AnyTransitionConditionMetException;
import algorithms.others.aboubacardiawara.statemachine.interfaces.IState;

public abstract class BaseBrain extends Brain {

    protected Logger logger = Logger.getLogger("BaseBrain");

    protected boolean leftSide = true;

    private int stateCounter = 0;

    protected ArrayList<String> receivedMessages = new ArrayList<>();

    // main robot (up|middle|bottom) + secondary robot (up|bottom)
    public enum Robots {
        MRUP, MRMIDDLE, MRBOTTOM, SRUP, SRBOTTOM
    };

    protected Map<Robots, double[]> teammatesPositions;

    protected Robots currentRobot;

    protected double robotX;
    protected double robotY;
    protected IState currentState;
    protected List<IRadarResult> detectRadarResult = new ArrayList<>();
    int position = 0;
    public static String OPPONENT_POS_MSG_SIGN = "OPPONENT_POS_MSG";
    public static String TEAM_POS_MSG_SIGN = "TEAM_POS_MSG";
    public static String MSG_SEPARATOR = ":";

    protected double initialX() {
        return 0;
    }

    protected double initialY() {
        return 0;
    }

    protected boolean temmateDetected() {
        Types objectType = detectFront().getObjectType();
        return objectType == IFrontSensorResult.Types.TeamMainBot
                || objectType == IFrontSensorResult.Types.TeamSecondaryBot;
    }

    @Override
    public void activate() {
        identifyInitSide();
        currentRobot = identifyRobot();
        this.robotX = initialX();
        this.robotY = initialY();
        currentState = buildStateMachine();
        exportGraphset();
    }

    /**
     * create a file and write the graphset of the state machine in.
     */
    protected void exportGraphset() {
    }

    /**
     * Detect which side our robot is in the field.
     */
    protected void identifyInitSide() {
        this.leftSide = isSameDirection(getHeading(), Parameters.EAST);
    }

    protected abstract Robots identifyRobot();

    protected abstract IState buildStateMachine();

    protected static double EPSILON = 0.05;

    protected boolean wallDetected() {
        boolean res = detectWall();
        return res;
    }

    protected boolean detectWall() {
        return detectFront().getObjectType() == WALL;
    }

    /**
     * Should be called first in each overridden method.
     */
    protected void beforeEachStep() {
        this.receivedMessages = fetchAllMessages();
    }

    protected void afterEachStep() {
        sendLogMessage(this.currentState.toString());
        // sendMyStateToTeammates();
    }

    @Override
    public void step() {
        if (this.currentState.toString().equals("Start Fire")) {
            // System.out.println("steping !");
        }
        if (!Objects.isNull(currentState)) {
            try {
                if (this.stateCounter == 0)
                    currentState.setUp();
                currentState = currentState.next();
                currentState.tearDown();
                this.stateCounter = 0;
            } catch (AnyTransitionConditionMetException e) {
                if (this.currentState.toString().equals("Start Fire")) {
                    // System.out.println("FIRE ACTION");
                }
                this.beforeEachStep();
                currentState.performsAction();
                this.afterEachStep();
                this.stateCounter++;
            }
        }
    }

    @Override
    public double getHeading() {
        return normalize(super.getHeading());
    }

    protected boolean isSameDirection(double heading, double expectedDirection) {
        return isSameDirection(heading, expectedDirection, EPSILON);
    }

    protected boolean isSameDirection(double heading, double expectedDirection, double epsilon) {
        return Math.abs(normalize(heading) - normalize(expectedDirection)) < epsilon;
    }

    protected double normalize(double dir) {
        double res = dir;
        while (res < 0)
            res += 2 * Math.PI;
        while (res >= 2 * Math.PI)
            res -= 2 * Math.PI;
        return res;
    }

    protected void turnRight() {
        stepTurn(Parameters.Direction.RIGHT);
    }

    protected void turnLeft() {
        stepTurn(Parameters.Direction.LEFT);
    }

    protected boolean isOpponentBot(IRadarResult radarResult) {
        return radarResult.getObjectType() == IRadarResult.Types.OpponentMainBot
                || radarResult.getObjectType() == IRadarResult.Types.OpponentSecondaryBot;
    }

    protected void logRobotPosition() {
        sendLogMessage("x: " + robotX + " y: " + robotY);
    }

    protected List<IRadarResult> detectOpponents() {
        this.detectRadarResult = detectRadar();
        List<IRadarResult> opponents = new ArrayList<>();
        for (IRadarResult radarResult : this.detectRadarResult) {
            if (isOpponentBot(radarResult)) {
                opponents.add(radarResult);
            }
        }
        return opponents;
    }

    protected boolean isNotDead(IRadarResult radarResult) {
        return radarResult.getObjectType() != IRadarResult.Types.Wreck;
    }

    protected Direction fastWayToTurn(double targetDirection) {
        double diff = targetDirection - this.getHeading();
        if (diff > Math.PI) {
            return Parameters.Direction.LEFT;
        } else if (diff < -Math.PI) {
            return Parameters.Direction.RIGHT;
        } else if (diff > 0) {
            return Parameters.Direction.RIGHT;
        } else {
            return Parameters.Direction.LEFT;
        }
    }

    protected Direction fastWayToTurnV2(double targetDirection) {
        // double diff = targetDirection - this.getHeading();
        // normalize les angles et le resultat
        double diff = normalize(normalize(targetDirection) - normalize(this.getHeading()));
        if (diff <= Math.PI / 2) {
            return Parameters.Direction.LEFT;
        } else {
            return Parameters.Direction.RIGHT;
        }
    }

    protected boolean obstacleDetected() {
        boolean wallDetected = wallDetected();
        boolean objectDetected = this.detectRadarResult.stream().anyMatch(result -> {
            // System.out.println("OBJECT DISTANCE: " + result.getObjectDistance());
            return result.getObjectDistance() < 100; // the distance is always too big ()
        });

        if (wallDetected) {
            // System.out.println("WALL DETECTED BY " + currentRobot);
        }
        if (objectDetected) {
            // System.out.println("OBJECT DETECTED BY " + currentRobot);
        }

        return wallDetected || objectDetected;
    }

}
package algorithms.others.aboubacardiawara.brains.core;

import java.util.ArrayList;
import java.util.stream.Collectors;

import characteristics.IRadarResult;
import characteristics.Parameters;

public abstract class MainBotBaseBrain extends BaseBrain {

    @Override
    public void move() {
        super.move();
        robotX += Parameters.teamAMainBotSpeed * Math.cos(getHeading());
        robotY += Parameters.teamAMainBotSpeed * Math.sin(getHeading());
    }

    @Override
    protected Robots identifyRobot() {
        ArrayList<IRadarResult> mainRobotsAround = detectRadar();

        if (thereIsRobotUpAndDown(mainRobotsAround)) {
            return Robots.MRMIDDLE;
        } else if (thereIsRobotUp(mainRobotsAround)) {
            return Robots.MRBOTTOM;
        }
        return Robots.MRUP;

    }

    private boolean thereIsRobotUp(ArrayList<IRadarResult> mainRobotsAround) {
        return mainRobotsAround
                .stream()
                .filter(radarResult -> radarResult.getObjectType() == IRadarResult.Types.TeamMainBot)
                .filter(radarResult -> radarResult.getObjectDirection() == Parameters.NORTH)
                .collect(Collectors.toCollection(ArrayList::new))
                .size() == 1;
    }

    private boolean thereIsRobotUpAndDown(ArrayList<IRadarResult> mainRobotsAround) {

        return mainRobotsAround
                .stream()
                .filter(radarResult -> radarResult.getObjectType() == IRadarResult.Types.TeamMainBot)
                .filter(radarResult -> radarResult.getObjectDirection() == Parameters.NORTH
                        || radarResult.getObjectDirection() == Parameters.SOUTH)
                .collect(Collectors.toCollection(ArrayList::new))
                .size() == 2;
    }

    @Override
    protected double initialX() {
        if (leftSide) {
            if (currentRobot == Robots.MRUP) {
                return Parameters.teamAMainBot1InitX;
            } else if (currentRobot == Robots.MRMIDDLE) {
                return Parameters.teamAMainBot2InitX;
            } else {
                return Parameters.teamAMainBot3InitX;
            }
        } else {
            if (currentRobot == Robots.MRUP) {
                return Parameters.teamBMainBot1InitX;
            } else if (currentRobot == Robots.MRMIDDLE) {
                return Parameters.teamBMainBot2InitX;
            } else {
                return Parameters.teamBMainBot3InitX;
            }
        }
    }

    @Override
    protected double initialY() {
        if (leftSide) {
            if (currentRobot == Robots.MRUP) {
                return Parameters.teamAMainBot1InitY;
            } else if (currentRobot == Robots.MRMIDDLE) {
                return Parameters.teamAMainBot2InitY;
            } else {
                return Parameters.teamAMainBot3InitY;
            }
        } else {
            if (currentRobot == Robots.MRUP) {
                return Parameters.teamBMainBot1InitY;
            } else if (currentRobot == Robots.MRMIDDLE) {
                return Parameters.teamBMainBot2InitY;
            } else {
                return Parameters.teamBMainBot3InitY;
            }
        }
    }
}
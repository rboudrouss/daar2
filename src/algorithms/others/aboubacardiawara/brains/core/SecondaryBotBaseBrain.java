package algorithms.others.aboubacardiawara.brains.core;

import characteristics.IRadarResult;
import characteristics.Parameters;

public abstract class SecondaryBotBaseBrain extends BaseBrain {

    //

    @Override
    public void move() {
        super.move();
        if (leftSide) {
            robotX += Parameters.teamASecondaryBotSpeed * Math.cos(getHeading());
            robotY += Parameters.teamASecondaryBotSpeed * Math.sin(getHeading());
        } else {
            robotX += Parameters.teamBSecondaryBotSpeed * Math.cos(getHeading());
            robotY += Parameters.teamBSecondaryBotSpeed * Math.sin(getHeading());

        }
    }

    @Override
    protected double initialX() {
        if (leftSide) {
            if (currentRobot == Robots.SRUP) {
                return Parameters.teamASecondaryBot1InitX;
            } else {
                return Parameters.teamASecondaryBot2InitX;
            }
        } else {
            if (currentRobot == Robots.SRUP) {
                return Parameters.teamBSecondaryBot1InitX;
            } else {
                return Parameters.teamBSecondaryBot2InitX;
            }
        }
    }

    @Override
    protected double initialY() {
        if (leftSide) {
            if (currentRobot == Robots.SRUP) {
                return Parameters.teamASecondaryBot1InitY;
            } else {
                return Parameters.teamASecondaryBot2InitY;
            }
        } else {
            if (currentRobot == Robots.SRUP) {
                return Parameters.teamBSecondaryBot1InitY;
            } else {
                return Parameters.teamBSecondaryBot2InitY;
            }
        }
    }

    @Override
    protected Robots identifyRobot() {
        for (IRadarResult radarResult : detectRadar()) {
            if (isSameDirection(radarResult.getObjectDirection(), Parameters.SOUTH)) {
                if (radarResult.getObjectType() == IRadarResult.Types.TeamSecondaryBot) {
                    return Robots.SRUP;
                }
            }
        }
        return Robots.SRBOTTOM;
    }

}
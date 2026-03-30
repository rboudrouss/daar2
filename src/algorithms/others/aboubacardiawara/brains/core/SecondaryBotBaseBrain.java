package algorithms.others.aboubacardiawara.brains.core;

import algorithms.others.aboubacardiawara.brains.core.dto.Const;
import characteristics.IRadarResult;
import characteristics.Parameters;

public abstract class SecondaryBotBaseBrain extends BaseBrain {

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

    /**
     * Builds the broadcast message announcing an opponent's position.
     * Format: "OPPONENT_POS_MSG:posY:posX:health:type"
     */
    protected String buildOpponentPosMessage(IRadarResult radarResult, double oppX, double oppY) {
        String type = radarResult.getObjectType() == IRadarResult.Types.OpponentMainBot ? "main" : "secondary";
        return Const.OPPONENT_POS_MSG_SIGN
                + Const.MSG_SEPARATOR + oppY
                + Const.MSG_SEPARATOR + oppX
                + Const.MSG_SEPARATOR + getHealth()
                + Const.MSG_SEPARATOR + type;
    }

    /**
     * Broadcasts the position of every live opponent currently visible on radar.
     * Called automatically in beforeEachStep so all states benefit, not only DetecState.
     */
    private void sendOpponentPositions() {
        for (IRadarResult r : detectRadar()) {
            if (isOpponentBot(r) && isNotDead(r)) {
                double oppX = robotX + r.getObjectDistance() * Math.cos(r.getObjectDirection());
                double oppY = robotY + r.getObjectDistance() * Math.sin(r.getObjectDirection());
                broadcast(buildOpponentPosMessage(r, oppX, oppY));
            }
        }
    }

    @Override
    protected void beforeEachStep() {
        super.beforeEachStep();
        sendOpponentPositions();
    }

}

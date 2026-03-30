package algorithms.others.aboubacardiawara.brains.core.dto;

import algorithms.others.aboubacardiawara.brains.core.BaseBrain.Robots;

/** Holf informations about a robot (Main | Secondary)
 * RobotState
 */
public class RobotState {
    
    private Position position;
    
    private double health;

    private boolean isMain;

    private boolean isSecondary;

    private Robots robotName;

    public Position getPosition() {
        return position;
    }

    public double getHealth() {
        return health;
    }


    public boolean isMain() {
        return isMain;
    }


    public boolean isSecondary() {
        return isSecondary;
    }


    public Robots getRobotName() {
        return robotName;
    }

    public RobotState robotName(Robots robotName) {
        this.robotName = robotName;
        return this;
    }

    public RobotState position(Position position) {
        this.position = position;
        return this;
    }

    public RobotState health(double health) {
        this.health = health;
        return this;
    }

    public RobotState isMain(boolean isMain) {
        this.isMain = isMain;
        return this;
    }

    public RobotState isSecondary(boolean isSecondary) {
        this.isSecondary = isSecondary;
        return this;
    }

    

    public static RobotState of(String message) {
        String[] res = message.split(Const.MSG_SEPARATOR);
        
        RobotState robotState = new RobotState();
        
        return robotState
            .robotName(Robots.valueOf(res[1]))
            .position(Position.of(Double.valueOf(res[3]), Double.valueOf(res[2])))
            .health(Double.parseDouble(res[4]))
            .isMain(res[5].equals(Const.MAIN_SIGN))
            .isSecondary(res[5].equals(Const.SECONDARY_SIGN));
    }

    /** Produce a string representation of the robot state.
     * message format: "SIGN:robotName:posY:posX:health:group"
     */
    @Override
    public String toString() {
        StringBuilder message = new StringBuilder();
        return message
            .append(Const.TEAM_POS_MSG_SIGN)
            .append(Const.MSG_SEPARATOR)
            .append(robotName)
            .append(Const.MSG_SEPARATOR)
            .append(position.getY())
            .append(Const.MSG_SEPARATOR)
            .append(position.getX())
            .append(Const.MSG_SEPARATOR)
            .append(health)
            .append(Const.MSG_SEPARATOR)
            .append(isMain ? Const.MAIN_SIGN : isSecondary ? Const.SECONDARY_SIGN : "")
            .toString();
    }

    


}
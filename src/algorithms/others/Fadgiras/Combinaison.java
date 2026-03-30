package algorithms.others.Fadgiras;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;

public class Combinaison extends Brain {
  //---PARAMETERS---//
  private static final double HEADINGPRECISION = 0.01;
  private static final double ANGLEPRECISION = 0.001;

  private Parameters.Direction turnDirectionForAvoiding;

  //---VARIABLES---//
  private boolean turnRightTask,fallBackCoveringFireTask, avoidTeambot, avoidingTeammate,avoidingWreck;
  private double endTaskDirection,endMoveTask,distance;
  private static enum Action { NONE, MOVE, MOVEBACK, FIRELEFT, FIRERIGHT, FIRE };
  private static Action[] fallBackCoveringFireScheme = { Action.FIRE, Action.MOVEBACK, Action.FIRELEFT, Action.MOVEBACK, Action.FIRERIGHT, Action.MOVEBACK };
  //private static Action[] fallBackCoveringFireScheme = { Action.FIRE, Action.MOVEBACK};
  private int schemeIndex;

  //---CONSTRUCTORS---//
  public Combinaison() { super(); }

  //---ABSTRACT-METHODS-IMPLEMENTATION---//
  public void activate() {
    turnRightTask=false;
    move();
    sendLogMessage("Moving a head. Waza!");
  }
  public void step() {
    if (fallBackCoveringFireTask) {
      if (distance>endMoveTask) {
        fallBackCoveringFireTask=false;
      } else {
        switch (fallBackCoveringFireScheme[schemeIndex]){
          case MOVEBACK:
            moveBack();
            distance+=Parameters.teamAMainBotSpeed;
            break;
          case FIRE:
            fire(getHeading());
            break;
          case FIRELEFT:
            fire(getHeading()-0.01*Math.PI);
            break;
          case FIRERIGHT:
            fire(getHeading()+0.01*Math.PI);
            break;
        }
        schemeIndex=(schemeIndex+1)%fallBackCoveringFireScheme.length;
      }
      return;
    }

    if (avoidTeambot) {
      if (distance < endMoveTask) {
        moveBack();
        distance += Parameters.teamAMainBotSpeed;
      } else if (isSameDirection(myGetHeading(), Parameters.NORTH)) {
        // Decide to turn right or left
        stepTurn(Parameters.Direction.RIGHT); // or Parameters.Direction.LEFT
        avoidingTeammate = true;
        // Reset distance
        distance = 0;
      } else if (avoidingTeammate && detectFront().getObjectType() != IFrontSensorResult.Types.TeamSecondaryBot) {
        stepTurn(Parameters.Direction.LEFT); // or Parameters.Direction.RIGHT depending on the earlier choice
        // Reset distance
        distance = 0;
      } else if (avoidingTeammate && distance < endMoveTask) {
        move();
        distance += Parameters.teamAMainBotSpeed;
      } else {
        avoidingTeammate = false;
        avoidTeambot = false;
      }
      return;
    }

    if (turnRightTask) {
      if (isHeading(endTaskDirection)) {
	turnRightTask=false;
      } else {
	stepTurn(Parameters.Direction.RIGHT);
      }
      return;
    }
    if (detectFront().getObjectType()==IFrontSensorResult.Types.WALL) {
      fallBackCoveringFireTask=false;
      turnRightTask=true;
      endTaskDirection=getHeading()+Parameters.RIGHTTURNFULLANGLE;
      stepTurn(Parameters.Direction.RIGHT);
      sendLogMessage("Iceberg at 12 o'clock. Heading to my three!");
      return;
    }
    if (detectFront().getObjectType()==IFrontSensorResult.Types.OpponentMainBot || detectFront().getObjectType()==IFrontSensorResult.Types.OpponentSecondaryBot) {
      turnRightTask=false;
      fallBackCoveringFireTask=true;
      endMoveTask=300;
      moveBack();
      distance=Parameters.teamAMainBotSpeed;
      schemeIndex=0;
      sendLogMessage("Enemy at 12 o'clock. Fall back covering fire for 30cm!");
      return;
    }
    if (detectFront().getObjectType()==IFrontSensorResult.Types.TeamSecondaryBot) {
      turnRightTask=false;
      avoidTeambot = true;
      endMoveTask=20;
      moveBack();
      distance=Parameters.teamAMainBotSpeed;
      schemeIndex=0;
      sendLogMessage("Team bot at 12 o'clock. Avoiding!");
      return;
    }

    if (detectFront().getObjectType() == IFrontSensorResult.Types.Wreck && !avoidingWreck) {
      sendLogMessage("Wreck bot detected at 12 o'clock. Starting avoidance procedure.");
      avoidingWreck = true;
      turnDirectionForAvoiding = Parameters.Direction.RIGHT;  // or LEFT, based on your preference or logic
      endMoveTask = 20;  // adjust as needed
      distance = 0;
      return;
    }

    if (avoidingWreck) {
      sendLogMessage("In the process of avoiding the Wreck.");

      if (distance < endMoveTask) {
        sendLogMessage("Backing away from the Wreck.");
        moveBack();
        distance += Parameters.teamAMainBotSpeed;
        return;
      }

      if (!isSameDirection(myGetHeading(), Parameters.WEST)) {
        sendLogMessage("Turning to avoid the Wreck.");
        stepTurn(turnDirectionForAvoiding);
        return;
      } else if (turnDirectionForAvoiding == Parameters.Direction.RIGHT && isSameDirection(myGetHeading(), Parameters.SOUTH)) {
        sendLogMessage("Finished turning right. Now moving forward.");
        distance = 0;
      } else if (turnDirectionForAvoiding == Parameters.Direction.LEFT && isSameDirection(myGetHeading(), Parameters.NORTH)) {
        sendLogMessage("Finished turning left. Now moving forward.");
        distance = 0;
      }

      if (detectFront().getObjectType() != IFrontSensorResult.Types.Wreck && distance < endMoveTask) {
        sendLogMessage("No more Wreck detected in front. Moving forward.");
        move();
        distance += Parameters.teamAMainBotSpeed;
        return;
      } else if (detectFront().getObjectType() == IFrontSensorResult.Types.Wreck) {
        sendLogMessage("Still detecting Wreck in front. Turning again.");
        stepTurn(turnDirectionForAvoiding);
        return;
      } else {
        sendLogMessage("Finished avoiding the Wreck.");
        avoidingWreck = false;
      }

      if (distance < endMoveTask) {
        sendLogMessage("Moving forward after avoiding the Wreck.");
        move();
        distance += Parameters.teamAMainBotSpeed;
        return;
      } else {
        sendLogMessage("Finished avoiding the Wreck.");
        avoidingWreck = false;
      }
    }

    move(); //And what to do when blind blocked?
    sendLogMessage("Moving a head. Waza!");
    return;
  }
  private boolean isHeading(double dir){
    return Math.abs(Math.sin(getHeading()-dir))<HEADINGPRECISION;
  }

  private double myGetHeading(){
    double result = getHeading();
    while(result<0) result+=2*Math.PI;
    while(result>2*Math.PI) result-=2*Math.PI;
    return result;
  }
  private boolean isSameDirection(double dir1, double dir2){
    return Math.abs(dir1-dir2)<ANGLEPRECISION;
  }
}
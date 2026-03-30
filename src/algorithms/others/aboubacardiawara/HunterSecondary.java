package algorithms.others.aboubacardiawara;

import algorithms.others.aboubacardiawara.brains.core.SecondaryBotBaseBrain;
import algorithms.others.aboubacardiawara.statemachine.impl.State;
import algorithms.others.aboubacardiawara.statemachine.interfaces.IState;
import characteristics.IRadarResult;
import characteristics.Parameters;

public class HunterSecondary extends SecondaryBotBaseBrain {

  Boolean collisionDetected = false;
  protected double targetHeading;
  private boolean detected;
  private int moveacount = 200;

  @Override
  protected IState buildStateMachine() {
    IState initState = new State();
    initState.setDescription("Init State");
    IState MoveState = new State();
    MoveState.setDescription("Move State");
    IState TurnTowardEnemies = new State();
    TurnTowardEnemies.setDescription("Turn Right");
    IState DetecState = new State();
    DetecState.setDescription("Detect State");
    IState STMoveWest = new State();
    STMoveWest.setDescription("Move West");
    IState STTurnWest = new State();
    STTurnWest.setDescription("Turn West");
    IState STTurnEast = new State();
    STTurnEast.setDescription("Turn East");
    IState STTurnNorth = new State();
    STTurnNorth.setDescription("Turn North");
    IState STTurnSouth = new State();
    STTurnSouth.setDescription("Turn South");

    double initStateAngleTarget;

    if (this.leftSide) {
      if (this.currentRobot == Robots.SRUP) {
        initStateAngleTarget = this.getHeading() - (Math.PI / 3);
      } else {
        initStateAngleTarget = this.getHeading() + (Math.PI / 3);
      }
    } else {
      if (this.currentRobot == Robots.SRUP) {
        initStateAngleTarget = this.getHeading() + (Math.PI / 3);
      } else {
        initStateAngleTarget = this.getHeading() - (Math.PI / 3);
      }
    }

    initState.addNext(MoveState, () -> isSameDirection(getHeading(), initStateAngleTarget));
    initState.setStateAction(() -> {
      if (leftSide) {
        if (this.currentRobot == Robots.SRUP) {
          turnLeft();
        } else {
          turnRight();
        }
      } else {
        if (this.currentRobot == Robots.SRUP) {
          turnRight();
        } else {
          turnLeft();
        }
      }
    });

    MoveState.addNext(TurnTowardEnemies, () -> moveacount == 0);
    MoveState.setStateAction(() -> {
      move();
      moveacount--;
    });

    double turnTowardEnemiesTargetDirection;
    if (this.leftSide) {
      turnTowardEnemiesTargetDirection = Parameters.EAST;
    } else {
      turnTowardEnemiesTargetDirection = Parameters.WEST;
    }
    TurnTowardEnemies.addNext(DetecState, () -> isSameDirection(getHeading(), turnTowardEnemiesTargetDirection));
    TurnTowardEnemies.setStateAction(() -> {
      if (this.leftSide) {
        if (this.currentRobot == Robots.SRUP) {
          turnRight();
        } else {
          turnLeft();
        }
      } else {
        if (this.currentRobot == Robots.SRUP) {
          turnLeft();
        } else {
          turnRight();
        }
      }
    });

    // Opponent broadcast is handled centrally in SecondaryBotBaseBrain.beforeEachStep.
    // Here we only decide whether to move or evade.
    DetecState.setStateAction(() -> {
      detected = false;
      boolean bulletSeen = false;
      for (IRadarResult radarResult : detectRadar()) {
        if (isOpponentBot(radarResult) && isNotDead(radarResult)) {
          detected = true;
        }
        if (radarResult.getObjectType() == IRadarResult.Types.BULLET) {
          bulletSeen = true;
        }
      }
      // FIX: actually evade bullets; only move forward when the path is clear
      if (bulletSeen) {
        moveBack();
      } else if (!detected) {
        move();
      }
    });

    // Wall avoidance: perimeter loop strategy
    if (this.leftSide) {
      DetecState.addNext(STTurnNorth, () -> detectWall() && isSameDirection(getHeading(), Parameters.EAST));
      DetecState.addNext(STTurnNorth,
          () -> detectWall() && isSameDirection(getHeading(), Parameters.NORTH) && currentRobot != Robots.SRUP);
      DetecState.addNext(STTurnWest,
          () -> detectWall() && isSameDirection(getHeading(), Parameters.NORTH) && currentRobot == Robots.SRUP);
      DetecState.addNext(STTurnEast, () -> detectWall() && isSameDirection(getHeading(), Parameters.SOUTH));
      DetecState.addNext(STTurnEast, () -> detectWall() && isSameDirection(getHeading(), Parameters.WEST));
      STTurnNorth.addNext(DetecState, () -> isSameDirection(getHeading(), Parameters.NORTH));
      STTurnNorth.setStateAction(() -> turnLeft());
      STTurnWest.addNext(DetecState, () -> isSameDirection(getHeading(), Parameters.WEST));
      STTurnWest.setStateAction(() -> turnLeft());
      STTurnEast.addNext(DetecState, () -> isSameDirection(getHeading(), Parameters.EAST));
      STTurnEast.setStateAction(() -> turnRight());
    } else {
      DetecState.addNext(STTurnNorth, () -> detectWall() && isSameDirection(getHeading(), Parameters.EAST));
      // FIX: removed duplicate SOUTH transition that was unreachable (shadow of the two lines above)
      DetecState.addNext(STTurnSouth,
          () -> detectWall() && isSameDirection(getHeading(), Parameters.SOUTH) && currentRobot == Robots.SRUP);
      DetecState.addNext(STTurnEast,
          () -> detectWall() && isSameDirection(getHeading(), Parameters.SOUTH) && currentRobot != Robots.SRUP);
      DetecState.addNext(STTurnSouth, () -> detectWall() && isSameDirection(getHeading(), Parameters.WEST));
      STTurnSouth.addNext(DetecState, () -> isSameDirection(getHeading(), Parameters.SOUTH));
      STTurnSouth.setStateAction(() -> turnLeft());
      STTurnWest.addNext(DetecState, () -> isSameDirection(getHeading(), Parameters.WEST));
      STTurnWest.setStateAction(() -> turnLeft());
      STTurnEast.addNext(DetecState, () -> isSameDirection(getHeading(), Parameters.EAST));
      STTurnEast.setStateAction(() -> turnRight());
      STTurnNorth.addNext(DetecState, () -> isSameDirection(getHeading(), Parameters.WEST));
      STTurnNorth.setStateAction(() -> turnRight());
    }

    return initState;
  }

  @Override
  protected void beforeEachStep() {
    // super (SecondaryBotBaseBrain) already calls sendOpponentPositions
    super.beforeEachStep();
    logRobotPosition();
  }

  @Override
  protected void afterEachStep() {
    super.afterEachStep();
  }

}

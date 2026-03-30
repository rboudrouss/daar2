/* ******************************************************
 * DAAR Project 4 - Simovies Swarm Algorithms
 * Team A Secondary Bot - SYNCHRONIZED FLANKER
 * Fast flanking with synchronized fire
 * ******************************************************/
package algorithms.others.tsuru77;

import java.util.ArrayList;
import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;

public class TeamASecondaryBotBOUVARD extends Brain {
    //---VARIABLES---//
    private boolean turnTask, turnRight, moveTask;
    private double endTaskDirection, targetToShoot;
    private int endTaskCounter, fireLatency;
    private boolean firstMove;
    private boolean hasTarget;
    private int robotId;

    //---CONSTRUCTORS---//
    public TeamASecondaryBotBOUVARD() { super(); }

    //---METHODS---//
    public void activate() {
        turnTask = true;
        moveTask = false;
        firstMove = true;
        fireLatency = 0;
        hasTarget = false;
        robotId = (Math.random() > 0.5) ? 1 : 2;
        
        // Flanquer en diagonale
        double flankAngle = (robotId == 1) ? -0.35 : 0.35;
        endTaskDirection = Parameters.EAST + flankAngle;
        double ref = endTaskDirection - getHeading();
        if (ref < 0) ref += Math.PI * 2;
        turnRight = (ref > 0 && ref < Math.PI);
        targetToShoot = Parameters.EAST;
        
        if (turnRight) stepTurn(Parameters.Direction.RIGHT);
        else stepTurn(Parameters.Direction.LEFT);
        
        sendLogMessage("Sync Flanker Ready!");
    }
    
    public void step() {
        if (getHealth() <= 0) return;
        
        if (fireLatency > 0) fireLatency--;
        
        ArrayList<IRadarResult> radarResults = detectRadar();
        
        // ===== ÉTAPE 1: Lire les messages - PRIORITÉ ABSOLUE =====
        ArrayList<String> messages = fetchAllMessages();
        for (String msg : messages) {
            if (msg.startsWith("FIRE:")) {
                try {
                    String[] parts = msg.split(":");
                    targetToShoot = Double.parseDouble(parts[1]);
                    hasTarget = true;
                } catch (Exception e) {}
            }
        }
        
        // ===== ÉTAPE 2: Détecter les ennemis =====
        // Secondary bots ciblent en priorité les Secondary ennemis
        for (IRadarResult r : radarResults) {
            if (r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
                targetToShoot = r.getObjectDirection();
                hasTarget = true;
                broadcast("FIRE:" + targetToShoot);
                break;
            }
        }
        if (!hasTarget) {
            for (IRadarResult r : radarResults) {
                if (r.getObjectType() == IRadarResult.Types.OpponentMainBot) {
                    targetToShoot = r.getObjectDirection();
                    hasTarget = true;
                    broadcast("FIRE:" + targetToShoot);
                    break;
                }
            }
        }
        
        // ===== ÉTAPE 3: TIRER SI CIBLE =====
        if (hasTarget && fireLatency <= 0) {
            fire(targetToShoot);
            fireLatency = 21;
            hasTarget = false;
            return;
        }
        
        // ===== ÉTAPE 4: Se déplacer =====
        if (turnTask) {
            if (isHeading(endTaskDirection)) {
                if (firstMove) {
                    firstMove = false;
                    turnTask = false;
                    moveTask = true;
                    endTaskCounter = 400;
                    move();
                    return;
                }
                turnTask = false;
                moveTask = true;
                endTaskCounter = 150;
                move();
            } else {
                if (turnRight) stepTurn(Parameters.Direction.RIGHT);
                else stepTurn(Parameters.Direction.LEFT);
            }
            return;
        }
        
        if (moveTask) {
            IFrontSensorResult front = detectFront();
            
            if (front.getObjectType() == IFrontSensorResult.Types.WALL ||
                front.getObjectType() == IFrontSensorResult.Types.Wreck ||
                front.getObjectType() == IFrontSensorResult.Types.TeamMainBot ||
                front.getObjectType() == IFrontSensorResult.Types.TeamSecondaryBot) {
                turnTask = true;
                moveTask = false;
                endTaskDirection = getHeading() + (Math.random() > 0.5 ? 1 : -1) * Math.PI * 0.5;
                double ref = endTaskDirection - getHeading();
                if (ref < 0) ref += Math.PI * 2;
                turnRight = (ref > 0 && ref < Math.PI);
                if (turnRight) stepTurn(Parameters.Direction.RIGHT);
                else stepTurn(Parameters.Direction.LEFT);
                return;
            }
            
            if (endTaskCounter < 0) {
                turnTask = true;
                moveTask = false;
                endTaskDirection = Parameters.EAST + (Math.random() - 0.5) * 0.5;
                double ref = endTaskDirection - getHeading();
                if (ref < 0) ref += Math.PI * 2;
                turnRight = (ref > 0 && ref < Math.PI);
                if (turnRight) stepTurn(Parameters.Direction.RIGHT);
                else stepTurn(Parameters.Direction.LEFT);
            } else {
                endTaskCounter--;
                move();
            }
            return;
        }
        
        move();
    }
    
    private boolean isHeading(double dir) {
        return Math.abs(Math.sin(getHeading() - dir)) < Parameters.teamASecondaryBotStepTurnAngle;
    }
}
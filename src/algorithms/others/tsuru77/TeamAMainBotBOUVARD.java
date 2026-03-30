/* ******************************************************
 * DAAR Project 4 - Simovies Swarm Algorithms
 * Team A Main Bot - SYNCHRONIZED ATTACK
 * All bots fire at the same target when one spots enemy
 * ******************************************************/
package algorithms.others.tsuru77;


import java.util.ArrayList;
import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;

public class TeamAMainBotBOUVARD extends Brain {
    //---VARIABLES---//
    private boolean turnTask, turnRight, moveTask;
    private double endTaskDirection, targetToShoot;
    private int endTaskCounter, fireLatency;
    private boolean firstMove;
    private boolean hasTarget;  // True when we have a valid target to shoot

    //---CONSTRUCTORS---//
    public TeamAMainBotBOUVARD() { super(); }

    //---METHODS---//
    public void activate() {
        turnTask = true;
        moveTask = false;
        firstMove = true;
        fireLatency = 0;
        hasTarget = false;
        
        // Tous vers l'EST
        endTaskDirection = Parameters.EAST;
        turnRight = true;
        targetToShoot = Parameters.EAST;
        
        stepTurn(Parameters.Direction.RIGHT);
        sendLogMessage("Sync Attack Ready!");
    }
    
    public void step() {
        if (getHealth() <= 0) return;
        
        if (fireLatency > 0) fireLatency--;
        
        ArrayList<IRadarResult> radarResults = detectRadar();
        
        // ===== ÉTAPE 1: Lire les messages - PRIORITÉ ABSOLUE =====
        // Si un coéquipier a vu un ennemi, on tire dessus immédiatement !
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
        
        // ===== ÉTAPE 2: Détecter les ennemis nous-même =====
        for (IRadarResult r : radarResults) {
            if (r.getObjectType() == IRadarResult.Types.OpponentMainBot) {
                targetToShoot = r.getObjectDirection();
                hasTarget = true;
                // Dire à TOUT le monde de tirer sur cette cible !
                broadcast("FIRE:" + targetToShoot);
                break;
            }
        }
        if (!hasTarget) {
            for (IRadarResult r : radarResults) {
                if (r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
                    targetToShoot = r.getObjectDirection();
                    hasTarget = true;
                    broadcast("FIRE:" + targetToShoot);
                    break;
                }
            }
        }
        
        // ===== ÉTAPE 3: TIRER SI ON A UNE CIBLE =====
        if (hasTarget && fireLatency <= 0) {
            fire(targetToShoot);
            fireLatency = 21;
            hasTarget = false;  // Reset pour prochain step
            return;  // Tirer est prioritaire !
        }
        
        // ===== ÉTAPE 4: Se déplacer vers l'ennemi =====
        if (turnTask) {
            if (isHeading(endTaskDirection)) {
                if (firstMove) {
                    firstMove = false;
                    turnTask = false;
                    moveTask = true;
                    endTaskCounter = 600;
                    move();
                    return;
                }
                turnTask = false;
                moveTask = true;
                endTaskCounter = 200;
                move();
            } else {
                if (turnRight) stepTurn(Parameters.Direction.RIGHT);
                else stepTurn(Parameters.Direction.LEFT);
            }
            return;
        }
        
        if (moveTask) {
            IFrontSensorResult front = detectFront();
            
            // Éviter obstacles
            if (front.getObjectType() == IFrontSensorResult.Types.WALL ||
                front.getObjectType() == IFrontSensorResult.Types.Wreck ||
                front.getObjectType() == IFrontSensorResult.Types.TeamMainBot ||
                front.getObjectType() == IFrontSensorResult.Types.TeamSecondaryBot) {
                turnTask = true;
                moveTask = false;
                endTaskDirection = getHeading() + (Math.random() > 0.5 ? 1 : -1) * Math.PI * 0.4;
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
                // Retourner vers l'EST
                endTaskDirection = Parameters.EAST + (Math.random() - 0.5) * 0.3;
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
        return Math.abs(Math.sin(getHeading() - dir)) < Parameters.teamAMainBotStepTurnAngle;
    }
}
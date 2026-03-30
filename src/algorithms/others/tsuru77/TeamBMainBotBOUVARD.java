/* ******************************************************
 * DAAR Project 4 - Simovies Swarm Algorithms
 * Team B Main Bot - Strategy: Defensive Formation
 * ******************************************************/
package algorithms.others.tsuru77;

import java.util.ArrayList;
import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;

/**
 * TeamBMainBot - Robot principal de l'équipe B
 * 
 * Stratégie "Defensive Formation":
 * - Les 3 robots principaux forment un triangle défensif
 * - Restent en position et tirent sur les ennemis qui approchent
 * - Se regroupent si un coéquipier est détruit
 */
public class TeamBMainBotBOUVARD extends Brain {
    
    //=== CONSTANTES ===//
    private static final double FIELD_WIDTH = 3000;
    private static final double FIELD_HEIGHT = 2000;
    private static final double DEFENSE_LINE_X = 2400;  // Ligne de défense
    private static final double FIRING_RANGE = 800;
    private static final double SAFE_DISTANCE = 100;
    
    //=== ÉTATS DU GRAFCET ===//
    private enum State {
        INIT,       // Initialisation
        POSITION,   // Se mettre en position
        HOLD,       // Tenir la position
        FIRE,       // Tirer sur l'ennemi
        REGROUP     // Se regrouper
    }
    private State currentState = State.INIT;
    
    //=== VARIABLES ===//
    private double myX, myY;
    private double targetX, targetY;
    private double targetDirection;
    private int moveCounter;
    private int fireLatency;
    private boolean turning;
    private boolean turnRight;
    private int robotId;  // 1, 2, ou 3
    private double watchDirection;  // Direction de surveillance
    private double lastEnemyDirection;
    private int fireCounter;
    
    //=== CONSTRUCTEUR ===//
    public TeamBMainBotBOUVARD() { 
        super(); 
    }
    
    @Override
    public void activate() {
        fireLatency = 0;
        turning = false;
        fireCounter = 0;
        lastEnemyDirection = Parameters.WEST;
        
        // Déterminer l'ID du robot (1, 2 ou 3) basé sur la position Y
        double initY = getInitialY();
        if (initY < 900) robotId = 1;
        else if (initY > 1100) robotId = 3;
        else robotId = 2;
        
        myX = 2800;
        myY = initY;
        
        // Position cible en formation triangulaire
        switch (robotId) {
            case 1:
                targetX = DEFENSE_LINE_X;
                targetY = 600;
                watchDirection = Parameters.WEST - 0.3;  // Surveiller Nord-Ouest
                break;
            case 2:
                targetX = DEFENSE_LINE_X - 150;
                targetY = 1000;
                watchDirection = Parameters.WEST;  // Surveiller Ouest
                break;
            case 3:
                targetX = DEFENSE_LINE_X;
                targetY = 1400;
                watchDirection = Parameters.WEST + 0.3;  // Surveiller Sud-Ouest
                break;
            default:
                targetX = DEFENSE_LINE_X;
                targetY = 1000;
                watchDirection = Parameters.WEST;
        }
        
        // Calculer la direction initiale
        targetDirection = Math.atan2(targetY - myY, targetX - myX);
        turning = true;
        turnRight = normalizeAngle(targetDirection - getHeading()) > 0;
        
        currentState = State.POSITION;
        moveCounter = 0;
        
        sendLogMessage("Team B Defender #" + robotId + " - Defensive Formation");
    }
    
    /**
     * Estime la position Y initiale basée sur le robot ID
     */
    private double getInitialY() {
        // Approximation basée sur les paramètres
        double heading = getHeading();
        // Utiliser un petit mouvement pour détecter la position relative
        return 1000; // Valeur par défaut, sera raffinée
    }
    
    @Override
    public void step() {
        if (getHealth() <= 0) {
            sendLogMessage("Defender #" + robotId + " down!");
            broadcast("DEFENDER_DOWN:" + robotId);
            return;
        }
        
        if (fireLatency > 0) fireLatency--;
        
        // Vérifier les messages (détection de coéquipiers morts)
        checkTeamMessages();
        
        // Détection radar
        ArrayList<IRadarResult> radarResults = detectRadar();
        IRadarResult enemy = findNearestEnemy(radarResults);
        
        // Si ennemi à portée
        if (enemy != null) {
            lastEnemyDirection = enemy.getObjectDirection();
            if (currentState == State.HOLD) {
                currentState = State.FIRE;
            }
            // Informer les coéquipiers
            broadcast("ENEMY_SPOTTED:" + enemy.getObjectDirection() + ":" + enemy.getObjectDistance());
        }
        
        // Machine à états
        switch (currentState) {
            case INIT:
            case POSITION:
                statePosition();
                break;
            case HOLD:
                stateHold(radarResults);
                break;
            case FIRE:
                stateFire(enemy);
                break;
            case REGROUP:
                stateRegroup();
                break;
        }
        
        updateOdometry();
    }
    
    //=================================================//
    //=== ÉTATS DU GRAFCET                          ===//
    //=================================================//
    
    /**
     * État POSITION: Se mettre en position défensive
     */
    private void statePosition() {
        IFrontSensorResult front = detectFront();
        
        if (front.getObjectType() == IFrontSensorResult.Types.WALL ||
            front.getObjectType() == IFrontSensorResult.Types.TeamMainBot) {
            // Stop, on est arrivé ou bloqué
            currentState = State.HOLD;
            targetDirection = watchDirection;
            turning = true;
            turnRight = normalizeAngle(targetDirection - getHeading()) > 0;
            sendLogMessage("Defender #" + robotId + " in position");
            return;
        }
        
        if (turning) {
            if (isHeading(targetDirection)) {
                turning = false;
                moveCounter = 400;
            } else {
                if (turnRight) stepTurn(Parameters.Direction.RIGHT);
                else stepTurn(Parameters.Direction.LEFT);
            }
            return;
        }
        
        if (moveCounter > 0) {
            moveCounter--;
            move();
        } else {
            // Arrivé en position
            currentState = State.HOLD;
            targetDirection = watchDirection;
            turning = true;
            turnRight = normalizeAngle(targetDirection - getHeading()) > 0;
            sendLogMessage("Defender #" + robotId + " in position");
        }
    }
    
    /**
     * État HOLD: Tenir la position et surveiller
     */
    private void stateHold(ArrayList<IRadarResult> radarResults) {
        // Se tourner vers la direction de surveillance
        if (turning) {
            if (isHeading(watchDirection)) {
                turning = false;
            } else {
                if (turnRight) stepTurn(Parameters.Direction.RIGHT);
                else stepTurn(Parameters.Direction.LEFT);
            }
            return;
        }
        
        // Tir de suppression périodique vers l'ouest
        if (fireLatency <= 0 && Math.random() < 0.02) {
            // Tir aléatoire vers l'ouest
            double suppressionDir = Parameters.WEST + (Math.random() - 0.5) * 0.5;
            fire(suppressionDir);
            fireLatency = Parameters.bulletFiringLatency + 10;
        }
        
        // Petit mouvement pour éviter d'être une cible facile
        if (Math.random() < 0.01) {
            if (Math.random() > 0.5) move();
            else moveBack();
        }
    }
    
    /**
     * État FIRE: Tirer sur l'ennemi détecté
     */
    private void stateFire(IRadarResult enemy) {
        fireCounter++;
        
        if (enemy != null) {
            double enemyDir = enemy.getObjectDirection();
            double enemyDist = enemy.getObjectDistance();
            
            // Tirer!
            if (fireLatency <= 0) {
                // Ajouter une légère prédiction
                double leadAngle = 0;
                if (enemyDist > 500) {
                    leadAngle = (Math.random() - 0.5) * 0.1;
                }
                fire(enemyDir + leadAngle);
                fireLatency = Parameters.bulletFiringLatency + 1;
            }
            
            // Tourner pour viser
            if (!isHeading(enemyDir)) {
                double diff = normalizeAngle(enemyDir - getHeading());
                if (diff > 0) stepTurn(Parameters.Direction.RIGHT);
                else stepTurn(Parameters.Direction.LEFT);
            }
            
            // Si l'ennemi est trop proche, reculer légèrement
            if (enemyDist < 300) {
                moveBack();
            }
        } else {
            // Plus d'ennemi visible
            if (fireCounter > 50) {
                currentState = State.HOLD;
                turning = true;
                targetDirection = watchDirection;
                turnRight = normalizeAngle(targetDirection - getHeading()) > 0;
                fireCounter = 0;
                sendLogMessage("Enemy cleared - Resuming watch");
            } else {
                // Continuer à tirer dans la dernière direction connue
                if (fireLatency <= 0) {
                    fire(lastEnemyDirection);
                    fireLatency = Parameters.bulletFiringLatency + 5;
                }
            }
        }
    }
    
    /**
     * État REGROUP: Se regrouper après la perte d'un coéquipier
     */
    private void stateRegroup() {
        // Nouvelle position de regroupement (plus serrée)
        double newTargetX = DEFENSE_LINE_X - 100;
        double newTargetY = 1000;  // Vers le centre
        
        targetDirection = Math.atan2(newTargetY - myY, newTargetX - myX);
        
        if (turning) {
            if (isHeading(targetDirection)) {
                turning = false;
                moveCounter = 200;
            } else {
                if (turnRight) stepTurn(Parameters.Direction.RIGHT);
                else stepTurn(Parameters.Direction.LEFT);
            }
            return;
        }
        
        if (moveCounter > 0) {
            moveCounter--;
            move();
        } else {
            currentState = State.HOLD;
            watchDirection = Parameters.WEST;
            turning = true;
            targetDirection = watchDirection;
            turnRight = normalizeAngle(targetDirection - getHeading()) > 0;
        }
    }
    
    //=================================================//
    //=== MÉTHODES UTILITAIRES                      ===//
    //=================================================//
    
    private IRadarResult findNearestEnemy(ArrayList<IRadarResult> radarResults) {
        IRadarResult nearest = null;
        double minDist = Double.MAX_VALUE;
        
        for (IRadarResult r : radarResults) {
            if (r.getObjectType() == IRadarResult.Types.OpponentMainBot ||
                r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
                if (r.getObjectDistance() < minDist) {
                    minDist = r.getObjectDistance();
                    nearest = r;
                }
            }
        }
        return nearest;
    }
    
    private void checkTeamMessages() {
        ArrayList<String> messages = fetchAllMessages();
        for (String msg : messages) {
            if (msg.startsWith("DEFENDER_DOWN:")) {
                // Un coéquipier est mort
                if (currentState != State.REGROUP) {
                    currentState = State.REGROUP;
                    turning = true;
                    turnRight = true;
                    sendLogMessage("Teammate down - Regrouping");
                }
            } else if (msg.startsWith("ENEMY_SPOTTED:") && currentState == State.HOLD) {
                // Un coéquipier a repéré un ennemi
                String[] parts = msg.split(":");
                if (parts.length >= 2) {
                    try {
                        lastEnemyDirection = Double.parseDouble(parts[1]);
                    } catch (NumberFormatException e) {
                        // Ignorer
                    }
                }
            }
        }
    }
    
    private int countTeammates(ArrayList<IRadarResult> radarResults) {
        int count = 0;
        for (IRadarResult r : radarResults) {
            if (r.getObjectType() == IRadarResult.Types.TeamMainBot ||
                r.getObjectType() == IRadarResult.Types.TeamSecondaryBot) {
                count++;
            }
        }
        return count;
    }
    
    private boolean isHeading(double dir) {
        return Math.abs(normalizeAngle(getHeading() - dir)) < Parameters.teamBMainBotStepTurnAngle * 2;
    }
    
    private double normalizeAngle(double angle) {
        while (angle > Math.PI) angle -= 2 * Math.PI;
        while (angle < -Math.PI) angle += 2 * Math.PI;
        return angle;
    }
    
    private void updateOdometry() {
        double heading = getHeading();
        myX += Math.cos(heading) * Parameters.teamBMainBotSpeed;
        myY += Math.sin(heading) * Parameters.teamBMainBotSpeed;
        myX = Math.max(SAFE_DISTANCE, Math.min(FIELD_WIDTH - SAFE_DISTANCE, myX));
        myY = Math.max(SAFE_DISTANCE, Math.min(FIELD_HEIGHT - SAFE_DISTANCE, myY));
    }
}
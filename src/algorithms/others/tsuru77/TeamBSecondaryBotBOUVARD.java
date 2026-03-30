package algorithms.others.tsuru77;

import java.util.ArrayList;
import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;

/**
 * TeamBSecondaryBot - Robot secondaire de l'équipe B
 * 
 * Stratégie "Perimeter Watch":
 * - Patrouille autour de la position défensive
 * - Détecte les ennemis en approche
 * - Alerte l'équipe et tire sur les intrus
 */
public class TeamBSecondaryBotBOUVARD extends Brain {
    
    //=== CONSTANTES ===//
    private static final double FIELD_WIDTH = 3000;
    private static final double FIELD_HEIGHT = 2000;
    private static final double PATROL_CENTER_X = 2000;  // Centre de patrouille
    private static final double PATROL_CENTER_Y = 1000;
    private static final double PATROL_RADIUS = 400;
    private static final double SAFE_DISTANCE = 100;
    
    //=== ÉTATS DU GRAFCET ===//
    private enum State {
        INIT,       // Initialisation
        GO_PATROL,  // Aller vers la zone de patrouille
        CIRCLE,     // Faire des cercles
        ALERT,      // Alerter et attaquer
        EVADE       // Éviter
    }
    private State currentState = State.INIT;
    
    //=== VARIABLES ===//
    private double myX, myY;
    private double targetDirection;
    private int moveCounter;
    private int fireLatency;
    private boolean turning;
    private boolean turnRight;
    private int circlePhase;
    private double lastEnemyDirection;
    private double lastEnemyDistance;
    private int alertCounter;
    private int robotId;  // 1 ou 2
    
    //=== CONSTRUCTEUR ===//
    public TeamBSecondaryBotBOUVARD() { 
        super(); 
    }
    
    @Override
    public void activate() {
        fireLatency = 0;
        turning = false;
        circlePhase = 0;
        alertCounter = 0;
        lastEnemyDirection = Parameters.WEST;
        lastEnemyDistance = 1000;
        
        // Déterminer l'ID basé sur la position initiale Y
        double heading = getHeading();
        // Robot 1 patrouille le nord, Robot 2 patrouille le sud
        myX = 2500;
        if (Math.abs(heading - Parameters.WEST) < 0.1) {
            // Essayer de détecter via les autres robots
            myY = 1000;
            robotId = 1;
        } else {
            myY = 1000;
            robotId = 2;
        }
        
        // Aller vers le point de patrouille
        if (robotId == 1) {
            targetDirection = Parameters.WEST + Parameters.NORTH * 0.3;
        } else {
            targetDirection = Parameters.WEST + Parameters.SOUTH * 0.3;
        }
        
        turning = true;
        turnRight = normalizeAngle(targetDirection - getHeading()) > 0;
        
        currentState = State.GO_PATROL;
        moveCounter = 0;
        
        sendLogMessage("Team B Patrol #" + robotId + " - Perimeter Watch");
    }
    
    @Override
    public void step() {
        if (getHealth() <= 0) {
            sendLogMessage("Patrol #" + robotId + " down!");
            broadcast("PATROL_DOWN:" + robotId);
            return;
        }
        
        if (fireLatency > 0) fireLatency--;
        
        // Détection radar
        ArrayList<IRadarResult> radarResults = detectRadar();
        IRadarResult enemy = findNearestEnemy(radarResults);
        
        // Si ennemi détecté
        if (enemy != null) {
            lastEnemyDirection = enemy.getObjectDirection();
            lastEnemyDistance = enemy.getObjectDistance();
            
            // Alerter l'équipe
            broadcast("INTRUDER:" + lastEnemyDirection + ":" + lastEnemyDistance);
            
            if (currentState != State.ALERT) {
                currentState = State.ALERT;
                alertCounter = 0;
            }
        }
        
        // Machine à états
        switch (currentState) {
            case INIT:
            case GO_PATROL:
                stateGoPatrol();
                break;
            case CIRCLE:
                stateCircle();
                break;
            case ALERT:
                stateAlert(enemy);
                break;
            case EVADE:
                stateEvade();
                break;
        }
        
        updateOdometry();
    }
    
    //=================================================//
    //=== ÉTATS DU GRAFCET                          ===//
    //=================================================//
    
    /**
     * État GO_PATROL: Aller vers la zone de patrouille
     */
    private void stateGoPatrol() {
        IFrontSensorResult front = detectFront();
        
        if (front.getObjectType() == IFrontSensorResult.Types.WALL) {
            turning = true;
            targetDirection = getHeading() + Math.PI * 0.5;
            turnRight = true;
            return;
        }
        
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
            // Arrivé en zone de patrouille
            currentState = State.CIRCLE;
            circlePhase = 0;
            sendLogMessage("Patrol zone reached - Circling");
        }
    }
    
    /**
     * État CIRCLE: Patrouiller en cercle autour du point central
     */
    private void stateCircle() {
        IFrontSensorResult front = detectFront();
        
        // Éviter les murs et coéquipiers
        if (front.getObjectType() == IFrontSensorResult.Types.WALL ||
            front.getObjectType() == IFrontSensorResult.Types.TeamMainBot) {
            turning = true;
            targetDirection = getHeading() + Math.PI * 0.6;
            turnRight = true;
            return;
        }
        
        if (turning) {
            if (isHeading(targetDirection)) {
                turning = false;
                moveCounter = 80 + (int)(Math.random() * 40);
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
            // Changer de direction pour continuer le cercle
            turning = true;
            
            // Pattern circulaire
            double baseAngle;
            if (robotId == 1) {
                // Robot 1: cercle dans la moitié nord
                switch (circlePhase % 4) {
                    case 0: baseAngle = Parameters.WEST; break;
                    case 1: baseAngle = Parameters.NORTH; break;
                    case 2: baseAngle = Parameters.EAST; break;
                    case 3: baseAngle = Parameters.SOUTH + 0.3; break;
                    default: baseAngle = Parameters.WEST;
                }
            } else {
                // Robot 2: cercle dans la moitié sud
                switch (circlePhase % 4) {
                    case 0: baseAngle = Parameters.WEST; break;
                    case 1: baseAngle = Parameters.SOUTH; break;
                    case 2: baseAngle = Parameters.EAST; break;
                    case 3: baseAngle = Parameters.NORTH - 0.3; break;
                    default: baseAngle = Parameters.WEST;
                }
            }
            
            targetDirection = baseAngle + (Math.random() - 0.5) * 0.3;
            turnRight = normalizeAngle(targetDirection - getHeading()) > 0;
            circlePhase++;
        }
    }
    
    /**
     * État ALERT: Ennemi détecté - attaquer et signaler
     */
    private void stateAlert(IRadarResult enemy) {
        alertCounter++;
        
        if (enemy != null) {
            double enemyDir = enemy.getObjectDirection();
            double enemyDist = enemy.getObjectDistance();
            
            // Tirer!
            if (fireLatency <= 0) {
                fire(enemyDir);
                fireLatency = Parameters.bulletFiringLatency + 1;
            }
            
            // Stratégie de harcèlement: rester à distance moyenne
            if (enemyDist < 350) {
                // Trop proche, reculer
                moveBack();
            } else if (enemyDist > 550) {
                // Trop loin, se rapprocher
                if (!isHeading(enemyDir)) {
                    double diff = normalizeAngle(enemyDir - getHeading());
                    if (diff > 0) stepTurn(Parameters.Direction.RIGHT);
                    else stepTurn(Parameters.Direction.LEFT);
                } else {
                    move();
                }
            } else {
                // Bonne distance, cercler autour
                double orbitDir = enemyDir + Math.PI / 2 * (robotId == 1 ? 1 : -1);
                if (!isHeading(orbitDir)) {
                    double diff = normalizeAngle(orbitDir - getHeading());
                    if (diff > 0) stepTurn(Parameters.Direction.RIGHT);
                    else stepTurn(Parameters.Direction.LEFT);
                } else {
                    move();
                }
            }
            
            // Continuer à alerter
            broadcast("INTRUDER:" + enemyDir + ":" + enemyDist);
            
            // Vérifier si on doit évader (santé faible)
            if (getHealth() < 40) {
                currentState = State.EVADE;
                targetDirection = Parameters.EAST;  // Retour vers la base
                turning = true;
                turnRight = normalizeAngle(targetDirection - getHeading()) > 0;
                sendLogMessage("Taking damage - Evading!");
            }
        } else {
            // Ennemi perdu
            if (alertCounter > 200) {
                currentState = State.CIRCLE;
                circlePhase = 0;
                sendLogMessage("Intruder lost - Resuming patrol");
            } else {
                // Chercher dans la dernière direction
                if (!isHeading(lastEnemyDirection)) {
                    double diff = normalizeAngle(lastEnemyDirection - getHeading());
                    if (diff > 0) stepTurn(Parameters.Direction.RIGHT);
                    else stepTurn(Parameters.Direction.LEFT);
                } else {
                    move();
                }
            }
        }
    }
    
    /**
     * État EVADE: Évader en cas de danger
     */
    private void stateEvade() {
        IFrontSensorResult front = detectFront();
        
        if (front.getObjectType() == IFrontSensorResult.Types.WALL) {
            turning = true;
            targetDirection = getHeading() + Math.PI * 0.5;
            turnRight = true;
            return;
        }
        
        if (turning) {
            if (isHeading(targetDirection)) {
                turning = false;
            } else {
                if (turnRight) stepTurn(Parameters.Direction.RIGHT);
                else stepTurn(Parameters.Direction.LEFT);
            }
            return;
        }
        
        // Courir vers la zone sûre
        move();
        
        // Vérifier si on peut reprendre la patrouille
        if (myX > 2300 && getHealth() > 50) {
            currentState = State.CIRCLE;
            sendLogMessage("Safe - Resuming patrol");
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
    
    private boolean isHeading(double dir) {
        return Math.abs(normalizeAngle(getHeading() - dir)) < Parameters.teamBSecondaryBotStepTurnAngle * 2;
    }
    
    private double normalizeAngle(double angle) {
        while (angle > Math.PI) angle -= 2 * Math.PI;
        while (angle < -Math.PI) angle += 2 * Math.PI;
        return angle;
    }
    
    private void updateOdometry() {
        double heading = getHeading();
        myX += Math.cos(heading) * Parameters.teamBSecondaryBotSpeed;
        myY += Math.sin(heading) * Parameters.teamBSecondaryBotSpeed;
        myX = Math.max(SAFE_DISTANCE, Math.min(FIELD_WIDTH - SAFE_DISTANCE, myX));
        myY = Math.max(SAFE_DISTANCE, Math.min(FIELD_HEIGHT - SAFE_DISTANCE, myY));
    }
}
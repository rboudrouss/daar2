package algorithms;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;
import java.util.ArrayList;

/**
 * ScoutBot — bot secondaire : éclaireur + obstacle sacrificiel.
 *
 * Stratégie (exploite le radar 500u des secondaries vs 300u des mains) :
 *
 *  Phase 1 — ADVANCE :
 *    Se dirige vers le centre du terrain (~x=1500) en avançant tout droit.
 *    Portée radar 500u → détecte les ennemis bien avant les bots principaux.
 *    Broadcast les positions ennemies en continu aux MainBots.
 *
 *  Phase 2 — HOLD :
 *    Une fois au centre (après ~333 steps), s'arrête et scanne en rotation.
 *    Couverture maximale du terrain.
 *
 *  Phase 3 — SACRIFICE (HP ≤ 30) :
 *    S'arrête immédiatement. Son corps devient un obstacle permanent
 *    (exploitation du bug : les bots morts restent dans le monde).
 *    L'ennemi doit dépenser 10 tirs pour le tuer → 10 tirs de moins sur les mains.
 *
 * Note : les secondaries NE PEUVENT PAS tirer (hasRocket=false dans le moteur).
 */
public class ScoutBot extends Brain {

    private static final double PRECISION = 0.01;

    // Distance à parcourir pour atteindre le centre (1000u / speed 3 ≈ 333 steps)
    private static final int STEPS_TO_CENTER = 333;

    // Rotation radar : scan complet en ~200 steps (2π / stepTurnAngle ≈ 200)
    private static final double SCAN_STEP = 0.01 * Math.PI; // = stepTurnAngle

    private enum State { ADVANCE, HOLD_AND_SCAN, SACRIFICE }

    private State state = State.ADVANCE;
    private int stepCount = 0;

    // Évitement de mur
    private boolean escapingWall = false;
    private double wallEscapeDir = 0;

    // Rotation de scan radar quand immobile
    private boolean scanningLeft = false;
    private double scanAnchor = Double.NaN; // heading de référence pour le scan aller-retour

    public ScoutBot() {
        super();
    }

    @Override
    public void activate() {
        scanAnchor = getHeading(); // mémoriser la direction initiale pour le scan
    }

    @Override
    public void step() {
        stepCount++;

        // Toujours scanner et broadcaster les ennemis (priorité absolue)
        broadcastEnemies();

        // Transition vers SACRIFICE si HP critique
        if (getHealth() <= 30 && state != State.SACRIFICE) {
            state = State.SACRIFICE;
            sendLogMessage("Sacrificing at current position as obstacle.");
        }

        // Transition ADVANCE → HOLD une fois au centre
        if (state == State.ADVANCE && stepCount >= STEPS_TO_CENTER) {
            state = State.HOLD_AND_SCAN;
            scanAnchor = getHeading();
            sendLogMessage("Center reached. Holding and scanning.");
        }

        switch (state) {
            case ADVANCE:
                doAdvance();
                break;

            case HOLD_AND_SCAN:
                // Rotation radar aller-retour de ±90° autour de scanAnchor
                doScan();
                break;

            case SACRIFICE:
                // Ne rien faire : immobile = obstacle permanent max HP restants
                // Le corps bloquera les ennemis même après destruction
                sendLogMessage("Obstacle active. HP=" + (int) getHealth());
                break;
        }
    }

    /**
     * Avance vers le centre avec évitement de mur.
     */
    private void doAdvance() {
        if (escapingWall) {
            if (isHeading(wallEscapeDir)) {
                escapingWall = false;
                move();
            } else {
                stepTurn(Parameters.Direction.RIGHT);
            }
        } else {
            IFrontSensorResult front = detectFront();
            if (front.getObjectType() == IFrontSensorResult.Types.WALL
                    || front.getObjectType() == IFrontSensorResult.Types.Wreck) {
                wallEscapeDir = normalizeAngle(getHeading() + Parameters.RIGHTTURNFULLANGLE);
                escapingWall = true;
                stepTurn(Parameters.Direction.RIGHT);
            } else {
                move();
            }
        }
    }

    /**
     * Rotation radar en éventail (±90° autour de scanAnchor) pour maximiser la couverture.
     */
    private void doScan() {
        double leftLimit  = normalizeAngle(scanAnchor - Math.PI / 2);
        double rightLimit = normalizeAngle(scanAnchor + Math.PI / 2);

        if (!scanningLeft && isHeading(rightLimit)) {
            scanningLeft = true;
        } else if (scanningLeft && isHeading(leftLimit)) {
            scanningLeft = false;
        }

        stepTurn(scanningLeft ? Parameters.Direction.LEFT : Parameters.Direction.RIGHT);
    }

    /**
     * Scanne le radar et broadcast la cible la plus proche aux coéquipiers.
     *
     * Format : "E:dir:dist"
     *   - dir  : direction absolue (référentiel monde, identique pour tous les bots)
     *   - dist : distance en mm
     *
     * Les MainBots utilisent ce message pour cibler hors de leur portée radar (300u).
     * La portée radar du ScoutBot est de 500u → 65% de couverture supplémentaire.
     */
    private void broadcastEnemies() {
        IRadarResult closestEnemy = null;
        double minDist = Double.MAX_VALUE;

        for (IRadarResult r : detectRadar()) {
            boolean isEnemy = r.getObjectType() == IRadarResult.Types.OpponentMainBot
                           || r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot;
            if (isEnemy && r.getObjectDistance() < minDist) {
                minDist = r.getObjectDistance();
                closestEnemy = r;
            }
        }

        if (closestEnemy != null) {
            broadcast("E:" + closestEnemy.getObjectDirection() + ":" + closestEnemy.getObjectDistance());
            sendLogMessage("Enemy spotted at dist=" + (int) minDist);
        }
    }

    private boolean isHeading(double dir) {
        return Math.abs(Math.sin(getHeading() - dir)) < PRECISION;
    }

    private double normalizeAngle(double a) {
        while (a > Math.PI)  a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }
}

package algorithms;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;
import java.util.ArrayList;

/**
 * MainBot — bot principal avec tir prédictif et focus-fire coordonné.
 *
 * Stratégie :
 *  - Radar scan chaque step → trouve l'ennemi le plus proche
 *  - Tir prédictif : estime la position future de l'ennemi via vélocité angulaire
 *  - Broadcast la cible aux coéquipiers (mains + scouts)
 *  - Écoute les scouts (ScoutBot) pour cibler hors portée radar
 *  - Avance sur la cible pendant le cooldown de tir (= pression constante)
 *  - Évitement de mur simple (virage 90° droite)
 */
public class MainBot extends Brain {

    // Précision de visée en radians (sin < 0.01 ≈ 0.57°)
    private static final double AIM_PRECISION = 0.01;

    // Direction de tir courante (NaN = pas de cible)
    private double aimDir = Double.NaN;

    // Cooldown interne synchronisé avec le moteur (Brain.counter = 20 steps)
    private int fireCooldown = 0;

    // Dernier angle radar de la cible pour interpolation prédictive
    private double lastTargetDir = Double.NaN;

    // Données pour l'évitement de mur
    private boolean escapingWall = false;
    private double wallEscapeDir = 0;

    public MainBot() {
        super();
    }

    @Override
    public void activate() {
        // Rien à initialiser ici, variables déjà initialisées
    }

    @Override
    public void step() {
        // Décrémenter notre cooldown interne
        fireCooldown = Math.max(fireCooldown - 1, 0);

        // 1. Scanner le radar pour trouver un ennemi
        IRadarResult target = findClosestEnemy();

        if (target != null) {
            // Vider la mailbox (évite accumulation de messages obsolètes)
            fetchAllMessages();
            // Tir prédictif : ajuster la direction selon le mouvement estimé de la cible
            aimDir = predictAim(target);
            // Partager la cible avec les coéquipiers
            broadcast("E:" + target.getObjectDirection() + ":" + target.getObjectDistance());
        } else {
            // Pas de cible radar : écouter les scouts
            aimDir = getTargetFromBroadcast();
            if (Double.isNaN(aimDir)) {
                lastTargetDir = Double.NaN; // on a perdu la cible
            }
        }

        // 2. Exécuter l'action
        if (!Double.isNaN(aimDir)) {
            // On a une cible : viser et tirer
            escapingWall = false;
            if (isHeading(aimDir)) {
                if (fireCooldown == 0) {
                    // Tirer !
                    fire(aimDir);
                    fireCooldown = Parameters.bulletFiringLatency; // = 20
                } else {
                    // Cooldown actif mais déjà visé → avancer sur la cible
                    move();
                }
            } else {
                // Pas encore visé → tourner vers la cible
                turnToward(aimDir);
            }
        } else {
            // Aucune cible : avancer avec évitement de mur
            advanceSafely();
        }
    }

    /**
     * Trouve l'ennemi le plus proche dans le radar (portée 300u pour main bot).
     * Priorité aux OpponentSecondaryBot (moins de HP = kill plus rapide).
     */
    private IRadarResult findClosestEnemy() {
        IRadarResult closestMain = null;
        IRadarResult closestSecondary = null;
        double minMain = Double.MAX_VALUE;
        double minSecondary = Double.MAX_VALUE;

        for (IRadarResult r : detectRadar()) {
            if (r.getObjectType() == IRadarResult.Types.OpponentMainBot
                    && r.getObjectDistance() < minMain) {
                minMain = r.getObjectDistance();
                closestMain = r;
            } else if (r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot
                    && r.getObjectDistance() < minSecondary) {
                minSecondary = r.getObjectDistance();
                closestSecondary = r;
            }
        }
        // Secondary = 10 hits pour tuer (vs 30 pour main) → cible prioritaire si proche
        if (closestSecondary != null && minSecondary < minMain + 50) {
            return closestSecondary;
        }
        return closestMain != null ? closestMain : closestSecondary;
    }

    /**
     * Tir prédictif basé sur la vélocité angulaire observée entre deux steps.
     *
     * Principe : si la cible a tourné de Δθ depuis le dernier step,
     * elle continuera dans la même direction. On anticipe sur (d / v_balle) steps.
     *
     * Exploitation du bug du simulateur : getObjectDistance() est exact (pas bruité).
     */
    private double predictAim(IRadarResult target) {
        double currentDir = target.getObjectDirection();
        double predictedDir = currentDir;

        if (!Double.isNaN(lastTargetDir)) {
            double angularVelocity = normalizeAngle(currentDir - lastTargetDir);
            double stepsToImpact = target.getObjectDistance() / Parameters.bulletVelocity;
            predictedDir = normalizeAngle(currentDir + angularVelocity * stepsToImpact);
        }

        lastTargetDir = currentDir;
        return predictedDir;
    }

    /**
     * Récupère la direction d'une cible depuis les broadcasts des scouts.
     * Format attendu : "E:dir:dist" (émis par ScoutBot)
     * Retourne Double.NaN si aucun message valide.
     */
    private double getTargetFromBroadcast() {
        double bestDir = Double.NaN;
        double bestDist = Double.MAX_VALUE;

        for (String msg : fetchAllMessages()) {
            if (msg.startsWith("E:")) {
                String[] parts = msg.split(":");
                if (parts.length >= 3) {
                    try {
                        double dir = Double.parseDouble(parts[1]);
                        double dist = Double.parseDouble(parts[2]);
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestDir = dir;
                        }
                    } catch (NumberFormatException ignored) { }
                }
            }
        }
        return bestDir;
    }

    /**
     * Avance tout droit avec évitement de mur (virage 90° à droite).
     */
    private void advanceSafely() {
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
     * Tourne d'un step vers la direction cible (choix LEFT/RIGHT optimal).
     */
    private void turnToward(double dir) {
        double diff = normalizeAngle(dir - getHeading());
        stepTurn(diff > 0 ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
    }

    /**
     * Vérifie si le heading courant est aligné avec dir à AIM_PRECISION près.
     */
    private boolean isHeading(double dir) {
        return Math.abs(Math.sin(getHeading() - dir)) < AIM_PRECISION;
    }

    /**
     * Normalise un angle dans [-π, π].
     */
    private double normalizeAngle(double a) {
        while (a > Math.PI)  a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }
}

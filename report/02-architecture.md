# Architecture

## Hiérarchie des classes

Le simulateur expose une classe `Brain` que chaque robot doit étendre. `BaseBot` centralise la logique commune et délègue deux méthodes abstraites à ses sous-classes : `scanRadar()` et l'initialisation via `activate()`.

```{.mermaid caption="Hiérarchie des classes"}
classDiagram
    class Brain {
        +activate()
        +step()
        +broadcast(msg)
        +fetchAllMessages()
        +detectRadar()
        +fire(angle)
        +move() / moveBack()
        +stepTurn(dir)
    }
    class BaseBot {
        #botId : String
        #position : Position
        #state : State
        #allies : Map~String,BotState~
        #enemies : List~Enemy~
        #trackedBullets : List~Bullet~
        +moveToRdv(x, y)
        +evadeIncomingBullets() bool
        +startAvoidance()
        #scanRadar()
    }
    class MainBot {
        -currentTarget : Enemy
        -fireCount : int
        +selectTarget() Enemy
        +computeFiringAngle() double
    }
    class AssistBot {
        -rendezvousY : double
        -distanceSinceLastTurn : double
        +approachRdv()
    }
    Brain <|-- BaseBot
    BaseBot <|-- MainBot
    BaseBot <|-- AssistBot
```

## Machine à états finie

Chaque robot est gouverné par une machine à états finie à sept états définis dans `BaseBot.State`. Les transitions sont déclenchées par les résultats du radar ou l'arrivée de messages.

```{.mermaid caption="Machine à états finie (BaseBot)"}
stateDiagram-v2
    [*] --> FIRST_RDV : activate()
    FIRST_RDV --> MOVING : rendezvous reçu (POS scout)
    MOVING --> FIRE : ennemi détecté dans radar
    FIRE --> MOVING : cible perdue ou MAX_FIRE_COUNT atteint
    MOVING --> MOVING_BACK : obstacle en trajectoire
    MOVING_BACK --> TURNING_RIGHT : voie droite libre
    MOVING_BACK --> TURNING_LEFT : voie gauche libre
    TURNING_RIGHT --> MOVING : cap atteint
    TURNING_LEFT --> MOVING : cap atteint
    MOVING --> DEAD : PV = 0
    FIRE --> DEAD : PV = 0
```

L'état `FIRE` est prioritaire : dès qu'un ennemi est sélectionnable, `MainBot.step()` force la transition vers `FIRE` avant toute autre évaluation.

## Structures de données

`Utils.java` définit quatre classes utilisées comme conteneurs d'état.

**`Position`** stocke des coordonnées `(x, y)` en double et implémente `equals()` et `hashCode()` pour utilisation dans des collections.

**`Bullet`** représente un projectile détecté : position courante `(x, y)` et vitesse `(vx, vy)`. Le champ `hasVelocity` indique si la vitesse a déjà été calculée à partir de deux observations consécutives.

**`BotState`** maintient la position et le statut vivant/mort d'un allié. Elle est stockée dans la `Map<String, BotState> allies` indexée par identifiant de bot.

**`Enemy`** conserve un historique circulaire des trois dernières positions observées (`xHistory[0..2]`, `yHistory[0..2]`). La vitesse est calculée par différence finie : `speedX = xHistory[0] - xHistory[1]`. La méthode `predictPosition(t)` projette la position future en détectant d'abord une éventuelle oscillation (changement de signe de la vitesse entre deux pas consécutifs), auquel cas elle revient à la moyenne des deux dernières positions.

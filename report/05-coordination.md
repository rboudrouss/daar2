# Modèle acteur et coordination

## Le modèle acteur dans le simulateur

Chaque robot est un acteur au sens strict : il dispose d'un état privé opaque, ne partage aucune mémoire avec ses coéquipiers, et communique uniquement par messages. Le simulateur joue le rôle de boîte aux lettres : `broadcast(msg)` dépose un message dans la file de tous les bots de l'équipe, `fetchAllMessages()` vide la file du bot appelant.

Cette architecture élimine toute synchronisation par verrou. Deux robots peuvent lire et écrire simultanément sans risque de corruption, car le simulateur sérialise les appels par tick. La contrepartie est que la communication est asynchrone et non fiable dans le sens du modèle acteur : un message émis au tick $t$ n'est disponible qu'au tick $t+1$ au plus tôt.

## Protocole de messages

Cinq types de messages sont définis sous forme de chaînes brutes non typées :

| Type | Format | Émetteur | Usage |
|---|---|---|---|
| `POS` | `POS id x y heading` | Tous | Partager la position courante |
| `ENEMY` | `ENEMY dir dist type x y` | MainBot, AssistBot | Signaler un ennemi détecté |
| `WRECK` | `WRECK x y` | MainBot, AssistBot | Signaler une épave |
| `DEAD` | `DEAD id` | AssistBot | Annoncer sa propre destruction |
| `MOVING_BACK` | `MOVING_BACK id x y` | AssistBot | Signaler un ennemi trop proche |

`POS` est émis à chaque appel de `tryMove()`, ce qui garantit une mise à jour de la carte alliée à chaque déplacement effectif. Les MainBots utilisent les positions des scouts (`SBOT` en priorité, `NBOT` si SBOT est mort) comme waypoint de ralliement dans leur état `FIRST_RDV` et `MOVING`.

```{.mermaid caption="Flux de messages entre acteurs (un tick)"}
sequenceDiagram
    participant AB as AssistBot (acteur)
    participant SIM as Simulateur (boîte aux lettres)
    participant MB as MainBot (acteur)

    AB->>SIM: broadcast("ENEMY dir dist type x y")
    AB->>SIM: broadcast("POS s1 x y heading")
    Note over SIM: file commune de l'équipe
    MB->>SIM: fetchAllMessages()
    SIM-->>MB: ["ENEMY ...", "POS ..."]
    MB->>SIM: broadcast("POS 1 x y heading")
    AB->>SIM: fetchAllMessages()
    SIM-->>AB: ["POS 1 x y heading"]
```

## Comportement de l'AssistBot

L'AssistBot a un rôle de capteur mobile. Son cycle de vie se décompose en deux grandes phases.

**Phase d'approche (`isApproachingRdv = true`).** Le bot exécute une séquence en trois étapes sans lire les messages : rotation vers le nord (NBOT) ou le sud (SBOT), déplacement vertical jusqu'à `rendezvousY` (780 ou 1250 unités), puis rotation vers l'est ou l'ouest selon l'équipe. Cette initialisation est purement locale et ne dépend d'aucun message.

**Phase active.** Le bot se déplace horizontalement et diffuse les ennemis détectés. Il se fige (`isFrozen = true`) dans deux cas : obstacle en trajectoire ou ennemi MainBot adverse à moins de `4 × BOT_RADIUS = 200` unités. Quand il est gelé, il émet `MOVING_BACK` et cesse tout déplacement jusqu'au prochain tick radar. L'évasion de balles reste active dans cet état.

Un compteur `distanceSinceLastTurn` déclenche un évitement après 1500 unités parcourues (moitié de la largeur d'arène), ce qui force périodiquement le scout à changer de cap et couvrir une zone différente.

## Limites du modèle

L'implémentation présente plusieurs écarts par rapport à un modèle acteur complet :

- **Messages non typés.** Les messages sont des chaînes parsées manuellement par `split(" ")`. Une erreur de formatage provoque une `NumberFormatException` silencieuse.
- **Pas de garantie d'ordre.** `fetchAllMessages()` renvoie les messages dans l'ordre d'arrivée, mais rien ne garantit qu'un `DEAD` soit traité avant le `ENEMY` suivant du même bot.
- **Pas d'accusé de réception.** Un MainBot ne sait pas si son allié a bien reçu une position ennemie. La rediffusion systématique compense partiellement cette absence.
- **Identité auto-assignée.** Chaque bot détermine son propre identifiant au démarrage en scannant le radar (`determineIdentity()` pour MainBot, détection d'obstacle nord pour AssistBot). Ce mécanisme suppose une configuration initiale stable et échouerait si deux bots obtenaient le même identifiant.

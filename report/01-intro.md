# Introduction

## Contexte

Ce projet s'inscrit dans le cadre du cours DAAR 2026 et a pour objectif d'implémenter un système multi-agents de combat robotique selon le **modèle acteur**. Dans ce modèle, chaque agent est un processus isolé qui ne partage aucune mémoire avec ses voisins et ne communique que par envoi de messages asynchrones.

L'arène de jeu mesure 3000 × 2000 unités. Deux équipes s'y affrontent, chacune composée de cinq robots. Le simulateur fournit à chaque robot une interface de type `Brain` avec trois primitives de communication : `broadcast()` pour envoyer un message, `fetchAllMessages()` pour lire la boîte de réception, et `detectRadar()` pour observer l'environnement local.

## Organisation du code

Le paquet `algorithms.MyBots` contient quatre fichiers :

| Fichier | Rôle |
|---|---|
| `Utils.java` | Structures de données : `Position`, `Bullet`, `BotState`, `Enemy` |
| `BaseBot.java` | Classe abstraite : navigation, évitement, évasion de balles, communication |
| `MainBot.java` | Robot tireur (vitesse 1, PV 300) : détection, ciblage, tir |
| `AssistBot.java` | Robot éclaireur (vitesse 3, PV 100) : reconnaissance, relai d'information |

## Composition d'une équipe

Chaque équipe aligne cinq robots répartis en deux rôles :

- **3 MainBots** (MAIN1, MAIN2, MAIN3) positionnés en colonne centrale, chargés de tirer.
- **2 AssistBots** (NBOT, SBOT) positionnés en flanc nord et sud, chargés de scouter et de retransmettre les positions ennemies.

Aucun robot ne connaît l'état interne d'un autre. Toute coordination passe par des messages broadcastés dans la boîte aux lettres du simulateur.

# Détection et tir

## Détection et suivi des ennemis

`MainBot.scanRadar()` convertit chaque résultat radar en coordonnées absolues :

$$x_{ennemi} = x_{bot} + d \cdot \cos(\theta), \quad y_{ennemi} = y_{bot} + d \cdot \sin(\theta)$$

Les ennemis détectés sont transmis via `broadcast("ENEMY ...")` pour que les alliés puissent les enregistrer sans les avoir vus directement.

`trackEnemy()` maintient la liste `enemies` par fusion : si un ennemi existe à moins de 50 unités de la position rapportée, sa fiche est mise à jour via `updatePosition()` ; sinon un nouvel `Enemy` est créé. Pour les positions reçues par message (et non par radar direct), la distance et la direction sont recalculées depuis la position courante du bot.

La classe `Enemy` détecte l'oscillation entre deux pas consécutifs en comparant le signe des deltas de position. Si `prevDx * currentDx < 0` ou `prevDy * currentDy < 0`, la cible est considérée en oscillation et la position prédite est ramenée à la moyenne des deux dernières positions au lieu d'extrapoler la vitesse.

## Sélection de cible

`selectTarget()` trie la liste `enemies` par distance croissante, puis teste chaque ennemi jusqu'à en trouver un dont la ligne de tir est dégagée :

1. Prédire la position de l'ennemi au temps de vol $t = d / v_{balle}$.
2. Vérifier que cette position prédite n'est pas masquée par un allié via `isAllyInFireLine()`.
3. Retourner le premier ennemi qui passe ce filtre.

`isAllyInFireLine()` prédit également la position future des alliés : les scouts se déplacent à vitesse 3 si un MainBot est à moins de 1250 unités, sinon restent stationnaires ; les autres MainBots avancent d'une unité le long du cap courant.

## Calcul de l'angle de tir

`computeFiringAngle()` opère en trois étapes.

**Étape 1 : prédiction.** La position future de la cible est calculée avec le temps de vol de la balle :

$$t = \frac{d}{v_{balle}}, \quad (x_f, y_f) = (x_0 + v_x \cdot t,\; y_0 + v_y \cdot t)$$

**Étape 2 : angle central et déviation maximale.** L'angle vers la position prédite est :

$$\theta_c = \arctan2(y_f - y_{bot},\; x_f - x_{bot})$$

La déviation maximale admissible pour toucher une cible de rayon $r_{bot}$ avec une balle de rayon $r_{balle}$ est :

$$\Delta\theta = \arctan\!\left(\frac{r_{bot} + r_{balle} - 1}{d}\right)$$

**Étape 3 : sélection de l'angle.** Trois angles sont testés dans l'ordre $\{\theta_c,\; \theta_c - \Delta\theta,\; \theta_c + \Delta\theta\}$. Pour chaque angle, `isLineClear()` projette un segment jusqu'à la portée maximale de la balle (1000 unités) et vérifie que ni un allié ni une épave ne se trouve à moins de $r_{bot} + r_{balle}$ de ce segment. Le premier angle passant ce test est retenu.

Si aucun angle n'est valide, `computeFiringAngle()` retourne `NaN` et le bot revient en état `MOVING`.

## Manoeuvre post-tir

Après chaque tir, `isManeuvering` est mis à vrai. `maneuverAfterFire()` exécute alors une repositionnement :

- Si le cap n'est pas aligné avec la cible (tolérance 0.5 rad) : rotation vers la cible.
- Si la distance à la cible est inférieure à 980 unités : recul.
- Sinon : avance.

Un compteur `fireCount` limite à 100 le nombre de tirs consécutifs sur la même cible. Au-delà, l'ennemi est retiré de la liste (il est probablement détruit ou hors portée) et le bot retourne en `MOVING`.

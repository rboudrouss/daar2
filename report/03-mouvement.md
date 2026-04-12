# Navigation et évitement

## Navigation par waypoints

La méthode `moveToRdv(tX, tY)` calcule l'angle vers la cible puis le restreint aux quatre directions cardinales via `getNearestAllowedDirection()`. Cette contrainte est appliquée aux MainBots pour garantir des trajectoires rectilignes prévisibles et faciliter la vérification de dégagement de ligne de tir. Les AssistBots ignorent cette restriction et naviguent librement.

À chaque pas, `tryMove(forward)` calcule la position prédite après déplacement et appelle `isWithinBounds()` avant d'exécuter le mouvement. La marge de sécurité est de 100 unités pour les MainBots, 150 pour les AssistBots. Si la position prédite sort des limites, `startAvoidance()` est déclenché immédiatement.

## Évitement d'obstacles

`startAvoidance()` teste trois chemins alternatifs dans l'ordre de priorité suivant :

1. Tournant à droite : cap courant + 0.5π
2. Tournant à gauche : cap courant − 0.5π
3. Recul : cap inverse

Pour chaque candidat, la méthode itère sur tous les obstacles détectés par le radar et teste si leurs coins (bounding box de rayon `r`) tombent dans le couloir de trajectoire via `isPointInTrajectory()`.

`isPointInTrajectory()` construit un rectangle de largeur `2 × BOT_RADIUS` et de longueur `2 × BOT_RADIUS` centré sur le cap à tester, puis délègue à `isPointInRectangle()`. Ce dernier utilise deux projections par produit scalaire pour tester l'appartenance à un rectangle quelconque orienté :

$$0 \le \overrightarrow{AP} \cdot \overrightarrow{AB} \le |\overrightarrow{AB}|^2 \quad \text{et} \quad 0 \le \overrightarrow{AP} \cdot \overrightarrow{AD} \le |\overrightarrow{AD}|^2$$

Le premier chemin sans obstacle retenu détermine le nouvel état (`TURNING_RIGHT`, `TURNING_LEFT` ou `MOVING_BACK`) et la cible d'évitement `(avoidTargetX, avoidTargetY)` placée à 50 unités dans la direction choisie.

Pour l'`AssistBot`, `avoidanceFraction` est tiré aléatoirement dans `[0.2, 0.8]` à chaque évitement, ce qui varie l'angle de dégagement entre 36° et 144°.

## Évasion de balles

L'évasion repose sur trois méthodes chaînées dans `BaseBot`.

**`updateTrackedBullets(rawBullets)`** associe chaque balle détectée par le radar à son observation précédente. La correspondance utilise la position prédite `(x + vx, y + vy)` avec une tolérance de 15 unités (légèrement supérieure à la vitesse de 10 unités/pas pour absorber les erreurs d'arrondi). La vitesse `(vx, vy)` est calculée par différence entre la position courante et celle du pas précédent.

**`isBulletThreat(b, px, py, danger)`** calcule la distance perpendiculaire du point `(px, py)` à la trajectoire de la balle par la formule :

$$d_\perp = \left| \overrightarrow{dx} \cdot \hat{v}_y - \overrightarrow{dy} \cdot \hat{v}_x \right|$$

où $(\hat{v}_x, \hat{v}_y)$ est le vecteur vitesse normalisé et $(dx, dy)$ le vecteur du bot vers la balle. Une menace est confirmée si $d_\perp < r_{bot} + r_{balle} + 10$ et si la balle se dirige bien vers le bot (produit scalaire positif).

**`performDodge()`** anticipe 5 pas en avant et 5 pas en arrière le long du cap courant, et exécute le déplacement qui sort le bot de la zone de danger. En l'absence des deux options, le recul est appliqué par défaut.

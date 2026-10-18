# Attribution des tirs Create Big Cannons

Étude faite sur les sources de **Create Big Cannons 5.11.7** (branche `create-v6-1.21.1`, NeoForge 1.21.1, Create 6.0.10,
Ritchie's Projectile Library 2.1.2) et de Create (`mc1.21.1/dev`). Implémentation : `compat/cbc/CbcCompat.java`.
Aucun mixin n'est utilisé.

## Ce que font les sources

| Question | Constat dans CBC | Fichier |
|---|---|---|
| Le projectile a-t-il un propriétaire ? | **Non.** `setOwner` n'est appelé nulle part dans CBC. Les obus (`AbstractCannonProjectile extends Projectile`) n'ont jamais de propriétaire. | `munitions/AbstractCannonProjectile.java` |
| Quelle `DamageSource` à l'impact ? | `indirectArtilleryFire()` crée un `CannonDamageSource(type)` qui appelle `super(type)` : **ni entité directe, ni entité responsable**. `getEntity()` et `getDirectEntity()` valent `null`. Types : `createbigcannons:cannon_projectile`, `big_cannon_projectile`, `shrapnel`, `grapeshot`, `flak`, `machine_gun_fire`, `traffic_cone`. | `munitions/CannonDamageSource.java`, `AbstractBigCannonProjectile.java:248` |
| Et à l'explosion ? | Toutes les explosions d'obus (`ShellExplosion`, `ImpactExplosion`, `ShrapnelExplosion`, `FlakExplosion`, `FluidExplosion`, `MortarStoneExplosion`) reçoivent `this.indirectArtilleryFire(false)` : même source sans entité. Certaines passent même `null` comme entité source de l'explosion. | `he_shell/HEShellProjectile.java:32`, etc. |
| Où les dégâts sont-ils appliqués ? | Toujours **pendant le tick de l'obus** (ou de la gerbe d'éclats) : `tick()` → `clipAndDamage()` → `onHitEntity()` → `entity.hurt(...)`, et la détonation se fait elle aussi dans le tick. | `AbstractCannonProjectile.java:98-125, 418-428` |
| Les éclats ? | Shrapnel, grapeshot et flak créent des entités `*Burst` via `addFreshEntity` **pendant le tick de l'obus parent**. Elles infligent leurs dégâts dans leur propre tick. | `fragment_burst/CBCProjectileBurst.java:96` |
| Comment le tir est-il déclenché ? | `AbstractMountedCannonContraption.fireShot(level, PitchOrientedContraptionEntity)`. Pour un canon monté : signal redstone sur la face de tir de la monture → `neighborChanged` → **tick de bloc programmé** → `onRedstoneUpdate` → `fireShot`. Pour un affût (`CannonCarriageEntity`) : paquet du joueur assis → `tryFiringShot`. Les autocanons tirent depuis le tick de leur contraption. | `CannonMountBlock.java:88-118`, `MountedBigCannonContraption.java:250-266`, `CannonCarriageEntity.java:150-166` |
| Où apparaît l'obus ? | `setPos(spawnPos)` puis `addFreshEntity`. `spawnPos` = bouche + 1 bloc − 2 blocs, donc **à l'intérieur du canon**, dans la boîte englobante de la contraption (Create la calcule à partir des blocs). | `MountedBigCannonContraption.java:430-464`, Create `AbstractContraptionEntity.setPos` |
| Tir par redstone ? | Rien ne distingue un tir déclenché par un levier actionné à la main d'un tir par horloge redstone : le chemin de code est le même. | — |
| Qui peut s'asseoir ? | Create n'accepte un passager sur une contraption que si elle contient un siège (`canAddPassenger`). Un gros canon monté n'en a pas. Le joueur « assis » est donc celui qui monte un **affût** (`CannonCarriageEntity`). | Create `AbstractContraptionEntity.java:252` |

Conclusion : depuis la `DamageSource` seule, il est **impossible** de savoir quel obus a frappé. En revanche, le dégât a
toujours lieu pendant le tick d'une entité CBC.

## Stratégie retenue (sans mixin)

1. **Opérateurs.** `PlayerInteractEvent.RightClickBlock` : un clic droit sur une monture de canon
   (`ControlPitchContraption`, soit `CannonMountBlockEntity` ou `FixedCannonMountBlockEntity`) ou sur un bloc **adjacent**
   (rayon de Chebyshev 1 : levier, bouton, manivelle posés sur ou contre la monture) enregistre
   `(monture, joueur, gameTime)` dans `CannonOperatorTracker`. C'est ce qui couvre l'assemblage, le tir au levier et la
   visée à la manivelle. Un joueur assis sur le canon ou sur son affût est aussi opérateur (signal frais).
   Les `FakePlayer` (déployeur de Create) ne comptent jamais.
2. **Tir.** `EntityJoinLevelEvent` d'un `AbstractCannonProjectile` : le canon qui tire est soit la contraption **en train
   de tiquer** (autocanons, exact), soit la ou les `PitchOrientedContraptionEntity` dont la boîte englobante est à moins de
   2 blocs du point d'apparition. L'opérateur est le joueur assis ou, à défaut, le dernier joueur ayant interagi avec la
   monture, **seulement si l'interaction date de moins de `cannonOperatorWindowTicks`** (200 ticks par défaut).
   L'obus est alors marqué (attachment `bloodcontracts:responsible`, sérialisé : le marquage survit au déchargement de chunk).
3. **Héritage.** Toute entité CBC qui apparaît pendant le tick d'une entité CBC marquée (gerbes d'éclats) hérite du marquage.
4. **Dégâts.** `EntityTickEvent.Pre/Post` maintient la pile des entités CBC en train de tiquer. Un dégât de type
   `createbigcannons:*` sans entité est attribué au marquage de l'entité CBC en cours de tick. Ce marquage alimente le
   `DamageLedger` (verrou d'annulation) puis `KillAttributor` comme n'importe quelle source.

### Cas sans attribution (« dans le doute, pas de récompense »)
- Tir par redstone (horloge, capteur, liaison sans fil Create) sans interaction récente d'un joueur avec la monture.
- Interaction plus vieille que `cannonOperatorWindowTicks`.
- Plusieurs canons candidats dont les opérateurs diffèrent (batterie serrée tirée au même instant).
- Dégât CBC appliqué hors du tick d'une entité CBC marquée (obus non marqué, bloc d'obus qui explose au sol, etc.).
- Dégâts de feu ou de fluide des obus fluides : ce sont des types vanilla (`on_fire`). Ils suivent la règle générale
  (registre des dégâts si la mort est environnementale).

### Limites connues
- Le chargement mécanique (chargeur de canon, déployeur) et la visée par rotation automatique ne sont pas des
  interactions directes : ils ne désignent personne.
- Un levier relié à la monture par plus d'un bloc de redstone n'est pas « adjacent » : il ne compte pas. C'est voulu,
  le rayon est volontairement strict.
- Le chargement manuel d'un autocanon passe par le paquet d'interaction de contraption de Create, qui ne déclenche
  aucun événement NeoForge. Ce n'est pas observé (le tir reste attribuable via le levier ou le siège).

## Autres sources Create

| Source | Comportement |
|---|---|
| Déployeur avec une arme | Attaque via `DeployerFakePlayer extends FakePlayer` (avec l'UUID de son propriétaire) : **aucune attribution**. Le propriétaire d'une machine n'est jamais présumé responsable. Testé (`machineFakePlayerCreditsNobody`). |
| Scie, perceuse, roue de broyage, écrasement par contraption | Types de dégâts Create sans entité, hors du tag `bloodcontracts:environmental` : **aucune attribution**, même avec un coup de joueur récent. |

## Tests (`./gradlew runGameTestServerCompat`)

Les tests construisent un **vrai canon** : monture, culasse et fûts en fonte, gargousses et obus chargés via l'API de
chargement manuel de CBC, assemblage par la monture, tir par la redstone.

| Test | Niveau |
|---|---|
| `shotAfterRecentInteractionCreditsOperator` : levier actionné par un joueur, l'obus tue la cible | Réel de bout en bout (le clic est simulé en postant le même `RightClickBlock` que le jeu) |
| `redstoneShotWithoutInteractionCreditsNobody` : bloc de redstone, personne n'a touché le canon | Réel |
| `staleInteractionCreditsNobody` : interaction plus vieille que la fenêtre | Réel |
| `shellExplosionCreditsOperator` : obus explosif à fusée d'impact contre un mur, la cible hors de la trajectoire meurt du souffle | Réel |
| `playerOnCarriageIsOperator` : joueur sur un affût portant le canon | Niveau le plus proche : vrai affût, vrai canon, appel direct de la recherche d'opérateur (pas de tir depuis l'affût) |
| `markSurvivesSerialization` : le marquage survit à la sauvegarde de l'entité | Direct |

`cannonOperatorWindowTicks` est fixé à **200 ticks** (10 s) : de quoi charger, viser et tirer, mais pas d'attribuer un tir
d'horloge déclenché longtemps après.

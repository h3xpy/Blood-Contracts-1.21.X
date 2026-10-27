# État des tests (v2)

Dernière exécution : 2026-10-27 (NeoForge 21.1.248).

| Suite | Commande | Résultat |
|---|---|---|
| JUnit (logique pure) | `./gradlew test` | ✅ 53/53 |
| GameTests, sans Create ni CBC | `./gradlew runGameTestServer` | ✅ 90/90 |
| GameTests avec Create 6.0.10, CBC 5.11.7, RPL 2.1.2, CBC Enchanced Shells 1.4.0 (les 90 tests de base + 8 de compat) | `./gradlew runGameTestServerCompat` | ✅ 98/98 |

Les serveurs de GameTest s'arrêtent avec un code non nul si un test échoue. Chaque GameTest tourne dans son propre batch
(ils partagent l'état global du `BountyManager`) et commence par `TestSupport.reset()`, qui remet la config, le PvP,
le gestionnaire et l'horloge réelle. Tout ce qui se mesure en temps réel (préavis, immunité, retour des cœurs) passe par
`TimeSource` ; les tests utilisent `TestSupport.TestClock`, avancée à la main (`advanceMinutes`).

Run de compat : Ritchie's Projectile Library force-charge les chunks traversés par un obus puis retire ce chargement,
y compris sur une zone de GameTest voisine. Les tests de canon utilisent donc un champ de tir de 20 blocs
(`cannon_range`) fermé par un mur d'obsidienne.

Tests JUnit : `V2ModelTest` (types, élargissement seul, préavis, persistance v2, soldes, migration d'une vraie sauvegarde v1),
`TranslationKeysTest`, `BountyTest`, `KillAttributorTest`, `DamageLedgerTest`, `BountySavedDataTest`, `CannonOperatorTrackerTest`.

## Matrice §8.4

### Migration
| | Exigence | Test(s) | Note |
|---|---|---|---|
| ✅ | Une sauvegarde v1 se charge : primes actives conservées (type `PUBLIC`), objets intacts, aucune duplication | `V2ModelTest.realV1SaveMigratesWithoutLosingAnything` | Vraie sauvegarde 1.0.x (`src/test/resources/v1/`), rechargée puis réécrite en v2 et relue : mêmes piles, mêmes livraisons, même historique |
| ✅ | Une prime v1 qui viole les nouvelles limites reste active | `V2ModelTest.v1BountiesBreakingTheNewLimitsStayActive` | 5 primes du même commanditaire sur la même cible |

### Séquestre et cycle de vie
| | Exigence | Test(s) | Note |
|---|---|---|---|
| ✅ | Création : l'objet exact quitte l'inventaire et entre au séquestre | `LifecycleTests.createMovesExactStackToEscrow`, `NetworkTests.createFromMenuSlotEscrowsTheSlotItem` | Composants, quantité, copie sans référence partagée |
| ✅ | Création refusée (soi-même, emplacement vide, blacklist, liste privée vide ou trop longue) : inventaire inchangé | `LifecycleTests.createRefusedLeavesInventoryUnchanged`, `TypeTests.invalidPrivateListsAreRefused`, `FeatureTests.oneBlacklistedStackRefusesTheWholeBounty` | Liste privée : vide, trop longue, avec le commanditaire, avec la cible, joueur inconnu |
| ✅ | Seconde prime sur la même cible par le même commanditaire : refusée | `LifecycleTests.oneBountyPerSponsorTargetPair` | `PAIR_EXISTS` |
| ✅ | Au-delà de `maxActiveBountiesPerSponsor` : refusée | `LifecycleTests.createRefusedOverSponsorLimit` | |
| ✅ | Deux commanditaires différents sur la même cible : acceptés | `LifecycleTests.oneBountyPerSponsorTargetPair` | |
| ✅ | Demande d'annulation : toujours `ACTIVE` après 29 min | `LifecycleTests.cancelNoticeKeepsBountyActiveThenRefunds` | |
| ✅ | Kill éligible pendant le préavis : remise normale (objet, cœur, immunité), pas de retour au commanditaire | `LifecycleTests.killDuringNoticePaysNormally` | |
| ✅ | Échéance du préavis sans kill : `CANCELLED`, l'objet revient une seule fois | `LifecycleTests.cancelNoticeKeepsBountyActiveThenRefunds` | Vérifications périodiques répétées ensuite : pas de second remboursement |
| ✅ | Préavis échu pendant un arrêt : finalisé au redémarrage | `LifecycleTests.noticeEndedDuringDowntimeIsFinalizedAtStart` | |
| ✅ | `resume` pendant le préavis : toujours active après 30 min | `LifecycleTests.resumeWithdrawsTheCancellation` | Par commande et par payload aussi : `V2NetworkTests.commandsWidenButNeverNarrow`, `widenPayloadsAreRevalidated` |
| ✅ | Seconde demande d'annulation : délai non raccourci | `LifecycleTests.secondCancelRequestDoesNotShortenTheNotice`, `V2ModelTest.noticeIsNeverShortenedAndCanBeWithdrawn` | |
| ✅ | Une prime en préavis bloque une nouvelle prime sur la même cible par le même commanditaire | `LifecycleTests.oneBountyPerSponsorTargetPair` | |
| ✅ | `admin cancel` : immédiat | `V2NetworkTests.adminCancelIsImmediateAndHeartsAreReadable`, `LifecycleTests.onlySponsorOrOperatorCanCancel` | Vraie commande depuis la console ; refusée aux joueurs |
| ✅ | **Conservation des objets** | `TestSupport.assertConserved`, appelé dans tous les scénarios qui déplacent un objet | Inventaires, curseur, emplacements de menu, séquestre, livraisons, sol (objets marqués d'un UUID propre au test) |

### Types et élargissement
| | Exigence | Test(s) | Note |
|---|---|---|---|
| ✅ | `PUBLIC` : n'importe quel joueur éligible encaisse | `TypeTests.privateBountiesCanOnlyBeWidened`, `V2ModelTest.publicBountyAnyoneButSponsorAndTarget`, tous les tests d'attribution | |
| ✅ | `PRIVATE` : un non-listé tue la cible, la prime reste `ACTIVE` ; un listé encaisse | `TypeTests.privateBountyOnlyListedHuntersCollect` | Aucun cœur ne bouge sur le kill du non-listé |
| ✅ | Ajout d'un chasseur : il devient éligible | `TypeTests.privateBountiesCanOnlyBeWidened`, `V2NetworkTests.widenPayloadsAreRevalidated` | Taille maximale respectée : `TypeTests.wideningRespectsTheListSize` |
| ✅ | `PRIVATE → PUBLIC` accepté | `TypeTests.privateBountiesCanOnlyBeWidened`, `V2NetworkTests.commandsWidenButNeverNarrow` | |
| ✅ | Retrait d'un chasseur ou `PUBLIC → PRIVATE` impossible par commande et par payload | `V2NetworkTests.commandsWidenButNeverNarrow`, `widenPayloadsAreRevalidated`, `TypeTests.privateBountiesCanOnlyBeWidened`, `V2ModelTest.huntersCanOnlyBeAdded` | Aucune commande, aucun payload, aucune méthode du gestionnaire ni de `Bounty` ne le permet (vérifié par réflexion sur l'arbre de commandes et les classes) |
| ✅ | Modification par un autre joueur, ou sur une prime non `ACTIVE` : refusée | `TypeTests.privateBountiesCanOnlyBeWidened`, `V2NetworkTests.widenPayloadsAreRevalidated`, `commandsWidenButNeverNarrow` | |
| ✅ | Confidentialité : la vue d'un non-commanditaire n'a pas la liste, mais a le type et son éligibilité | `V2NetworkTests.hunterListOnlyReachesTheSponsor` | Vérifié après encodage/décodage réseau, pour un étranger, un chasseur listé, la cible ; présent pour le commanditaire et les opérateurs ; l'historique ne la divulgue pas non plus |

### Attribution
| | Exigence | Test(s) | Note |
|---|---|---|---|
| ✅ | Épée, flèche, TNT | `AttributionTests.swordKillCreditsHunter`, `arrowKillCreditsShooterNotArrow`, `tntLitByPlayerCreditsThatPlayer` | Vraie flèche, vraie explosion |
| ✅ | Vide après un coup dans la fenêtre ; hors fenêtre : personne | `AttributionTests.hitThenVoidWithinWindowCreditsHunter`, `hitThenVoidOutsideWindowCreditsNobody` | |
| ✅ | Deux attaquants : le plus récent | `AttributionTests.twoAttackersMostRecentWins`, `KillAttributorTest.twoAttackersMostRecentWins` | |
| ✅ | Lave, chute, noyade sans joueur ; `/kill` : personne | `AttributionTests.lavaWithoutPlayerCreditsNobody`, `fallWithoutPlayerCreditsNobody`, `drowningWithoutPlayerCreditsNobody`, `killCommandCreditsNobody` | |
| ✅ | Loup apprivoisé selon la config | `AttributionTests.tamedWolfCreditsOwner`, `tamedWolfCreditsNobodyWhenDisabled` | |
| ✅ | Le commanditaire tue sa cible : sa prime reste active, les autres lui sont remises s'il y est éligible | `RobustnessTests.sponsorKillingTargetKeepsOwnBountyActive`, `severalBountiesAllPaidToEligibleHunter` | |
| ✅ | Plusieurs primes : toutes les éligibles, dans l'ordre de création | `HeartTests.severalBountiesStillOneHeartToTheOldestSponsorWithRoom`, `RobustnessTests.severalBountiesAllPaidToEligibleHunter` | |
| ✅ | Double `LivingDeathEvent` : une seule remise | `RobustnessTests.replayedDeathEventPaysOnce` | |
| ✅ | (ajout) Mob qui achève, machine (`FakePlayer`) : personne | `AttributionTests.mobKillAfterPlayerHitCreditsNobody`, `machineFakePlayerCreditsNobody` | |

### Cœurs et immunité
| | Exigence | Test(s) | Note |
|---|---|---|---|
| ✅ | Prime remportée : cible −1 cœur, commanditaire +1 (attribut `MAX_HEALTH`) | `HeartTests.collectedBountyMovesOneHeart` | 18 / 22 PV, modificateur `bloodcontracts:heart_balance` |
| ✅ | Somme des soldes toujours nulle | `HeartTests` (tous, via `assertSumZero`), `V2ModelTest.balancesAlwaysSumToZero` | Aucun transfert vide conservé |
| ✅ | Plancher et plafond : pas de transfert au-delà, objet remis quand même | `HeartTests.floorStopsTransfersButItemsArePaid`, `ceilingStopsTransfers` | |
| ✅ | 3 primes remportées : 1 seul cœur, au commanditaire le plus ancien, ou au suivant s'il est au plafond | `HeartTests.severalBountiesStillOneHeartToTheOldestSponsorWithRoom` | |
| ✅ | `PROGRESSIVE` en ligne : 1 PV après 90 min (rien à 89), 1 cœur après 3 h ; le commanditaire perd le même PV | `HeartTests.onlineRegenOneHpPerNinetyMinutes` | Jauge et transfert supprimés quand tout est rendu |
| ✅ | `PROGRESSIVE` hors ligne : rien à 11 h 59, 1 PV à 12 h (à la connexion) ; 30 h : 2 PV, jauge 0,5 | `HeartTests.offlineRegenOneHpPerTwelveHours`, `thirtyHoursOfflineGiveTwoHpAndKeepHalf` | |
| ✅ | 45 min en ligne (jauge 0,5) puis 6 h hors ligne : 1 PV, jauge 0 | `HeartTests.mixedOnlineAndOfflineTime` | |
| ✅ | Un arrêt du serveur compte comme hors ligne | `HeartTests.serverDowntimeCountsAsOffline` | |
| ✅ | Immunité en temps réel : expire pendant que la cible est hors ligne | `HeartTests.immunityBlocksCollectionUntilItEnds` | |
| ✅ | Avertissements de config dans les deux cas d'incohérence | `HeartTests.inconsistentSettingsAreWarned` | `BountyManager.configWarnings()`, loggés au démarrage par `onServerStarted` |
| ✅ | Une nouvelle perte ne remet pas la jauge à zéro | `HeartTests.newLossDoesNotResetTheGauge` | |
| ✅ | Transferts de plusieurs commanditaires remboursés du plus ancien au plus récent | `HeartTests.thirtyHoursOfflineGiveTwoHpAndKeepHalf`, `newLossDoesNotResetTheGauge` | |
| ✅ | `PERMANENT` : aucun retour | `HeartTests.permanentModeNeverGivesBack` | |
| ✅ | Scénario de cohérence : tuée à chaque fin d'immunité, la cible descend au plancher, puis remonte en 15 h de jeu ; solde global nul | `HeartTests.killedAtEveryImmunityEndThenRecovers` | 30 cycles de 2 h, 6 commanditaires |
| ✅ | Modificateur réappliqué après réapparition, reconnexion, changement de dimension, sans doublon | `HeartTests.modifierReappliedWithoutDuplicates` | Vraie réapparition (`PlayerList.respawn`), vraie reconnexion, téléportation dans le Nether |
| ✅ | La baisse du maximum ne tue pas | `HeartTests.loweringMaxHealthNeverKills` | |
| ✅ | Immunité : kill sans effet pendant, fonctionne après | `HeartTests.immunityBlocksCollectionUntilItEnds` | |
| ✅ | (ajout) `admin clearimmunity` : lève l'immunité tout de suite, réservé aux opérateurs | `V2NetworkTests.adminCanClearImmunity` | Persisté ; les primes redeviennent encaissables aussitôt ; « pas immunisé » si on recommence |
| ✅ | Prime créable sur une cible immunisée | `HeartTests.immunityBlocksCollectionUntilItEnds` | |
| ✅ | Transferts et immunité survivent au rechargement | `HeartTests.transfersAndImmunitySurviveReload`, `V2ModelTest.v2FieldsSurviveRoundTrip` | Jauge et compteurs de paires aussi |

### Livraison et robustesse
| | Exigence | Test(s) | Note |
|---|---|---|---|
| ✅ | Chasseur hors ligne : remise à la reconnexion, une seule fois | `RobustnessTests.offlineHunterIsPaidOnceOnReconnect` | Les récompenses vont dans l'onglet « À réclamer » (décision C4) |
| ✅ | Inventaire plein : objet au sol | `RobustnessTests.fullInventoryDropsRewardAtFeet`, `creativeHunterOverflowIsNotVoided` | |
| ✅ | Persistance complète après rechargement | `RobustnessTests.bountiesHistoryAndDeliveriesSurviveRestart`, `activeBountySurvivesRestart`, `HeartTests.transfersAndImmunitySurviveReload`, `V2ModelTest.v2FieldsSurviveRoundTrip` | Primes (type, chasseurs, préavis), livraisons, transferts, jauges, immunités, historique, paires |
| ✅ | Item inconnu au chargement : pas de crash | `RobustnessTests.unknownItemOnLoadDoesNotCrash`, `BountySavedDataTest.unknownItemDoesNotCrashAndRawDataIsKept` | |

### Historique
| | Exigence | Test(s) | Note |
|---|---|---|---|
| ✅ | Primes remportées et annulées dans l'historique | `RobustnessTests.bountiesHistoryAndDeliveriesSurviveRestart`, `LifecycleTests.cancelNoticeKeepsBountyActiveThenRefunds`, `V2NetworkTests.hunterListOnlyReachesTheSponsor` | |
| ✅ | Paires récurrentes affichées au-delà du seuil ; compteur conservé après purge et rechargement | `V2NetworkTests.recurringPairsShownAboveTheThreshold` | « Au-delà de 2 » : affiché à 3, pas à 2 (comme l'exemple « Y a encaissé 3 primes sur X »). Envoyé sur l'affiche et à l'écran de création |

### Compat (`runGameTestServerCompat`)
| | Exigence | Test(s) | Note |
|---|---|---|---|
| ✅ | Le mod charge sans Create ni CBC | `SmokeTests.modLoadsWithoutCreateOrCbc` + toute la suite `runGameTestServer` | |
| ✅ | Obus tiré après une interaction récente : joueur crédité | `CbcCompatTests.shotAfterRecentInteractionCreditsOperator` | Vrai canon assemblé, tiré par la redstone |
| ✅ | Tir par redstone sans interaction récente : personne | `CbcCompatTests.redstoneShotWithoutInteractionCreditsNobody`, `staleInteractionCreditsNobody` | |
| ✅ | Explosion d'obus : même règle | `CbcCompatTests.shellExplosionCreditsOperator` | |
| ✅ | Cas de `docs/cbc-attribution.md` | `CbcCompatTests.playerOnCarriageIsOperator`, `markSurvivesSerialization`, `moreShellsHeShellCreditsOperator`, `rocketBaseLauncherCreditsOperator` | |

### Réseau
| | Exigence | Test(s) | Note |
|---|---|---|---|
| ✅ | Payloads invalides : refusés, état inchangé, aucun objet créé | `NetworkTests.invalidCreateRequestsChangeNothing`, `invalidCancelRequestsChangeNothing`, `V2NetworkTests.widenPayloadsAreRevalidated` | Requête de création : UUID + type + liste d'UUID, jamais d'objet (`listPayloadRoundTripsAndHidesSponsor`) |

### Interface (sans humain)
| | Exigence | Test(s) | Note |
|---|---|---|---|
| ✅ | La vue contient le `GameProfile` de la cible avec ses textures, y compris hors ligne | `V2NetworkTests.bountiesOnOneTargetMakeOnePosterWithTheSkin` | Propriété `textures` signée, après encodage réseau |
| ✅ | Plusieurs primes sur une cible : une seule entrée | `V2NetworkTests.bountiesOnOneTargetMakeOnePosterWithTheSkin` | Ordre de création ; tri éligibles d'abord puis récentes : `collectablePostersComeFirst` |
| ✅ | Valeurs de config envoyées pour l'aide = config serveur | `V2NetworkTests.helpSettingsMatchTheServerConfig` | Envoyées à la connexion |
| ✅ | (ajout) Le commanditaire en ligne est prévenu quand sa cible tombe sous contrat | `V2NetworkTests.sponsorIsToldTheirTargetWentDown` | Une seule fois, avec le chasseur et le cœur gagné ; rien pour le chasseur |
| ✅ | Immunité et préavis dans la vue | `V2NetworkTests.viewsCarryImmunityAndNotice` | Plus l'horloge du serveur, pour les comptes à rebours |
| ✅ | Toutes les clés de traduction existent en `fr_fr` et `en_us` | `TranslationKeysTest` | Parcourt le code, les JSON de données et les clés construites depuis les enums ; mêmes clés et mêmes `%s` dans les deux langues |

### Ajouts v1 conservés
Multi-objets (`FeatureTests`), alerte à la cible, prime sur joueur hors ligne, import des joueurs déjà venus, skins hors
ligne, succès (`AdvancementTests`), onglet « À réclamer » : tous verts, inchangés depuis la 1.0.2.

## Ce qui demande un humain

Le rendu des écrans est semi-automatisé : `./gradlew runBoardDebug` ouvre la sauvegarde « New World », remplit le
tableau avec des données d'exemple et prend une capture de chaque onglet, du détail, du sélecteur de chasseurs et de
l'écran de création (`run/screenshots/board_*.png`), puis quitte. `./gradlew runFxDebug` capture l'alerte (en jeu, en
pause sous un menu, à la reprise), les notifications (« à réclamer », « contrat rempli ») et l'animation d'un cœur gagné
puis perdu, déclenchée par le vrai modificateur côté serveur (`run/screenshots/fx_*.png`). `./gradlew runAlertDebug`
garde l'ancien parcours de l'alerte à travers plusieurs écrans.
Les captures du 2026-10-27 ont été relues.

À vérifier en jeu, de préférence à trois en LAN (voir README) :
- [ ] **Affiches** : survol (l'affiche se redresse et grossit), clic qui ouvre le détail, pagination à la molette et aux flèches au-delà de 10 cibles.
- [ ] **Skins** : visage réel (avec chapeau) d'une cible hors ligne, skin par défaut seulement le temps du chargement.
- [ ] **Comptes à rebours** : « EXPIRE DANS » et « IMMUNISÉ » qui diminuent chaque minute, écran ouvert.
- [ ] **Détail, côté commanditaire** : « + Chasseurs » (sélecteur, recherche, limite), « Rendre publique », « Annuler » puis « Reprendre » ; boutons absents sur les primes des autres.
- [ ] **Création** : bascule Publique/Privée, avertissement public, sélection des chasseurs, « Signer » grisé tant qu'il manque une cible, un objet ou un chasseur ; cliquer dans le panneau de droite avec un objet en main ne le lâche pas ; « ◀ Tableau des primes » rend les objets posés.
- [ ] **Cœurs** : barre de vie qui change après une prime remportée et qui revient en jouant (sans mourir quand le maximum baisse).
- [ ] **Comment ça marche** : textes en français avec les valeurs du serveur (changer une valeur de config et se reconnecter).
- [ ] **Alerte** : reçue pendant qu'un menu est ouvert (réglages, chat…), rien ne s'affiche ni ne sonne avant de revenir au jeu, puis image et son partent ensemble ; ouverte pendant l'alerte, un menu la met en pause (liseré rouge seulement).
- [ ] **Notifications** : « à réclamer » et « contrat rempli » (visage de la cible, +1 cœur) avec leur son.
- [ ] **Animation des cœurs** : un cœur gagné jaillit au-dessus de la barre d'action et file vers sa place ; un cœur perdu se brise ; un demi-cœur rendu fait un petit éclat. La perte due à une mort sous contrat se joue après la réapparition (rien derrière l'écran de mort ; vérifié par `runFxDebug`), un changement survenu dans un menu se joue à sa fermeture. Pas d'animation parasite à la connexion ni en changeant de dimension.

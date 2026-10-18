# État des tests

Dernière exécution : 2026-10-18 (NeoForge 21.1.248).

| Suite | Commande | Résultat |
|---|---|---|
| JUnit (logique pure) | `./gradlew test` | ✅ 36/36 |
| GameTests, sans Create ni CBC (NeoForge 21.1.248) | `./gradlew runGameTestServer` | ✅ 40/40 |
| GameTests avec Create 6.0.10, CBC 5.11.7, RPL 2.1.2, CBC Enchanced Shells (« More Shells ») 1.4.0 (les 40 tests de base plus 8 tests de compat) | `./gradlew runGameTestServerCompat` | ✅ 48/48 |

Les serveurs de GameTest s'arrêtent avec un code non nul si un test échoue. Chaque GameTest tourne dans son propre batch
(ils partagent l'état global du `BountyManager`) et commence par `TestSupport.reset()`.

Tests JUnit : `BountyTest` (machine à états, éligibilité), `KillAttributorTest`, `DamageLedgerTest`,
`BountySavedDataTest` (sérialisation, historique borné, item inconnu), `CannonOperatorTrackerTest`.

## Matrice

### Cycle de vie et séquestre
| | Exigence | Test(s) | Note |
|---|---|---|---|
| ✅ | Créer une prime retire exactement l'objet et le place en séquestre | `LifecycleTests.createMovesExactStackToEscrow`, `NetworkTests.createFromMenuSlotEscrowsTheSlotItem` | Épée nommée et endommagée, pile de 64, copie sans référence partagée |
| ✅ | Création refusée (cible = soi, emplacement vide, blacklist), inventaire inchangé | `LifecycleTests.createRefusedLeavesInventoryUnchanged`, `createRefusedOverSponsorLimit` | Couvre aussi : cible inconnue, emplacement invalide, quantité invalide, limite par commanditaire |
| ✅ | Annulation : objet rendu, `CANCELLED`, dans l'historique | `LifecycleTests.cancelRefundsSponsorAndRecordsHistory`, `refundToOfflineSponsorWaitsForLogin` | Le remboursement d'un commanditaire hors ligne attend sa connexion |
| ✅ | Double annulation refusée, pas de duplication | `LifecycleTests.doubleCancelIsRefused` | |
| ✅ | Annulation refusée pendant `cancelLockSeconds` après dégâts d'un joueur éligible | `LifecycleTests.cancelLockedAfterEligibleDamage` | Les coups du commanditaire ne verrouillent pas ; le verrou expire |
| ✅ | Seul le commanditaire ou un opérateur peut annuler | `LifecycleTests.onlySponsorOrOperatorCanCancel` | |
| ✅ | **Conservation** sur chaque scénario | `TestSupport.assertConserved` appelé dans tous les scénarios qui déplacent un objet | Compte inventaires, curseur, emplacements de menu, séquestre, livraisons et objets au sol (objets marqués d'un UUID propre au test) |

### Attribution
| | Exigence | Test(s) | Note |
|---|---|---|---|
| ✅ | Mort par épée : chasseur crédité | `AttributionTests.swordKillCreditsHunter` | |
| ✅ | Mort par flèche : le tireur, pas la flèche | `AttributionTests.arrowKillCreditsShooterNotArrow` | Vraie flèche tirée, impact réel |
| ✅ | TNT allumée par un joueur | `AttributionTests.tntLitByPlayerCreditsThatPlayer` | Vraie explosion. Le chasseur est éloigné : s'il meurt dans l'explosion, sa récompense attend sa réapparition (comportement voulu) |
| ✅ | Coup puis vide dans la fenêtre | `AttributionTests.hitThenVoidWithinWindowCreditsHunter` | 20 ticks réels entre le coup et la mort |
| ✅ | Coup puis vide hors fenêtre | `AttributionTests.hitThenVoidOutsideWindowCreditsNobody` | Fenêtre réglée à 20 ticks, mort après 30 |
| ✅ | Deux attaquants | `AttributionTests.twoAttackersMostRecentWins`, `KillAttributorTest.twoAttackersMostRecentWins` | **Règle : le plus récent dans la fenêtre l'emporte** (égalité de tick : le dernier enregistré) |
| ✅ | Lave, chute, noyade sans joueur | `AttributionTests.lavaWithoutPlayerCreditsNobody`, `fallWithoutPlayerCreditsNobody`, `drowningWithoutPlayerCreditsNobody` | |
| ✅ | `/kill` : aucune attribution | `AttributionTests.killCommandCreditsNobody` | Vraie commande `/kill`, avec un coup de joueur juste avant |
| ✅ | Loup apprivoisé : propriétaire si `creditTamedPets`, personne sinon | `AttributionTests.tamedWolfCreditsOwner`, `tamedWolfCreditsNobodyWhenDisabled` | |
| ✅ | (ajout) Mob qui achève après un coup de joueur : personne | `AttributionTests.mobKillAfterPlayerHitCreditsNobody` | Seules les morts environnementales consultent le registre |
| ✅ | (ajout) Machine (`FakePlayer`, déployeur Create) : personne | `AttributionTests.machineFakePlayerCreditsNobody` | Même avec l'UUID du propriétaire |

### Anti-abus et robustesse
| | Exigence | Test(s) | Note |
|---|---|---|---|
| ✅ | Le commanditaire tue la cible : sa prime reste `ACTIVE` | `RobustnessTests.sponsorKillingTargetKeepsOwnBountyActive` | |
| ✅ | Plusieurs primes, commanditaires différents | `RobustnessTests.severalBountiesAllPaidToEligibleHunter` | Celle du chasseur-commanditaire reste active |
| ✅ | `LivingDeathEvent` déclenché deux fois : une seule remise | `RobustnessTests.replayedDeathEventPaysOnce` | Mort réelle, puis deux événements postés à nouveau |
| ✅ | Chasseur hors ligne : livraison conservée, remise à la reconnexion, une seule fois | `RobustnessTests.offlineHunterIsPaidOnceOnReconnect` | Vraie déconnexion et reconnexion avec le même UUID |
| ✅ | Inventaire plein : objet lâché au sol | `RobustnessTests.fullInventoryDropsRewardAtFeet`, `creativeHunterOverflowIsNotVoided` | Objet réservé au chasseur, sans despawn. Le mode créatif ne fait pas disparaître le surplus |
| ✅ | Persistance : actives, historique, livraisons | `RobustnessTests.bountiesHistoryAndDeliveriesSurviveRestart`, `activeBountySurvivesRestart`, `BountySavedDataTest` | Composants (enchantement, nom) conservés ; une seconde sauvegarde donne le même NBT |
| ✅ | Item inconnu au chargement : pas de crash | `RobustnessTests.unknownItemOnLoadDoesNotCrash`, `BountySavedDataTest.unknownItemDoesNotCrashAndRawDataIsKept` | NBT brut gardé et réécrit tel quel ; rechargé si le mod revient |
| ✅ | (ajout) Cible déconnectée : prime active, cible hors ligne connue acceptée | `RobustnessTests.targetLogoutKeepsBountyActive` | |
| ✅ | (ajout) Changement de dimension | `RobustnessTests.dimensionChangeDoesNotMatter` | Mort dans le Nether |

### Compat (`runGameTestServerCompat`)
| | Exigence | Test(s) | Note |
|---|---|---|---|
| ✅ | Le mod charge sans Create ni CBC | `SmokeTests.modLoadsWithoutCreateOrCbc` + toute la suite `runGameTestServer` | Le run principal n'a ni Create ni CBC sur son classpath |
| ✅ | Tir CBC après interaction récente : joueur crédité | `CbcCompatTests.shotAfterRecentInteractionCreditsOperator` | Vrai canon assemblé et tiré par la redstone |
| ✅ | Tir CBC par redstone sans interaction : personne | `CbcCompatTests.redstoneShotWithoutInteractionCreditsNobody`, `staleInteractionCreditsNobody` | |
| ✅ | Explosion d'obus CBC : même règle | `CbcCompatTests.shellExplosionCreditsOperator` | Obus explosif contre un mur, cible hors trajectoire tuée par le souffle |
| ✅ | Joueur assis | `CbcCompatTests.playerOnCarriageIsOperator` | Niveau le plus proche (voir `docs/cbc-attribution.md`) |
| ✅ | Marquage persistant | `CbcCompatTests.markSurvivesSerialization` | |
| ✅ | (ajout) Obus d'addon More Shells | `CbcCompatTests.moreShellsHeShellCreditsOperator` | Obus HE Mk.I tiré par un vrai canon, mort par le souffle |
| ✅ | (ajout) Lanceur sans contraption (base de fusée More Shells) | `CbcCompatTests.rocketBaseLauncherCreditsOperator` | Niveau le plus proche : vrai bloc, vraie entité fusée |
| ✅ | (ajout) Explosion d'une entité marquée sans propriétaire | `AttributionTests.explosionOfMarkedOwnerlessEntityCreditsMarkedPlayer`, `explosionOfOwnerlessEntityCreditsNobody` | Chemin `level.explode(obus, …)` des bombes More Shells |

### Réseau
| | Exigence | Test(s) | Note |
|---|---|---|---|
| ✅ | Payloads invalides refusés, état inchangé, aucun objet créé | `NetworkTests.invalidCreateRequestsChangeNothing`, `invalidCancelRequestsChangeNothing` | Sans menu ouvert, emplacement vide, cible = soi, cible inconnue, id inexistant, prime d'un autre, prime déjà close |
| ✅ | (ajout) Fermer le menu sans confirmer rend l'objet | `NetworkTests.invalidCreateRequestsChangeNothing` | |
| ✅ | (ajout) Codec des payloads, `showSponsor` respecté | `NetworkTests.listPayloadRoundTripsAndHidesSponsor` | La requête de création ne transporte qu'un UUID (16 octets) |

## Ce qui demande un humain

- ❌ (non automatisable) **Rendu visuel des écrans** (thème « avis de recherche » : parchemin, têtes des cibles, tampons ;
  textures générées par `tools/make_gui_textures.py`) : tableau des primes (touche `B` ou `/bounty gui`) et écran de création.
  À vérifier dans un client, de préférence à deux en LAN (voir README) : mise en page, défilement, info-bulles, bouton « Annuler » actif uniquement sur ses propres primes.
  Leur logique serveur est testée ; le serveur dédié a démarré sans charger de classe client (vérifié une fois avec `runServer`).

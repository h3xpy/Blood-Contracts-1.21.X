# Audit de la v1 (1.0.2) face à la spécification v2 (CLAUDE.md)

Audit fait avant toute modification. La v1 est celle qui tourne en production : mod `bloodcontracts` 1.0.2, NeoForge
21.1.248, ModDevGradle.

## Format de sauvegarde v1 (à migrer)
- Un seul `SavedData` sur l'Overworld : `data/bloodcontracts_bounties.dat`.
- Racine : `FormatVersion` = 1. Listes `Active`, `History`, `Deliveries`, `KnownPlayers` (UUID → nom) et `Skins`
  (UUID → propriété `textures` signée).
- Prime : `Id`, `Sponsor`, `Target`, `Rewards` (1 à 9 piles), `CreatedAt` (ms réelles), `State`, `ClaimedBy`, `ClosedAt`,
  `Summary`. Le format 0.1 (une seule pile `Reward` + `RewardItem`/`RewardCount`) est encore lu.
- Livraison : `Id`, `Recipient`, `Item` (une pile), `Bounty`, `Reason` (`REWARD` | `REFUND`).
- Une prime ou une livraison dont l'item est inconnu est gardée brute et réécrite telle quelle.
- Data attachment `bloodcontracts:responsible` sur les obus CBC marqués.
- Pas de type de prime, pas de liste de chasseurs, pas de cœurs, pas d'immunité, pas de compteur de paires.

## ✅ Déjà conforme
| Point | Section |
|---|---|
| `BountyManager` seul point d'entrée des mutations ; commandes, payloads et événements passent par lui | §3 |
| Séquestre : copie, emplacement vidé dans la même opération, rien ne bouge si une validation échoue | §4 Création |
| Validations : cible ≠ soi, cible connue (y compris hors ligne, import des `playerdata`), emplacement non vide, blacklist, quantité | §4 Création |
| Plusieurs commanditaires sur la même cible | §4 Création |
| Remise de toutes les primes éligibles à la mort, dans l'ordre de création ; le commanditaire tueur ne touche pas la sienne | §4 Remise |
| Ordre état → livraisons → `setDirty()` → remise physique | §4 Remise |
| Toute sortie du séquestre passe par `PendingDelivery` (une par pile) | §4 Livraison |
| Commanditaire masqué par défaut (`showSponsor` = false) | §4 Visibilité |
| Historique borné (`historySize` 200), primes remportées et annulées | §4 Historique |
| `DamageLedger`, `ResponsibleResolver`, `KillAttributor` (fenêtre 200 ticks, `/kill`, familiers, faux joueurs) | §6 |
| Compat CBC (+ CBC Enchanced Shells) sans mixin, étude dans `docs/cbc-attribution.md`, tir redstone = personne | §6 |
| Persistance sur l'Overworld, `ItemStack` via `HolderLookup.Provider`, item inconnu sans crash | §7 |
| Handlers réseau sur le thread serveur, intentions seulement, jamais d'`ItemStack` envoyé par le client | §7 Réseau |
| Emplacement de récompense = `Slot` d'un menu serveur, rendu si fermé sans confirmation | §7 Interface |
| Portraits avec le vrai skin, y compris hors ligne (propriété `textures` enregistrée et envoyée) | §7 Interface |
| Runs `gameTestServer` (sans Create/CBC) et `gameTestServerCompat`, `resetForTests()` | §8.2 |
| Clés de config `attributionWindowTicks`, `cannonOperatorWindowTicks` (200), `creditTamedPets`, `rewardBlacklist`, `showSponsor`, `historySize` | §9 |

## 🔧 À modifier ou à ajouter (sans conflit)
| Point | Section |
|---|---|
| `dataVersion` + migration v1 → v2 (types `PUBLIC`, aucune perte), test sur une sauvegarde v1 reconstruite | §0.3, §7 |
| `TimeSource` injectable pour tout le temps réel ; horloge contrôlable dans les tests | §3, §5 |
| Types `PUBLIC` / `PRIVATE`, liste de chasseurs (1 à `maxHunterListSize` = 20) | §4 Types |
| Élargir seulement : `addhunter`, `makepublic` ; refus de tout le reste | §4 Modification |
| `HeartTransfer`, solde calculé, bornes (`maxHeartsLost`, `maxHeartsGained`), 1 cœur max par mort au commanditaire le plus ancien | §5 Transfert |
| Retour progressif (jauge en ligne / hors ligne), mode `PERMANENT`, avertissements de config au démarrage | §5 Retour |
| Modificateur `bloodcontracts:heart_balance` sur `MAX_HEALTH`, recalculé (connexion, réapparition, clonage, dimension) | §5 Implémentation |
| Immunité de la cible (120 min réelles), persistée | §5 Immunité |
| Compteur de paires chasseur-cible, seuil `repeatPairThreshold` (2) | §4 Historique |
| Commandes `place … [public|private <joueurs…>]`, `addhunter`, `makepublic`, `resume`, `admin hearts` | §7 Commandes |
| Payloads `CreateBountyRequest(target, type, hunters)`, `AddHuntersRequest`, `MakePublicRequest`, `ResumeBountyRequest` ; vues regroupées par cible avec profil, immunité, `cancelAt`, éligibilité ; liste des chasseurs réservée au commanditaire ; config envoyée pour l'aide | §7 Réseau |
| Onglet « Comment ça marche » (valeurs venant de la config serveur) ; test de présence des clés `fr_fr` / `en_us` | §7 Interface, §8.4 |
| Nouvelles clés de config : `maxHunterListSize`, `cancelNoticeMinutes`, `maxHeartsLostPerDeath`, `maxHeartsLost`, `maxHeartsGained`, `heartRegenMode`, `heartRegenOnlineStepMinutes`, `heartRegenOfflineStepMinutes`, `targetImmunityMinutes`, `repeatPairThreshold` | §9 |
| `docs/CHANGELOG_V2.md` | §0.7 |

## ❓ Présent dans la v1, absent de ce document
| Fonctionnalité v1 | Contredit la spec ? | Proposition |
|---|---|---|
| Jusqu'à 9 piles différentes par prime (« + objets ») | Non (la spec parle d'« objets empilés » sur l'affiche) | Garder |
| Alerte plein écran « ta tête est mise à prix » + clé `alertRevealsSponsor` | Non | Garder |
| 4 succès (« Contrats de sang », « Le prix du sang », « Ennemi public n°1 », « Livraison express ») | Non | Garder |
| Import au démarrage des joueurs ayant un `playerdata` | Non | Garder |
| Bases de fusées CBC Enchanced Shells comme lanceurs | Non | Garder |
| Commandes `/bounty gui`, `/bounty claim` | `claim` dépend du conflit C4 | Garder `gui` ; `claim` selon C4 |
| Clé `debugLogging` | Non (§10 la demande) | Garder |

## ⚠️ Conflits (décision demandée avant d'intégrer)

### C1. Annulation : immédiate + verrou de combat (v1) contre préavis de 30 min (v2)
- **v1** : l'annulation est immédiate. L'objet est rendu sauf si la cible a pris des dégâts d'un chasseur éligible dans
  les 30 dernières secondes (`cancelLockSeconds`).
- **v2** : la prime reste `ACTIVE` et encaissable pendant `cancelNoticeMinutes` (30), avec possibilité de `resume`. Plus
  de verrou de combat. La clé `cancelLockSeconds` disparaît.
- **Impact joueurs** : un commanditaire ne récupère plus son objet tout de suite, ce qui protège les chasseurs en traque.
- **Impact données** : aucune perte. Champ `cancelAt` ajouté, null pour les primes existantes.
- **Recommandation** : appliquer la v2. Garder `cancelLockSeconds` lisible mais sans effet, documenté comme obsolète
  dans le changelog.
- **Décision (2026-10-27)** : préavis de 30 min (v2). `cancelLockSeconds` reste lisible mais sans effet ; noté comme obsolète dans le changelog.

### C2. `maxActiveBountiesPerSponsor` : 5 (v1) contre 3 (v2)
- Changer la valeur par défaut d'une clé existante. Sur un serveur existant, le fichier de config garde la valeur
  écrite (5) ; seul un nouveau fichier prendrait 3.
- **Recommandation** : passer le défaut à 3 et le noter dans le changelog. Les primes v1 au-delà de 3 restent actives
  (§0.3).
- **Décision (2026-10-27)** : défaut à 3. Les primes v1 en trop restent actives.

### C3. Une seule prime active par paire commanditaire-cible (v2) ; la v1 en autorise plusieurs
- **Impact** : un joueur ne peut plus empiler plusieurs primes sur la même cible. Les doublons v1 restent actifs
  jusqu'à leur fin (§0.3).
- **Recommandation** : appliquer, puisque la règle sert à borner le vol de cœurs.
- **Décision (2026-10-27)** : une seule prime active par paire. Les doublons v1 restent actifs jusqu'à leur fin.

### C4. Remise des récompenses : à réclamer dans le tableau (v1, décidé le 2026-10-18) contre automatique (v2)
- **v1** : gains et remboursements attendent dans l'onglet « À réclamer » (toast de notification, `/bounty claim`).
- **v2** : objet directement dans l'inventaire (au sol si plein), remise à la connexion si hors ligne. Pas d'onglet
  « À réclamer » dans la liste des onglets.
- **Impact données** : aucun, les livraisons existantes seraient remises à la connexion ou tout de suite.
- **Recommandation** : c'est toi qui avais demandé la réclamation manuelle. Je propose de **la garder** : la spec
  n'interdit pas l'onglet, et la mécanique du séquestre est la même.
- **Décision (2026-10-27)** : on garde « À réclamer » (onglet, toast, `/bounty claim`).

### C5. Interface : tableau « Ouverts / À réclamer / Historique » + écran de création (v1) contre affiches en grille (v2)
- **v2** : onglets « Avis de recherche » (grille d'affiches western avec portrait, tampons immunité, préavis, privée,
  éligibilité), « Créer », « Mes primes », « Historique », « Comment ça marche ».
- **Impact** : refonte complète des écrans. Aucun impact sur les données.
- **Recommandation** : appliquer la v2, en gardant l'onglet « À réclamer » si C4 = garder.
- **Décision (2026-10-27)** : interface v2, plus l'onglet « À réclamer ».

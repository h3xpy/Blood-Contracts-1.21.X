# Blood Contracts — Instructions pour Claude Code

Mod Minecraft **NeoForge 1.21.1** (Java 21) : système de primes sur les joueurs, financées exclusivement par les joueurs.
Mod ID : `bloodcontracts`. Package racine : à choisir (ex. `dev.bloodcontracts`), le garder cohérent partout.

Ce fichier décrit **quoi** construire et **comment le prouver**. Tu dois pouvoir développer et valider chaque mécanique **sans intervention humaine**, via les tests automatisés décrits en section 8. Une fonctionnalité n'est terminée que lorsque ses tests passent.

---

## 1. Contexte et objectif

Serveur multijoueur Create où chacun reste dans sa base. Le mod crée des raisons de sortir et de se battre, sans événements organisés par l'admin, sans zone de guerre permanente et sans détruire les constructions.

Un joueur (le **commanditaire**) engage un objet de son inventaire pour mettre une prime sur un autre joueur (la **cible**). Celui qui tue la cible (le **chasseur**) reçoit l'objet.

### Principes non négociables
1. **Le serveur ne crée jamais d'objet.** Toute récompense provient de l'inventaire d'un joueur. Aucune duplication, aucune disparition : à tout instant, un objet engagé est soit dans le séquestre, soit en livraison, soit chez un joueur, et jamais à deux endroits.
2. **Le serveur fait autorité.** Le client ne fait qu'envoyer des intentions. Il ne crée jamais d'objet et ne modifie jamais une récompense.
3. **Dans le doute, pas de récompense.** Si le responsable d'une mort n'est pas déterminé de façon fiable, la prime reste active.

### Hors périmètre (décision explicite)
- **Collusion ou comptes secondaires** : aucun objet n'est injecté, donc deux joueurs qui s'arrangent obtiennent le même résultat qu'en se donnant l'objet. Ne rien implémenter contre ça.
- Économie, monnaie, boutique.
- Protection anti-grief des bases (hors sujet pour ce mod).

---

## 2. Stack et dépendances

- NeoForge 1.21.1, Gradle avec **ModDevGradle** (`net.neoforged.moddev`).
- Java 21.
- **Create** (NeoForge 1.21.1, v6.0.x) : dépendance optionnelle.
- **Create Big Cannons (CBC)** : c'est lui qui ajoute les canons, pas Create. Il existe pour NeoForge 1.21.1 (versions 5.11.x), dépend de Create et de **Ritchie's Projectile Library**. Dépendance **optionnelle** : le mod doit fonctionner sans CBC ni Create. Récupérer les jars via Curse Maven ou Modrinth Maven, en `compileOnly` pour l'API et en runtime dans les runs de test.
- Toute intégration CBC ou Create vit dans un package `compat/` chargé seulement si `ModList.get().isLoaded(...)`.

---

## 3. Architecture

```
core/
  Bounty                 // record : id (UUID), sponsor, target, reward (ItemStack), createdAt, state, claimedBy, closedAt
  BountyState            // ACTIVE, CLAIMED, CANCELLED
  BountyManager          // seule API qui modifie l'état ; toute la logique métier
  BountySavedData        // persistance (SavedData sur l'Overworld)
  PendingDelivery        // objets dus à un joueur, livrés dès qu'il est en ligne
attribution/
  DamageLedger           // registre en mémoire des dégâts récents subis par chaque cible
  KillAttributor         // décide qui est responsable d'une mort (fonction pure autant que possible)
  ResponsibleResolver    // remonte DamageSource → joueur (projectiles, explosifs, familiers, compat)
compat/create/, compat/cbc/
network/                 // payloads client→serveur (intentions) et serveur→client (vues)
command/                 // /bounty ...
client/                  // écrans
config/                  // ModConfigSpec (serveur)
```

**Règle** : `BountyManager` est le seul point d'entrée qui modifie l'état. Les commandes, les payloads réseau et les événements l'appellent tous. Ainsi, un seul chemin est à tester.

---

## 4. Système de primes

### Création
- Entrées : commanditaire, UUID de la cible, emplacement d'inventaire source.
- Validations côté serveur : cible ≠ commanditaire, cible connue du serveur (en ligne ou déjà connectée), emplacement non vide, objet non blacklisté (config), quantité valide, limite de primes actives par commanditaire (config).
- L'objet est **retiré de l'inventaire et inscrit dans le séquestre au cours de la même opération**, avant de rendre la main. Si une validation échoue, rien ne bouge.
- Utiliser `ItemStack.copy()` et vider l'emplacement source. Ne jamais garder de référence partagée vers l'inventaire.
- Plusieurs primes peuvent viser la même cible.

### Visibilité
- Tout joueur peut lister les primes actives : cible, récompense (objet et quantité), date. Le nom du commanditaire est-il public ? Option de config `showSponsor`, `false` par défaut.

### Annulation
- Seul le commanditaire (ou un opérateur) peut annuler une prime `ACTIVE`.
- Passage `ACTIVE → CANCELLED`, puis l'objet part en **livraison** vers le commanditaire.
- Impossible d'annuler une prime déjà `CLAIMED`. L'opération est idempotente : une seconde annulation est refusée proprement.
- Délai anti-esquive configurable (`cancelLockSeconds`, 30 s par défaut) : impossible d'annuler si la cible a subi des dégâts d'un joueur éligible pendant ce délai. Cela évite d'annuler en plein combat.

### Remise
- À la mort de la cible, si `KillAttributor` désigne un chasseur fiable, **toutes** les primes `ACTIVE` sur cette cible dont le chasseur est éligible passent à `CLAIMED`. Chaque objet part en livraison vers le chasseur.
- Éligibilité : chasseur ≠ commanditaire de cette prime, chasseur ≠ cible. Une prime dont le tueur est le commanditaire reste `ACTIVE`.
- Ordre impératif : **changer l'état et créer la livraison, puis marquer les données comme modifiées, et seulement ensuite tenter la remise**.

### Livraison (`PendingDelivery`)
- Tout objet sortant du séquestre passe par là, qu'il s'agisse d'un gain ou d'une annulation (une livraison par pile).
- **Décision du 2026-10-18** : rien n'est poussé automatiquement dans l'inventaire. La livraison attend dans l'onglet
  « À réclamer » du tableau, une notification (toast, pas le chat) prévient le joueur, en ligne ou à sa connexion.
  `BountyManager.claimRewards` remet l'objet (inventaire, sinon au sol à ses pieds) puis supprime la livraison.
- Cela traite d'un même mécanisme le chasseur déconnecté, le commanditaire déconnecté et le crash entre la mort et la remise.

### Ajouts du 2026-10-18
- Une prime peut offrir jusqu'à 9 piles différentes (menu de création : 1 emplacement, « + objets » pour 9).
- Primes sur des joueurs hors ligne : tout joueur déjà connecté (registre persisté dans `BountySavedData`, complété
  au démarrage du serveur par les fichiers `playerdata/*.dat` du monde, nom depuis le cache de profils).
- Alerte plein écran « stressante » pour la cible en ligne (ou à sa connexion), hors chat ; le nom du commanditaire
  y figure si `alertRevealsSponsor` (true par défaut).

### Historique
- Les primes `CLAIMED` et `CANCELLED` restent dans le `SavedData`, avec chasseur et date, sans l'objet (il est livré). Taille bornée en config (`historySize`, 200 par défaut). Les plus anciennes sont purgées.

### Cas limites (comportements attendus)
| Situation | Comportement |
|---|---|
| Cible déconnectée | La prime reste active. Rien ne se passe. |
| Chasseur déconnecté après le kill | Livraison conservée, remise à la reconnexion. |
| Commanditaire déconnecté | La prime reste active. Il peut annuler plus tard. |
| Double `LivingDeathEvent` ou événement rejoué | La seconde occurrence ne trouve plus de prime `ACTIVE`, donc rien ne se passe. |
| Mort ambiguë ou environnementale | Aucune attribution. La prime reste active. |
| Cible tuée par le commanditaire | Sa propre prime reste active. Les primes des autres sont remises s'il est éligible pour elles. |
| Changement de dimension | Aucun impact : les données sont globales, indexées par UUID. |
| Crash ou redémarrage | Les données sont rechargées. Aucune prime n'est dupliquée et aucun objet n'est perdu. |

---

## 5. Attribution des éliminations (cœur technique)

### Principe
On ne se contente pas du dernier coup. On combine la source directe de la mort et un **registre des dégâts récents**.

### `DamageLedger`
- Pour chaque entité ayant au moins une prime active (pour limiter le coût), on enregistre à chaque dégât effectivement subi : `(UUID du responsable, gameTime, montant, type de dégât)`.
- Écouter `LivingDamageEvent.Post` (dégâts réellement appliqués). Vérifier les noms exacts des événements dans NeoForge 1.21.1 avant d'écrire le code.
- En mémoire seulement (pas de persistance). Purge des entrées plus vieilles que `attributionWindowTicks` (200 ticks = 10 s par défaut, en config) et purge à la mort ou à la déconnexion.

### `ResponsibleResolver` : de `DamageSource` à un joueur
1. `source.getEntity()` est un joueur : c'est lui (couvre les projectiles, la TNT allumée par un joueur, les potions lancées, etc.). **Ne jamais utiliser `getDirectEntity()` comme responsable** : c'est la flèche ou la TNT.
2. `source.getEntity()` est un `OwnableEntity` (loup apprivoisé, etc.) avec un propriétaire joueur : c'est le propriétaire (config `creditTamedPets`, `true` par défaut).
3. Projectile ou entité sans propriétaire : interroger les résolveurs de compatibilité (`compat/`).
4. Sinon : pas de responsable.

### `KillAttributor` : décision à la mort (`LivingDeathEvent`, priorité basse, côté serveur)
1. Si `ResponsibleResolver(source mortelle)` donne un joueur, il est retenu.
2. Sinon, si la source mortelle est environnementale (vide, chute, lave, feu, noyade, `/kill`, etc.), on consulte le registre : le joueur le plus récent ayant infligé des dégâts dans la fenêtre est retenu. Le vanilla fait déjà ce raisonnement pour « knocked into the void by X » via `lastHurtByPlayer` et le `CombatTracker`. S'en inspirer, mais garder notre propre registre (fenêtre configurable, testable).
3. Sinon : **aucune attribution**.
4. `/kill` et les morts par commande ne donnent jamais d'attribution, même avec un registre non vide.

Garder `KillAttributor` aussi pur que possible (entrées : source simplifiée et contenu du registre ; sortie : `Optional<UUID>`) pour le tester en JUnit sans lancer Minecraft.

### Create et Create Big Cannons
- **Ne pas supposer** que le propriétaire d'une machine est responsable de ses victimes.
- **Étape obligatoire avant de coder la compat CBC** : lire les sources de CBC (et de Ritchie's Projectile Library) pour savoir comment les projectiles de canon sont créés, s'ils ont un propriétaire (`Projectile#getOwner`), quelle `DamageSource` ils produisent à l'impact et à l'explosion, et ce que fait un tir déclenché par redstone. Résumer les conclusions dans `docs/cbc-attribution.md`.
- Stratégie attendue si les projectiles n'ont pas de propriétaire joueur :
  - Mémoriser, par monture de canon, le **dernier joueur ayant interagi directement** avec elle (assemblage, visée, chargement, levier ou bouton adjacent, selon ce que les sources permettent d'observer), avec un horodatage.
  - Au tir, marquer le projectile (data attachment NeoForge) avec ce joueur **uniquement si l'interaction date de moins de `cannonOperatorWindowTicks`** (config).
  - Les explosions de ce projectile héritent du marquage.
  - Un tir automatique ou par redstone sans interaction récente : **aucune attribution**.
- Même logique d'enquête pour les autres sources Create capables de tuer (contraptions, scies, etc.). Par défaut : aucune attribution.

---

## 6. Persistance et sécurité des données

- `BountySavedData` attaché à l'Overworld (`level.getDataStorage().computeIfAbsent(...)` avec la `SavedData.Factory` de 1.21.1). Sérialiser les `ItemStack` avec le `HolderLookup.Provider` (composants de données 1.21.1).
- Toute mutation passe par `BountyManager` et appelle `setDirty()`.
- Toutes les mutations ont lieu sur le thread serveur (les handlers réseau doivent passer par `context.enqueueWork`).
- Les primes et les livraisons sont dans **le même** `SavedData`, sauvegardées ensemble. Ainsi, un crash ne peut pas sauvegarder « prime réclamée » sans sauvegarder « livraison due ».
- Le chargement doit tolérer un objet dont l'item n'existe plus (mod retiré) : le logger, garder les données brutes, ne pas crasher.

---

## 7. Interface, réseau et commandes

### Commandes (`/bounty`)
- `place <joueur>` : utilise l'objet tenu en main principale.
- `list`, `info <id>`, `cancel <id>`, `history`.
- Opérateur : `admin cancel <id>`, `admin list`.

### Interface
- Écran de liste (primes actives), écran de détail, écran de création (sélection de la cible et emplacement de récompense), onglet historique.
- L'emplacement de récompense est un `Slot` de menu serveur (`AbstractContainerMenu`). L'objet est réellement déplacé côté serveur et rendu si le menu est fermé sans confirmation. Jamais d'ItemStack envoyé par le client.

### Réseau
- Payloads client vers serveur = **intentions** uniquement : `CreateBountyRequest(targetUUID)` (l'objet vient du slot du menu ouvert côté serveur), `CancelBountyRequest(bountyId)`, `RequestBountyList`.
- Chaque handler revalide tout côté serveur (menu ouvert, droits, état de la prime) et appelle `BountyManager`.

---

## 8. Tests automatisés (obligatoires)

Objectif : **chaque mécanique est prouvée par un test qui tourne sans humain**. Après chaque modification, lancer les tests et corriger jusqu'à ce que tout passe. Ne jamais désactiver ou affaiblir un test pour le faire passer.

### 8.1 Deux niveaux
1. **JUnit 5** (`src/test/java`, `./gradlew test`) pour la logique pure : machine à états de `Bounty`, `KillAttributor` avec registre simulé, purge du registre, règles d'éligibilité, sérialisation sans monde.
2. **GameTests NeoForge** (`./gradlew runGameTestServer`) pour tout ce qui touche au jeu : vrais dégâts, vraies morts, inventaires, `SavedData`, projectiles. Le serveur de GameTest démarre, exécute les tests puis **s'arrête avec un code non nul en cas d'échec**. C'est le signal à utiliser.

### 8.2 Configuration GameTest
- Dans `build.gradle`, déclarer un run `gameTestServer` (ModDevGradle) avec `systemProperty 'neoforge.enabledGameTestNamespaces', 'bloodcontracts'`.
- Enregistrer les tests via `RegisterGameTestsEvent` (classes annotées `@GameTestHolder("bloodcontracts")`, méthodes `@GameTest`, `@PrefixGameTestTemplate(false)` avec un template vide fourni dans `src/main/resources/data/bloodcontracts/structure/` ou généré).
- Les tests ne sont chargés que dans ce run (aucun impact en production).
- Joueurs simulés : `GameTestHelper#makeMockServerPlayerInLevel()` (vérifier le nom exact dans 1.21.1) ou `FakePlayerFactory`. Créer des helpers `TestPlayers.sponsor()`, `hunter()`, `target()`.
- Un second run `gameTestServerCompat` qui inclut Create, CBC et Ritchie's Projectile Library pour les tests de compat. Le run principal tourne **sans** ces mods pour prouver qu'ils sont optionnels.
- Chaque test réinitialise `BountyManager` (méthode `resetForTests()` réservée aux tests) pour éviter les interférences.

### 8.3 Comment simuler les situations
- Coup direct : `target.hurt(level.damageSources().playerAttack(hunter), …)` jusqu'à la mort.
- Projectile : faire apparaître une flèche avec `setOwner(hunter)` dirigée vers la cible, attendre l'impact (`helper.succeedWhen`).
- TNT : `PrimedTnt` avec `owner = hunter`, cible à côté.
- Vide : coup de `hunter`, puis `target.hurt(level.damageSources().fellOutOfWorld(), …)` dans la fenêtre, puis la même chose hors fenêtre (avancer le temps avec `helper.runAfterDelay`).
- Environnement : lave, chute ou noyade sans dégât de joueur préalable.
- Redémarrage : sérialiser `BountySavedData` en `CompoundTag`, créer une instance neuve, recharger, comparer.
- Déconnexion ou reconnexion : appeler directement les handlers de `BountyManager` utilisés par `PlayerLoggedOutEvent` et `PlayerLoggedInEvent` (rendre ces points d'entrée appelables).
- Réseau : appeler les handlers de payload côté serveur avec des données valides et invalides (id inexistant, prime d'un autre, cible = soi), sans client.

### 8.4 Matrice de tests à couvrir (au minimum)
**Cycle de vie et séquestre**
- [ ] Créer une prime retire exactement l'objet de l'inventaire et le place en séquestre (même item, mêmes composants, même quantité).
- [ ] Création refusée si cible = soi, emplacement vide ou objet blacklisté. Dans tous ces cas, l'inventaire est inchangé.
- [ ] Annulation : l'objet revient au commanditaire, la prime passe `CANCELLED` et figure dans l'historique.
- [ ] Double annulation : la seconde est refusée et aucun objet n'est dupliqué.
- [ ] Annulation refusée pendant `cancelLockSeconds` après des dégâts d'un joueur éligible.
- [ ] **Conservation** : sur chaque scénario, la somme des objets (inventaires + séquestre + livraisons + objets au sol) est identique avant et après.

**Attribution**
- [ ] Mort par épée : le chasseur est crédité.
- [ ] Mort par flèche : le tireur est crédité (et non la flèche).
- [ ] Mort par TNT allumée par un joueur : ce joueur est crédité.
- [ ] Coup puis mort dans le vide dans la fenêtre : le chasseur est crédité.
- [ ] Coup puis mort dans le vide **hors** fenêtre : personne, la prime reste active.
- [ ] Deux attaquants : le plus récent dans la fenêtre est crédité (ou la règle choisie, documentée).
- [ ] Lave, chute ou noyade sans joueur : aucune attribution.
- [ ] `/kill` : aucune attribution.
- [ ] Loup apprivoisé : le propriétaire est crédité si `creditTamedPets`, personne sinon.

**Anti-abus et robustesse**
- [ ] Le commanditaire tue la cible : sa prime reste `ACTIVE`.
- [ ] Plusieurs primes sur la même cible, de commanditaires différents : toutes sont remises au chasseur éligible ; celle du chasseur-commanditaire reste active.
- [ ] `LivingDeathEvent` déclenché deux fois : une seule remise.
- [ ] Chasseur hors ligne au moment de la remise : livraison conservée, remise à la reconnexion, une seule fois.
- [ ] Inventaire plein : l'objet est lâché au sol, pas perdu.
- [ ] Persistance : primes actives, historique et livraisons survivent à un aller-retour de sérialisation.
- [ ] Item inconnu au chargement : pas de crash.

**Compat (run `gameTestServerCompat`)**
- [ ] Le mod charge sans Create ni CBC (run principal).
- [ ] Projectile CBC tiré après interaction récente d'un joueur avec le canon : ce joueur est crédité.
- [ ] Tir CBC par redstone sans interaction récente : aucune attribution.
- [ ] Explosion d'obus CBC : même règle que l'impact.
- [ ] Les tests précis sont à ajuster d'après `docs/cbc-attribution.md`. Si une situation est impossible à simuler, le documenter et tester au niveau le plus proche (appel direct du résolveur avec un projectile CBC construit).

**Réseau**
- [ ] Payloads invalides : refusés, état inchangé, aucun objet créé.

### 8.5 Boucle de travail
1. Lire ce fichier.
2. Mettre en place le projet et le run GameTest avec un test trivial qui passe.
3. Implémenter mécanique par mécanique, **test d'abord** autant que possible.
4. Après chaque étape : `./gradlew test` puis `./gradlew runGameTestServer` (et `runGameTestServerCompat` quand la compat existe).
5. Tenir `docs/TEST_STATUS.md` à jour : liste de la matrice ci-dessus avec ✅ ou ❌ et une note.
6. Ne demander l'intervention humaine que pour ce qui ne peut pas être automatisé (rendu visuel des écrans). Le signaler dans `TEST_STATUS.md`.

---

## 9. Configuration serveur (`ModConfigSpec`)
- `attributionWindowTicks` (200)
- `cannonOperatorWindowTicks` (à fixer après l'étude CBC, ~200)
- `cancelLockSeconds` (30)
- `maxActiveBountiesPerSponsor` (5)
- `rewardBlacklist` (liste d'items)
- `creditTamedPets` (true)
- `showSponsor` (false)
- `historySize` (200)

## 10. Conventions
- Vérifier les API NeoForge 1.21.1 dans les sources ou la doc avant de les utiliser ; ne pas se fier à des noms issus d'autres versions (1.20.x, Forge).
- Pas de mixin si un événement NeoForge suffit. Si un mixin est nécessaire (compat CBC), le limiter au strict minimum et le documenter.
- Logs de debug derrière un flag de config, avec l'identifiant de prime et les UUID.
- Commits petits et fréquents, chacun avec ses tests au vert.

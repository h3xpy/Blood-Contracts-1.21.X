# Blood Contracts — Instructions pour Claude Code (mise à jour de la v1)

Mod Minecraft **NeoForge 1.21.1** (Java 21) : système de primes sur les joueurs, financées exclusivement par les joueurs.
Mod ID : `bloodcontracts`.

**Une v1 de ce mod existe déjà et tourne sur un serveur en production.** Ce fichier décrit la version cible. Ta mission : amener le code existant à cette spécification, sans perdre de données du serveur, et prouver chaque mécanique par des tests automatisés qui tournent sans intervention humaine.

---

## 0. Procédure de mise à jour (à suivre dans l'ordre)

1. **Audit avant toute modification.** Lis tout le code existant et compare-le à ce document. Produis `docs/AUDIT_V1.md` avec trois listes :
   - ✅ déjà conforme ;
   - 🔧 à modifier ou à ajouter (avec la section de ce document concernée) ;
   - ❓ présent dans la v1 mais absent de ce document.
2. **En cas de conflit avec la v1, demande-moi avant d'intégrer.** Il y a conflit dès que ce document impose un comportement différent de celui de la v1 sur une même mécanique (valeurs, règles, format des données, commandes, interface), ou qu'une fonctionnalité ❓ le contredit. Dans ce cas :
   - ne modifie ni ne supprime rien de la v1 sur ce point ;
   - liste le conflit dans une section **⚠️ Conflits** de `docs/AUDIT_V1.md` : ce que fait la v1, ce que demande ce document, l'impact pour les joueurs et sur les données, et ta recommandation ;
   - pose-moi la question et attends ma décision. Note ensuite la décision dans l'audit ;
   - en attendant, continue sur les points sans conflit.
   Les ajouts purs (mécaniques absentes de la v1 et qui ne changent rien d'existant) peuvent être intégrés sans demander. Garde toute fonctionnalité ❓ qui ne contredit pas ce document.
3. **Données de production.** Repère le format actuel de sauvegarde (SavedData, attachments, fichiers). Écris une migration v1 → v2 versionnée (champ `dataVersion`) qui :
   - conserve toutes les primes actives et **tous les objets engagés** (aucune perte, aucune duplication) ;
   - convertit les anciennes primes au mode `PUBLIC` si la v1 n'avait pas de mode ;
   - si des primes v1 violent les nouvelles limites (§4), ne les supprime pas : elles restent actives jusqu'à leur fin, les limites s'appliquent seulement aux nouvelles créations ;
   - est couverte par un test qui charge une sauvegarde v1 réelle ou reconstruite.
4. Mets en place les tests (§8) **avant** les grosses modifications, pour verrouiller le comportement existant qui reste valable.
5. Implémente les écarts 🔧 un par un, en suivant le principe test d'abord. Après chaque étape : `./gradlew test` puis `./gradlew runGameTestServer`.
6. Tiens `docs/TEST_STATUS.md` à jour (matrice §8.4 avec ✅/❌).
7. À la fin, écris `docs/CHANGELOG_V2.md` : ce qui a changé pour les joueurs, ce qui a changé dans la config, et les étapes de déploiement (sauvegarde du monde avant mise à jour).

---

## 1. Contexte et objectif

Serveur multijoueur Create où chacun reste dans sa base. Le mod crée des raisons de sortir et de se battre, sans événements organisés par l'admin, sans zone de guerre permanente et sans détruire les constructions.

Un joueur (le **commanditaire**) engage un objet de son inventaire pour mettre une prime sur un autre joueur (la **cible**). Celui qui tue la cible (le **chasseur**) reçoit l'objet ; la cible perd temporairement un cœur au profit du commanditaire.

### Principes non négociables
1. **Le serveur ne crée jamais d'objet.** Toute récompense provient de l'inventaire d'un joueur. À tout instant, un objet engagé est soit dans le séquestre, soit en livraison, soit chez un joueur, et jamais à deux endroits.
2. **Les cœurs ne sont ni créés ni détruits** : tout cœur perdu par une cible est gagné par un commanditaire.
3. **Le serveur fait autorité.** Le client n'envoie que des intentions. Il ne crée jamais d'objet et ne modifie jamais une récompense.
4. **Dans le doute, pas de récompense.** Si le responsable d'une mort n'est pas déterminé de façon fiable, la prime reste active.
5. **Aucun arbitrage admin.** Le système fonctionne seul. Les opérateurs ont seulement des commandes de maintenance.

### Décisions fermées (ne pas réintroduire)
- **Collusion côté commanditaire** (le commanditaire s'arrange avec le chasseur) : **acceptée volontairement**, y compris avec le transfert de cœurs. Elle force des combats, ce qui est le but du mod. Ses effets sont bornés uniquement par le plancher, le plafond et l'immunité (§5).
- **Collusion côté cible** (un allié tue la cible pour encaisser la prime d'un tiers) : traitée **uniquement** par les primes privées (§4), la perte de cœur, l'immunité et l'historique public. Il ne faut pas ajouter :
  - de détection via des mods d'équipes ou de claims (non utilisés, alliances non officielles) ;
  - d'arbitrage ni de validation admin ;
  - de sanction automatique ;
  - de destruction d'objets de la cible à la mort (trop inégal selon l'objet détruit).
- Pas de mode « tout le monde sauf X » : seulement deux types de prime, publique ou privée.
- Hors périmètre : économie, monnaie, boutique, protection des bases.

---

## 2. Stack et dépendances

- NeoForge 1.21.1, Gradle avec ModDevGradle (`net.neoforged.moddev`). Si la v1 utilise un autre plugin (NeoGradle), le garder sauf blocage réel.
- Java 21.
- **Create** (v6.0.x) : dépendance optionnelle.
- **Create Big Cannons (CBC)** : c'est lui qui ajoute les canons, pas Create. Il existe pour NeoForge 1.21.1 (5.11.x), dépend de Create et de **Ritchie's Projectile Library**. Dépendance optionnelle (`compileOnly` pour l'API, chargée dans le run de test compat).
- Toute intégration Create ou CBC vit dans `compat/`, chargée seulement si `ModList.get().isLoaded(...)`.
- Vérifie les API NeoForge 1.21.1 dans les sources avant de les utiliser ; ne te fie pas à des noms issus de 1.20.x ou de Forge.

---

## 3. Architecture cible

```
core/
  Bounty               // id, sponsor, target, reward (ItemStack), type, hunters (Set<UUID>), createdAt, state, cancelAt|null, claimedBy, closedAt
  BountyType           // PUBLIC, PRIVATE
  BountyState          // ACTIVE, CLAIMED, CANCELLED
  BountyManager        // SEULE API qui modifie l'état
  BountySavedData      // primes, livraisons, transferts de cœurs, immunités, historique, compteurs de paires
  PendingDelivery
  HeartTransfer        // id, from, to, remainingHp, createdAt
  TimeSource           // horloge temps réel injectable
attribution/
  DamageLedger, KillAttributor, ResponsibleResolver
compat/create/, compat/cbc/
network/, command/, client/, config/
```

Adapte les noms à la v1 si elle a déjà une structure équivalente ; ce qui compte, c'est qu'un **seul point d'entrée** (`BountyManager` ou son équivalent) modifie l'état, et que les commandes, payloads et événements passent tous par lui.

---

## 4. Primes

### Types
- **`PUBLIC`** : tout joueur peut encaisser, sauf le commanditaire et la cible.
- **`PRIVATE`** : seuls les joueurs de la liste de chasseurs peuvent encaisser. La liste contient au moins un joueur et au plus `maxHunterListSize` (20), sans le commanditaire ni la cible.

### Modification d'une prime active : élargir seulement
Une modification ne doit jamais pouvoir priver un chasseur de sa récompense au dernier moment.
- **Autorisé** à tout moment tant que la prime est `ACTIVE` : ajouter des chasseurs à une prime privée, passer de `PRIVATE` à `PUBLIC`.
- **Interdit** : retirer un chasseur, passer de `PUBLIC` à `PRIVATE`.
- Pour restreindre, le commanditaire doit **annuler puis recréer** la prime : l'ancienne reste encaissable pendant tout le préavis de 30 min.
- L'éligibilité est évaluée au moment de la mort, avec la liste en vigueur.

### Création
- Validations serveur : cible ≠ commanditaire, cible connue du serveur, emplacement non vide, objet non blacklisté, liste valide si `PRIVATE`.
- **Une seule prime active par paire commanditaire-cible** (obligatoire, non configurable : sinon on empile plusieurs primes pour voler plusieurs cœurs en une mort).
- Au plus `maxActiveBountiesPerSponsor` primes actives par commanditaire (3 par défaut), pour éviter d'encombrer la liste.
- Pas de limite par cible : plusieurs commanditaires peuvent viser la même cible.
- L'objet est retiré de l'inventaire et inscrit au séquestre dans la même opération (`ItemStack.copy()`, emplacement vidé). Si une validation échoue, rien ne bouge.
- Une prime peut être créée sur une cible immunisée (§5).

### Annulation avec préavis
Une prime ne disparaît jamais d'un coup : un chasseur qui a commencé sa traque doit avoir le temps de finir.
- Le commanditaire demande l'annulation d'une prime `ACTIVE` : la prime **reste `ACTIVE` et encaissable** pendant `cancelNoticeMinutes` (30, temps réel). On enregistre `cancelAt = maintenant + préavis`.
- Pendant le préavis, la prime est affichée à tous avec un compte à rebours (« expire dans 23 min »). Elle peut être remportée normalement : remise de l'objet, cœur, immunité.
- À l'échéance, si elle n'a pas été remportée, elle passe `CANCELLED` et l'objet part en livraison vers le commanditaire. Les échéances sont vérifiées périodiquement (~20 s) et au démarrage du serveur : un préavis échu pendant un arrêt est finalisé au redémarrage.
- Pendant le préavis, le commanditaire peut **retirer sa demande d'annulation** : `cancelAt` repasse à null. Il peut aussi continuer à élargir la prime.
- Une seconde demande d'annulation pendant le préavis ne change rien et ne raccourcit jamais le délai.
- Une prime en préavis compte toujours dans les limites (une par paire commanditaire-cible, `maxActiveBountiesPerSponsor`).
- Opérateurs : `admin cancel` est immédiat (maintenance).
- Il n'y a plus ni durée minimale ni verrou de combat : le préavis couvre les deux cas. Si la v1 en a, c'est un conflit : demande avant de les retirer (§0).

### Remise
- À la mort de la cible (hors immunité), si `KillAttributor` désigne un chasseur fiable, **toutes** les primes `ACTIVE` sur la cible pour lesquelles il est éligible (y compris celles en préavis d'annulation) passent à `CLAIMED`, dans l'ordre de création.
- Éligibilité : chasseur ≠ commanditaire, chasseur ≠ cible, et chasseur autorisé par le type de la prime.
- Ordre impératif dans une seule mutation : changement d'état, création des livraisons, transferts de cœurs, immunité, historique, puis `setDirty()`. La remise physique ne vient qu'ensuite.

### Livraison
- Tout objet qui sort du séquestre passe par `PendingDelivery`.
- Joueur en ligne : l'objet va dans l'inventaire, ou au sol s'il est plein. Joueur hors ligne : livraison conservée et remise à la connexion.

### Visibilité
- Liste des primes actives visible par tous : cible, récompense, type, date, immunité éventuelle avec temps restant, et un indicateur personnel « vous pouvez / ne pouvez pas encaisser ».
- Commanditaire masqué par défaut (`showSponsor` = false).
- **La liste des chasseurs d'une prime privée n'est visible que par son commanditaire et par les opérateurs.** Elle n'est jamais envoyée à un autre client.

### Historique et paires récurrentes
- Historique public des primes remportées (cible, chasseur, récompense, type, date) et annulées. Taille bornée (`historySize`, 200).
- Compteur par paire (chasseur, cible) du nombre de primes encaissées, conservé indépendamment de la purge de l'historique. Au-delà de `repeatPairThreshold` (2), il s'affiche sur la fiche de la cible et dans l'écran de création (« Y a encaissé 3 primes sur X »). C'est une information seulement, sans conséquence automatique.

---

## 5. Cœurs et immunité

### Transfert
- Une mort qui fait remporter au moins une prime coûte à la cible **au plus `maxHeartsLostPerDeath` (1) cœur**, même si plusieurs primes sont remportées. 1 cœur = 2 PV.
- Ce cœur va au commanditaire de la prime remportée **la plus ancienne**. Si ce commanditaire est au plafond, il va au suivant dans l'ordre de création. Les autres primes remportées donnent seulement leur objet.
- Solde **net** par joueur, borné : jamais sous `-maxHeartsLost` (5), jamais au-dessus de `+maxHeartsGained` (5).
- Un transfert est réduit, voire annulé, pour respecter les deux bornes : la cible ne perd jamais un cœur que personne ne reçoit. L'objet de la prime est remis dans tous les cas.

### Retour progressif (`heartRegenMode`)
- `PROGRESSIVE` (par défaut) : les cœurs reviennent **petit à petit**, jamais d'un coup.
  - Les cœurs reviennent **plus vite quand la cible est connectée** que lorsqu'elle est hors ligne, pour pousser à jouer sans pénaliser les absences :
    - connectée : 1 PV (un demi-cœur) toutes les `heartRegenOnlineStepMinutes` (90) de jeu ;
    - hors ligne : 1 PV toutes les `heartRegenOfflineStepMinutes` (720 = 12 h) réelles.
  - Modèle : chaque cible qui a des transferts en cours possède une **jauge de progression** (nombre réel, persisté). Pour chaque intervalle de temps réel écoulé, la jauge augmente de `durée / onlineStep` si la cible était connectée, ou de `durée / offlineStep` si elle était hors ligne. Chaque fois que la jauge atteint 1, on retire 1 de la jauge et **1 PV** revient à la cible, prélevé sur son transfert en cours **le plus ancien** ; le commanditaire de ce transfert perd ce même PV.
  - La jauge est mise à jour à chaque changement d'état (connexion, déconnexion), à chaque vérification périodique et au démarrage du serveur. On stocke `regenProgress`, `lastRegenUpdate` (horodatage `TimeSource`) et on considère la cible hors ligne pendant tout arrêt du serveur.
  - La jauge démarre à 0 quand la cible passe de « aucun transfert en cours » à « au moins un ». Une nouvelle perte ne la remet pas à zéro. Quand tout est rendu, elle s'arrête et repasse à 0.
  - Le calcul est déterministe et rattrapable : à la connexion et au démarrage, on applique tous les pas échus d'un coup (un PV par pas, jamais plus que dû).
- `PERMANENT` : aucun retour.
- Changer de mode s'applique aux transferts existants à partir du changement.

### Cohérence avec l'immunité (valeurs par défaut, à garder cohérentes si on les modifie)
Contexte : les joueurs sont étudiants. Ils jouent surtout en soirée en semaine (2 à 3 h), certains font de longues sessions le week-end, d'autres ne jouent pas le week-end (révisions).
- **L'immunité est en temps réel** (et non en temps de jeu) : se déconnecter la gaspille. Elle pousse la cible à profiter de sa protection pour jouer.
- Perte maximale : 1 cœur toutes les 2 h réelles (`maxHeartsLostPerDeath` + `targetImmunityMinutes`).
- Retour en jouant : 1 cœur pour 3 h de jeu, donc **plus lent que la perte maximale**. Un joueur tué en boucle pendant une longue session perd du terrain, et l'immunité reste le vrai frein. Un cœur perdu en début de soirée revient pendant la soirée suivante.
- Retour hors ligne : 1 cœur par 24 h. Un joueur qui révise tout le week-end récupère quand même ; le plancher (5 cœurs) se récupère en 5 jours sans jouer, ou en 15 h de jeu.
- Exemple de soirée : cœur perdu à 21 h, 2 h de jeu jusqu'à 23 h (≈ 1,3 PV), puis 20 h hors ligne (≈ 1,7 PV) : le cœur est revenu à la connexion du lendemain.
- Au démarrage, logger un avertissement si `heartRegenOnlineStepMinutes × 2 ≤ targetImmunityMinutes` (les cœurs reviendraient en jouant au moins aussi vite qu'on peut les perdre : système sans effet), ou si `heartRegenOfflineStepMinutes < heartRegenOnlineStepMinutes` (être hors ligne serait avantageux).

### Implémentation
- Source de vérité : la liste des `HeartTransfer` dans `BountySavedData`. Le solde est **calculé**, jamais stocké à part.
- `HeartTransfer` stocke un montant en PV restants (`remainingHp`), décrémenté par le retour progressif ; le transfert est supprimé à 0.
- Application via un `AttributeModifier` sur `Attributes.MAX_HEALTH`, id `bloodcontracts:heart_balance`, `ADD_VALUE`, valeur = solde en PV. Modificateur transitoire **recalculé** à la connexion, à la réapparition, au clonage, à chaque transfert et à chaque pas de retour.
- Vie actuelle ramenée au nouveau maximum si nécessaire, sans jamais tuer le joueur.
- Mise à jour des jauges toutes les ~20 s pour les joueurs connectés, et au démarrage ; les joueurs hors ligne sont recalculés à leur connexion (au taux hors ligne).
- L'état connecté ou hors ligne est déterminé par le serveur (événements de connexion et de déconnexion), jamais par le client.
- Ne touche qu'à notre propre modificateur.

### Immunité
- Après une mort où au moins une prime a été remportée, la cible est immunisée pendant `targetImmunityMinutes` (120, **temps réel**, qu'elle soit connectée ou non).
- Pendant l'immunité, aucune prime ne peut être remportée sur elle : pas de remise, pas de transfert, et les primes restent `ACTIVE`.
- L'immunité est persistée et survit aux redémarrages.

### Horloge
Tout ce qui se mesure en temps réel (préavis d'annulation, immunité, retour des cœurs) passe par `TimeSource`. Les tests utilisent une horloge contrôlable.

---

## 6. Attribution des éliminations

### `DamageLedger`
- Pour chaque entité qui a au moins une prime active : à chaque dégât réellement subi (`LivingDamageEvent.Post`, à vérifier dans 1.21.1), on enregistre `(UUID du responsable, gameTime, montant, type)`.
- En mémoire seulement. Purge au-delà de `attributionWindowTicks` (200 = 10 s), à la mort et à la déconnexion.

### `ResponsibleResolver` : de `DamageSource` à un joueur
1. `source.getEntity()` est un joueur : c'est lui. **Jamais `getDirectEntity()`** (ce serait la flèche ou la TNT).
2. `source.getEntity()` est un `OwnableEntity` dont le propriétaire est un joueur : c'est le propriétaire, si `creditTamedPets` (true).
3. Projectile ou entité sans propriétaire : on interroge les résolveurs de `compat/`.
4. Sinon : personne.

### `KillAttributor` (`LivingDeathEvent`, côté serveur)
1. Si la source mortelle désigne un joueur, c'est lui.
2. Sinon, pour une source environnementale (vide, chute, lave, feu, noyade…) : le joueur le plus récent du registre dans la fenêtre.
3. Sinon : personne.
4. `/kill` et les morts par commande ne donnent jamais rien.

Garde-le aussi pur que possible pour le tester en JUnit.

### Create Big Cannons
- **Étape obligatoire avant de coder** : lis les sources de CBC et de Ritchie's Projectile Library pour savoir si les obus ont un propriétaire, quelle `DamageSource` ils produisent à l'impact et à l'explosion, et ce que fait un tir par redstone. Documente-le dans `docs/cbc-attribution.md`.
- Stratégie si les obus n'ont pas de propriétaire joueur : mémoriser, par monture de canon, le dernier joueur ayant interagi directement avec elle (avec horodatage). Au tir, marquer le projectile (data attachment) avec ce joueur **seulement si l'interaction date de moins de `cannonOperatorWindowTicks`**. Les explosions héritent du marquage.
- Un tir automatique ou par redstone sans interaction récente ne donne **aucune attribution**. Le propriétaire d'une machine n'est jamais responsable par défaut.
- Mêmes règles pour les autres sources Create (contraptions, scies…) : par défaut, aucune attribution.

---

## 7. Persistance, interface, réseau, commandes

### Persistance
- Tout est dans `BountySavedData` sur l'Overworld, avec un champ `dataVersion` et la migration v1 décrite en §0.
- `ItemStack` sérialisés avec le `HolderLookup.Provider` (composants de données 1.21.1).
- Toutes les mutations passent par `BountyManager`, sur le thread serveur (`enqueueWork` pour le réseau), suivies de `setDirty()`.
- Un item inconnu au chargement (mod retiré) ne fait pas crasher : on le logge et on conserve les données brutes.

### Commandes `/bounty`
- `place <joueur> [public|private <joueurs...>]` : avec l'objet tenu en main principale.
- `addhunter <id> <joueur>`, `makepublic <id>`.
- `list`, `info <id>`, `history`.
- `cancel <id>` (lance le préavis), `resume <id>` (retire la demande d'annulation).
- Opérateurs : `admin cancel <id>`, `admin list`, `admin hearts <joueur>` (consultation).

### Interface
Onglets : **Avis de recherche**, **Créer**, **Mes primes**, **Historique**, **Comment ça marche**.

**Avis de recherche (écran principal) : des affiches, pas une liste.**
- Chaque prime active est une **affiche de style « avis de recherche » de western**, dans une grille paginée : fond parchemin vieilli (texture du mod, bords déchirés ou punaisés, légère rotation aléatoire mais stable par prime), titre « RECHERCHÉ », **portrait de la cible rendu depuis son skin**, son pseudo, la récompense (objet rendu en grand avec la quantité), et un tampon « PRIVÉE » pour les primes privées.
- Le portrait utilise le skin réel du joueur, y compris s'il est hors ligne : le serveur envoie le `GameProfile` (avec ses propriétés de textures) et le client charge le skin via le `SkinManager`. Afficher le visage, avec la couche chapeau, à grande échelle, ou le buste si c'est faisable proprement. Prévoir le skin par défaut en attendant le chargement.
- Indications visuelles directement sur l'affiche :
  - tampon ou bandeau « IMMUNISÉ — 1 h 12 » si la cible est immunisée ;
  - compte à rebours « EXPIRE DANS 23 MIN » en rouge pendant un préavis d'annulation ;
  - pastille « Vous pouvez encaisser » ou affiche grisée si le joueur n'est pas éligible.
- Plusieurs primes sur la même cible : une seule affiche, qui montre toutes les récompenses (objets empilés) et leur nombre.
- Animation légère au survol (l'affiche se redresse et grossit un peu). Clic pour ouvrir le détail.
- Tri : récompenses éligibles d'abord, puis les plus récentes.

**Détail** : grande affiche, toutes les primes sur la cible, paires récurrentes (« Y a encaissé 3 primes sur X »), et pour le commanditaire : la liste des chasseurs et les boutons élargir, annuler et reprendre.

**Créer** : choix de la cible (joueurs connus avec leur tête), type public ou privé, sélection des chasseurs, emplacement de récompense. Avertissement sur une prime publique : « Un allié de la cible pourrait l'encaisser. »

**Historique** : même style, avec des affiches tamponnées « ENCAISSÉE PAR Y » ou « RETIRÉE ».

**Comment ça marche** : un onglet court et illustré, en français, qui explique aux joueurs :
- placer une prime (l'objet est mis en séquestre ; le serveur ne crée rien) ;
- prime publique et prime privée, et l'élargissement seulement ;
- l'annulation avec un préavis de 30 min ;
- qui est crédité d'un kill (coup direct, projectile, vide après un coup, canon manipulé ; une mort environnementale ne compte pas) ;
- les cœurs (−1 pour la cible, +1 pour le commanditaire, plancher et plafond ; retour d'un demi-cœur toutes les 1 h 30 de jeu, ou toutes les 12 h hors ligne : jouer fait revenir les cœurs plus vite) ;
- l'immunité de 2 h en temps réel (se déconnecter la gaspille).
Les valeurs affichées viennent de la config serveur envoyée au client, jamais codées en dur. Textes dans des fichiers de langue (`fr_fr`, et `en_us`).

**Style** : cohérent sur tous les onglets (parchemin, encre, rouge sang pour les accents), textures dans `assets/bloodcontracts/textures/gui/`. Visuels 100 % originaux du mod.

**Technique** : l'emplacement de récompense est un `Slot` d'un menu serveur. L'objet est rendu si le menu est fermé sans confirmation. Le client n'envoie jamais d'`ItemStack`. Le rendu est purement client ; toutes les données viennent des vues envoyées par le serveur.

### Réseau
- Payloads client→serveur = intentions : `CreateBountyRequest(target, type, hunters)`, `AddHuntersRequest(id, hunters)`, `MakePublicRequest(id)`, `CancelBountyRequest(id)`, `ResumeBountyRequest(id)`, `RequestBountyList`.
- Les vues de primes incluent le `GameProfile` de la cible (textures du skin), l'état d'immunité, le `cancelAt` éventuel et l'éligibilité personnelle. Les valeurs de config utiles à l'onglet d'aide sont envoyées à la connexion.
- Tous revalidés côté serveur. Aucun payload ne permet de retirer un chasseur ou de rendre une prime privée.
- Les vues envoyées aux clients ne contiennent jamais la liste des chasseurs d'une prime dont le destinataire n'est pas le commanditaire.

---

## 8. Tests automatisés (obligatoires)

Chaque mécanique est prouvée par un test qui tourne sans humain. Ne désactive ni n'affaiblis jamais un test pour le faire passer.

### 8.1 Niveaux
1. **JUnit 5** (`./gradlew test`) : logique pure (états, éligibilité, élargissement seul, `KillAttributor`, calcul des soldes et bornes, sérialisation).
2. **GameTests NeoForge** (`./gradlew runGameTestServer`) : vrais dégâts, morts, inventaires, attributs, `SavedData`. Le serveur s'arrête avec un code non nul en cas d'échec.

### 8.2 Configuration
- Run `gameTestServer` avec `neoforge.enabledGameTestNamespaces=bloodcontracts`, **sans** Create ni CBC (ce run prouve qu'ils sont optionnels).
- Run `gameTestServerCompat` avec Create, CBC et Ritchie's Projectile Library.
- Tests enregistrés via `RegisterGameTestsEvent` / `@GameTestHolder`. Structure vide fournie.
- Joueurs simulés via `GameTestHelper#makeMockServerPlayerInLevel()` (nom à vérifier) ou `FakePlayerFactory`.
- `BountyManager.resetForTests()` et `TimeSource` contrôlable, réservés aux tests.

### 8.3 Simulations
- Coup direct : `hurt(damageSources().playerAttack(hunter))`.
- Flèche avec `setOwner(hunter)` ; `PrimedTnt` avec owner.
- Vide : coup du chasseur puis `fellOutOfWorld()`, dans la fenêtre et hors fenêtre.
- Redémarrage : aller-retour `CompoundTag` sur une instance neuve de `BountySavedData`.
- Connexion et déconnexion : appel direct des handlers.
- Réseau : appel des handlers serveur avec des payloads valides et invalides.

### 8.4 Matrice
**Migration**
- [ ] Une sauvegarde v1 se charge : primes actives conservées (type `PUBLIC`), objets intacts, aucune duplication.
- [ ] Une prime v1 qui viole les nouvelles limites reste active.

**Séquestre et cycle de vie**
- [ ] Création : l'objet exact (item, composants, quantité) quitte l'inventaire et entre au séquestre.
- [ ] Création refusée (soi-même, emplacement vide, blacklist, liste privée vide ou trop longue) : inventaire inchangé.
- [ ] Seconde prime sur la même cible par le même commanditaire : refusée.
- [ ] Au-delà de `maxActiveBountiesPerSponsor` : refusée.
- [ ] Deux commanditaires différents sur la même cible : acceptés.
- [ ] Demande d'annulation : la prime reste `ACTIVE` et encaissable pendant 30 min (horloge avancée de 29 min : toujours active).
- [ ] Kill éligible pendant le préavis : remise normale (objet, cœur, immunité), pas de retour au commanditaire.
- [ ] Échéance du préavis sans kill : `CANCELLED`, l'objet revient une seule fois.
- [ ] Préavis échu pendant un arrêt du serveur : finalisé au redémarrage.
- [ ] `resume` pendant le préavis : la prime redevient normale ; après 30 min, elle est toujours active.
- [ ] Seconde demande d'annulation : le délai n'est pas raccourci.
- [ ] Une prime en préavis bloque toujours une nouvelle prime sur la même cible par le même commanditaire.
- [ ] `admin cancel` : immédiat.
- [ ] **Conservation des objets** : la somme inventaires + séquestre + livraisons + sol est identique avant et après chaque scénario.

**Types et élargissement**
- [ ] `PUBLIC` : n'importe quel joueur éligible encaisse.
- [ ] `PRIVATE` : un non-listé tue la cible, la prime reste `ACTIVE` ; un listé encaisse.
- [ ] Ajout d'un chasseur en cours de prime : il devient éligible.
- [ ] Passage `PRIVATE → PUBLIC` : accepté.
- [ ] Retrait d'un chasseur ou passage `PUBLIC → PRIVATE` : impossible par commande comme par payload.
- [ ] Modification par un autre joueur que le commanditaire, ou sur une prime non `ACTIVE` : refusée.
- [ ] Confidentialité : la vue d'un non-commanditaire ne contient pas la liste, mais contient le type et son éligibilité personnelle.

**Attribution**
- [ ] Épée, flèche, TNT : le joueur responsable est crédité.
- [ ] Vide après un coup dans la fenêtre : le chasseur est crédité. Hors fenêtre : personne.
- [ ] Deux attaquants : le plus récent dans la fenêtre est crédité.
- [ ] Lave, chute, noyade sans joueur ; `/kill` : personne.
- [ ] Loup apprivoisé : le propriétaire est crédité selon la config.
- [ ] Le commanditaire tue sa cible : sa prime reste active ; les primes des autres sont remises s'il y est éligible.
- [ ] Plusieurs primes sur la cible : toutes les primes éligibles sont remises dans l'ordre de création.
- [ ] Double `LivingDeathEvent` : une seule remise.

**Cœurs et immunité**
- [ ] Prime remportée : la cible perd 1 cœur et le commanditaire en gagne 1 (attribut `MAX_HEALTH` vérifié).
- [ ] La somme de tous les soldes vaut toujours 0.
- [ ] Plancher et plafond : pas de transfert au-delà, l'objet est quand même remis.
- [ ] Mort avec 3 primes remportées : la cible perd 1 seul cœur, attribué au commanditaire de la plus ancienne ; si celui-ci est au plafond, au suivant.
- [ ] `PROGRESSIVE` en ligne : après 90 min connectée, la cible récupère exactement 1 PV et le commanditaire du transfert le plus ancien perd 1 PV ; après 3 h, 1 cœur entier.
- [ ] `PROGRESSIVE` hors ligne : après 11 h 59 hors ligne, rien ; après 12 h, 1 PV rendu à la connexion. Horloge avancée de 30 h hors ligne : 2 PV (et la jauge garde le reste, 0,5).
- [ ] Mélange : 45 min connectée (jauge 0,5) puis 6 h hors ligne (jauge 1,0) : 1 PV rendu, jauge à 0.
- [ ] Un arrêt du serveur compte comme du temps hors ligne pour tout le monde.
- [ ] Immunité en temps réel : elle expire pendant que la cible est hors ligne.
- [ ] Avertissements de config au démarrage dans les deux cas d'incohérence.
- [ ] Une nouvelle perte ne remet pas l'horloge de retour à zéro.
- [ ] Plusieurs transferts de commanditaires différents : remboursés du plus ancien au plus récent.
- [ ] `PERMANENT` : aucun retour.
- [ ] Scénario de cohérence : la cible reste connectée et meurt sous contrat à chaque fin d'immunité ; son solde baisse jusqu'au plancher (la perte dépasse le retour en ligne), puis remonte entièrement en 15 h de jeu sans mort, avec un solde global toujours nul.
- [ ] Modificateur réappliqué après réapparition, reconnexion et changement de dimension, sans doublon.
- [ ] La baisse du maximum ne tue pas le joueur.
- [ ] Immunité : un kill éligible pendant l'immunité ne donne rien ; après expiration, il fonctionne.
- [ ] Une prime peut être créée sur une cible immunisée.
- [ ] Transferts et immunité survivent au rechargement.

**Livraison et robustesse**
- [ ] Chasseur hors ligne : livraison remise à la reconnexion, une seule fois.
- [ ] Inventaire plein : objet au sol.
- [ ] Persistance complète (primes, livraisons, transferts, immunités, historique, paires) après rechargement.
- [ ] Item inconnu au chargement : pas de crash.

**Historique**
- [ ] Les primes remportées et annulées apparaissent dans l'historique.
- [ ] Paires récurrentes : affichées à partir du seuil ; le compteur survit à la purge et au rechargement.

**Compat (`gameTestServerCompat`)**
- [ ] Obus CBC tiré après une interaction récente d'un joueur avec le canon : ce joueur est crédité.
- [ ] Tir par redstone sans interaction récente : personne.
- [ ] Explosion d'obus : même règle que l'impact.
- [ ] Les cas à ajuster selon `docs/cbc-attribution.md`. Si une situation est impossible à simuler, la documenter et tester au plus proche (appel direct du résolveur).

**Réseau**
- [ ] Payloads invalides : refusés, état inchangé, aucun objet créé.

**Interface (testable sans humain)**
- [ ] La vue envoyée au client contient le `GameProfile` de la cible avec ses propriétés de textures, y compris pour une cible hors ligne.
- [ ] Plusieurs primes sur une cible : regroupées en une seule entrée de vue.
- [ ] Les valeurs de config envoyées pour l'onglet d'aide correspondent à la config serveur.
- [ ] Toutes les clés de traduction utilisées existent en `fr_fr` et `en_us` (test qui parcourt les clés).

Seul le rendu visuel des écrans (affiches, skins, animations) demande un humain. Le signaler dans `TEST_STATUS.md` avec une liste de vérifications à faire en jeu.

---

## 9. Configuration serveur
| Clé | Défaut |
|---|---|
| `maxActiveBountiesPerSponsor` | 3 |
| `maxHunterListSize` | 20 |
| `cancelNoticeMinutes` | 30 |
| `attributionWindowTicks` | 200 |
| `cannonOperatorWindowTicks` | à fixer après l'étude CBC (~200) |
| `creditTamedPets` | true |
| `maxHeartsLostPerDeath` | 1 |
| `maxHeartsLost` | 5 |
| `maxHeartsGained` | 5 |
| `heartRegenMode` | PROGRESSIVE |
| `heartRegenOnlineStepMinutes` | 90 (1 PV par 1 h 30 de jeu) |
| `heartRegenOfflineStepMinutes` | 720 (1 PV par 12 h hors ligne) |
| `targetImmunityMinutes` | 120 |
| `rewardBlacklist` | [] |
| `showSponsor` | false |
| `historySize` | 200 |
| `repeatPairThreshold` | 2 |

Si la v1 a déjà une config, conserve les clés existantes compatibles. Un renommage, une suppression ou un changement de valeur par défaut d'une clé existante est un conflit : demande avant (§0), puis documente-le dans le changelog.

## 10. Conventions
- Pas de mixin si un événement NeoForge suffit ; s'il en faut un (compat CBC), le garder minimal et le documenter.
- Logs de debug derrière un flag de config, avec l'identifiant de prime et les UUID.
- Commits petits, chacun avec ses tests au vert.

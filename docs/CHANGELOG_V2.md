# Blood Contracts 2.0.0 — changements depuis la 1.0.2

## Pour les joueurs

**Primes publiques et privées**
- Une prime est **publique** (tout le monde peut l'encaisser, sauf le commanditaire et la cible) ou **privée** (seuls les
  chasseurs choisis, 20 au plus). La liste des chasseurs reste secrète : seul le commanditaire la voit (et les opérateurs).
- Une prime ne peut qu'être **élargie** : ajouter des chasseurs, ou la rendre publique. Pour la restreindre, il faut
  l'annuler puis la recréer.
- Toutes les primes existantes deviennent publiques.

**Limites**
- **Une seule prime active par commanditaire et par cible.** On ne peut plus empiler plusieurs primes sur la même personne.
- Au plus **3 primes actives** par commanditaire sur une nouvelle installation (voir « Configuration » pour un serveur existant).
- Les primes déjà en place qui dépassent ces limites restent actives jusqu'à leur fin.

**Annulation avec préavis**
- Annuler ne retire plus la prime d'un coup : elle reste affichée et encaissable pendant **30 min** (temps réel), avec un
  compte à rebours. Ensuite l'objet revient au commanditaire, dans l'onglet « À réclamer ».
- Pendant le préavis, le commanditaire peut **reprendre** sa prime. Redemander l'annulation ne raccourcit pas le délai.
- Le verrou de combat de la 1.0 disparaît : le préavis le remplace.

**Cœurs**
- Une mort qui fait encaisser au moins une prime coûte **1 cœur** à la cible, gagné par le commanditaire de la prime la
  plus ancienne (le suivant si celui-ci est au plafond). Jamais plus d'un cœur par mort.
- Bornes : **−5 cœurs** au plus pour une cible, **+5 cœurs** au plus pour un commanditaire. Les objets sont remis même
  quand aucun cœur ne peut bouger.
- Les cœurs **reviennent petit à petit** : un demi-cœur toutes les **1 h 30 de jeu**, ou toutes les **12 h hors ligne**.
  Jouer les fait revenir plus vite. Le commanditaire rend ce qu'il avait gagné au même rythme.

**Immunité**
- Après une mort qui a payé une prime, la cible est **immunisée 2 h en temps réel** : aucune prime ne peut être encaissée
  sur elle. Se déconnecter la gaspille. On peut quand même placer une prime sur une cible immunisée.

**Tableau des primes** (touche `B` ou `/bounty gui`)
- **Recherchés** : une affiche « avis de recherche » par cible, avec son visage, toutes les récompenses, et des tampons :
  « PRIVÉE », « IMMUNISÉ — 1 h 12 », « EXPIRE DANS 23 MIN », « VOUS POUVEZ ENCAISSER » (affiche grisée sinon). Les affiches
  qu'on peut encaisser viennent en premier. Un clic ouvre le détail : toutes les primes sur la cible, et pour le
  commanditaire les boutons « + Chasseurs », « Rendre publique », « Annuler » / « Reprendre ».
- **+ Créer** : choix de la cible, du type (publique ou privée) et des chasseurs. Avertissement sur une prime publique :
  « Un allié de la cible pourrait l'encaisser. »
- **Mes primes**, **À réclamer** (inchangé), **Historique** (affiches tamponnées « ENCAISSÉE PAR … » ou « RETIRÉE »).
- **Comment ça marche ?** : les règles, avec les valeurs du serveur.
- Un chasseur qui a encaissé plus de 2 primes sur la même cible est signalé sur l'affiche (« Y a encaissé 3 primes sur X »),
  à titre d'information seulement.

**Alertes et notifications**
- L'alerte « ta tête est mise à prix » est un bandeau au centre de l'écran. Reçue pendant qu'un menu est ouvert
  (réglages, chat, inventaire…), elle attend le retour au jeu : l'image et le son arrivent ensemble. Si un menu s'ouvre
  pendant l'alerte, elle se met en pause (seul un liseré rouge pulse sur les bords) et reprend à sa fermeture.
- Notifications aux couleurs du mod : « objets à réclamer », et « contrat rempli » pour le commanditaire quand sa cible
  tombe (avec le chasseur et le cœur gagné).
- Un cœur gagné jaillit au-dessus de la barre d'action et file vers sa place ; un cœur perdu se brise en deux.

**Commandes**
- `/bounty place <joueur> [public | private <joueurs…>]` (objet en main).
- `/bounty addhunter <id> <joueur>`, `/bounty makepublic <id>`.
- `/bounty cancel <id>` lance le préavis ; `/bounty resume <id>` le retire.
- Inchangées : `list`, `info`, `history`, `claim`, `gui`.
- Opérateurs : `/bounty admin cancel <id>` (immédiat), `/bounty admin list` (avec les chasseurs), `/bounty admin hearts <joueur>`
  (consultation des cœurs, transferts, jauge et immunité), `/bounty admin clearimmunity <joueur>` (lève l'immunité).

## Configuration (`serverconfig/bloodcontracts-server.toml`)

**Nouvelles clés** (ajoutées automatiquement avec leur valeur par défaut au premier démarrage) :

| Clé | Défaut |
|---|---|
| `bounties.cancelNoticeMinutes` | 30 |
| `bounties.maxHunterListSize` | 20 |
| `bounties.repeatPairThreshold` | 2 |
| `hearts.maxHeartsLostPerDeath` | 1 |
| `hearts.maxHeartsLost` | 5 |
| `hearts.maxHeartsGained` | 5 |
| `hearts.heartRegenMode` | `PROGRESSIVE` (ou `PERMANENT`) |
| `hearts.heartRegenOnlineStepMinutes` | 90 |
| `hearts.heartRegenOfflineStepMinutes` | 720 |
| `hearts.targetImmunityMinutes` | 120 |

**Clés modifiées**
- `bounties.maxActiveBountiesPerSponsor` : défaut **5 → 3**. Un fichier de config existant **garde sa valeur** (NeoForge ne
  réécrit pas une valeur déjà présente) : sur le serveur de production, passer la valeur à `3` à la main.
- `bounties.cancelLockSeconds` : **obsolète**, sans effet (remplacée par `cancelNoticeMinutes`). Conservée pour ne pas
  casser les fichiers existants.

Inchangées : `attributionWindowTicks`, `cannonOperatorWindowTicks`, `creditTamedPets`, `rewardBlacklist`, `showSponsor`,
`alertRevealsSponsor`, `historySize`, `debugLogging`.

Au démarrage, le serveur logge un avertissement si les cœurs reviennent en jouant au moins aussi vite qu'on peut les
perdre (`heartRegenOnlineStepMinutes × 2 ≤ targetImmunityMinutes`), ou si être hors ligne est avantageux
(`heartRegenOfflineStepMinutes < heartRegenOnlineStepMinutes`).

## Données

- Les données passent au format **version 2** (`DataVersion` dans `data/bloodcontracts_bounties.dat`). La migration se fait
  seule au premier démarrage : primes actives, objets en séquestre, livraisons, historique et joueurs connus sont
  conservés ; les anciennes primes deviennent publiques. Elle est testée sur une vraie sauvegarde 1.0.x.
- **Pas de retour arrière** : une 1.0.x relisant des données v2 perdrait les types, les chasseurs, les préavis et les
  cœurs. Pour revenir en 1.0.x, restaurer la sauvegarde faite avant la mise à jour.

## Déploiement

La 2.0.0 doit être installée **sur le serveur et sur tous les clients** : le protocole réseau change (version 2), un
client 1.0.x ne peut plus rejoindre.

1. Prévenir les joueurs, puis arrêter le serveur.
2. **Sauvegarder le monde** entier (dossier du monde, dont `data/bloodcontracts_bounties.dat` et `serverconfig/`).
3. Remplacer `bloodcontracts-neoforge-1.21.1-1.0.2.jar` par `bloodcontracts-neoforge-1.21.1-2.0.0.jar` dans `mods/` (serveur et clients).
4. Démarrer le serveur. Le log doit contenir la ligne de migration des données v1 et aucune erreur Blood Contracts.
5. Ouvrir `serverconfig/bloodcontracts-server.toml` : mettre `maxActiveBountiesPerSponsor = 3` (voir plus haut), relire les
   nouvelles clés, puis redémarrer (ou laisser NeoForge recharger le fichier).
6. Vérifier en jeu : `/bounty admin list` (les primes d'avant sont là, publiques), le tableau (`B`), et `/bounty admin hearts <joueur>`.
7. En cas de problème : arrêter le serveur, restaurer la sauvegarde de l'étape 2 et remettre la 1.0.2.

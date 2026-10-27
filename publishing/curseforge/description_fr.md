![Blood Contracts](HEADER_IMAGE_URL)

# Mettez un prix sur la tête de vos amis.

**Blood Contracts** ajoute à votre serveur des primes financées par les joueurs. Choisissez une cible, engagez de
vrais objets sur sa tête, et laissez le serveur la traquer. Celui qui l'élimine empoche le butin, et la cible y laisse
un cœur.

Le serveur ne crée jamais le moindre objet : chaque récompense sort de l'inventaire d'un joueur. Pensé pour les petits
serveurs entre potes (bases Create, chacun dans son coin) qui veulent une raison de sortir, s'équiper et régler leurs
comptes, sans événement organisé ni zone de guerre permanente.

---

## Comment ça marche

1. **Signez le contrat.** Touche **B**, onglet **+ Créer**. Choisissez n'importe quel joueur connu du serveur, *en ligne
   ou non*, déposez la récompense (jusqu'à **9 piles différentes**) et choisissez :
   - **publique** : tout le monde peut l'encaisser ;
   - **privée** : seuls les chasseurs que vous désignez. Leur liste reste secrète.
2. **La cible est prévenue, et pas discrètement.** Un bandeau de sang au milieu de l'écran, un titre qui s'abat,
   une malédiction puis des battements de cœur. Si elle est dans un menu, l'alerte attend qu'elle revienne au jeu.
3. **Chassez.** Épée, arc, TNT, votre loup apprivoisé, une bonne poussette dans le vide ou la lave, ou un obus
   **Create Big Cannons** tiré à 300 blocs.
4. **Encaissez le prix du sang.** Le chasseur récupère les objets dans l'onglet **« À réclamer »**. La cible perd un
   cœur, qui va au commanditaire ; elle est ensuite **immunisée 2 h**.

![Les affiches](FEATURE_CONTRACTS_URL)

---

## Fonctionnalités

### 🩸 Des contrats financés par les joueurs, jamais par le serveur
- Les objets partent en séquestre dès la signature : une pile, ou jusqu'à 9 différentes.
- **Une prime par cible et par commanditaire**, 3 primes actives au plus par joueur. Plusieurs joueurs peuvent viser
  la même cible : le chasseur remporte **toutes** les primes auxquelles il a droit.
- **Une prime ne peut qu'être élargie** : ajouter des chasseurs, la rendre publique. Personne ne peut retirer la
  récompense à un chasseur au dernier moment.
- **Annulation avec préavis** : la prime reste affichée et encaissable **30 minutes**, avec un compte à rebours.
  Le commanditaire peut la reprendre pendant ce temps.
- Primes sur des **joueurs hors ligne** : quiconque s'est déjà connecté est une cible valable.

### ❤️ Des cœurs en jeu
- Une mort sous contrat coûte **1 cœur** à la cible, gagné par le commanditaire (jamais plus d'un par mort).
- Plancher et plafond : **−5 / +5 cœurs**. Les objets sont payés même quand plus aucun cœur ne peut bouger.
- Les cœurs **reviennent petit à petit** : un demi-cœur toutes les **1 h 30 de jeu**, ou toutes les **12 h hors
  ligne**. Jouer les fait revenir plus vite.
- **Immunité de 2 h en temps réel** après une prime encaissée : se déconnecter la gaspille.
- Un cœur gagné jaillit au-dessus de la barre d'action et file vers sa place ; un cœur perdu se brise sous vos yeux.

![Des cœurs en jeu](FEATURE_HUNT_URL)

### 🎯 Des éliminations équitables
L'attribution ne paie jamais la mauvaise personne. Dans le doute, personne n'est payé et la prime reste ouverte.
- Flèches, tridents, TNT, potions : c'est le **joueur derrière** qui est payé, jamais le projectile.
- Loups apprivoisés : leur maître est payé (configurable).
- **Poussé dans le vide, la lave, une chute ou le feu ?** Le dernier joueur à avoir frappé la cible dans les
  10 secondes encaisse.
- `/kill`, monstres, machines et déployeurs Create (faux joueurs), totem d'immortalité : **personne**.
- Le commanditaire qui tue sa propre cible ne récupère **pas** son objet.

### 💥 Compatible Create Big Cannons (optionnel)
Dans CBC, les obus n'ont pas de propriétaire, alors Blood Contracts détermine qui a tiré :
- le joueur qui a **actionné le levier, le bouton ou la manivelle à côté de la monture** dans les 10 dernières
  secondes, ou celui qui **chevauche l'affût** ;
- impacts, explosions HE, shrapnel, mitraille et flak sont tous crédités ;
- un canon déclenché par une horloge redstone sans personne aux commandes ne crédite **personne**.

Gère aussi **CBC Enchanced Shells** (« More Shells ») : obus Mk.I à V, sous-munitions, fusées, missile mounts.

### 📜 Un tableau des primes façon western
- **Recherchés** : une affiche « avis de recherche » par cible, avec son vrai visage (même hors ligne), toutes ses
  récompenses et des tampons : **PRIVÉE**, **IMMUNISÉ — 1 h 12**, **EXPIRE DANS 23 MIN**, **VOUS POUVEZ ENCAISSER**.
- Détail d'une cible, **Mes primes**, **À réclamer**, **Historique** tamponné « ENCAISSÉE PAR … » ou « RETIRÉE ».
- **Comment ça marche ?** : les règles, avec les valeurs réglées sur votre serveur.
- Notifications aux couleurs du mod : objets à réclamer, et « contrat rempli » quand votre cible tombe.

![Ta tête est mise à prix](FEATURE_ALERT_URL)
![Le prix du sang](FEATURE_CLAIM_URL)

### 🏆 Succès
| | Succès | Comment |
|---|---|---|
| 📜 | **Contrats de sang** | Signer son premier contrat sur la tête d'un « ami » |
| 💰 | **Le prix du sang** | Encaisser une prime |
| 💀 | **Ennemi public n°1** *(défi)* | Avoir 3 primes actives sur sa tête en même temps |
| 💣 | **Livraison express** *(défi, avec Create Big Cannons)* | Encaisser une prime d'un tir de canon |

### 🔒 Sûr par conception
- **Le serveur ne crée jamais d'objet.** À tout instant, un objet engagé se trouve à un seul endroit : le séquestre,
  une récompense en attente, ou un joueur. Les cœurs non plus : chaque cœur perdu est gagné par quelqu'un.
- Le serveur fait autorité : le client n'envoie que des intentions, jamais d'objet.
- Tout est sauvegardé ensemble : un crash ne peut rien perdre ni dupliquer. Les objets d'un mod retiré sont conservés
  intacts jusqu'à son retour.
- **Plus de 160 tests automatisés** (JUnit et GameTests en jeu, dont un vrai canon Create Big Cannons assemblé puis
  tiré), y compris des scénarios d'abus : double clic, déconnexion en pleine création, totem, horloge qui recule,
  récompense de plusieurs mégaoctets…

---

## Commandes
| Commande | |
|---|---|
| `/bounty gui` (ou touche **B**) | Ouvrir le tableau des primes |
| `/bounty place <joueur> [public \| private <joueurs…>]` | Prime avec l'objet en main |
| `/bounty addhunter <id> <joueur>`, `/bounty makepublic <id>` | Élargir sa prime |
| `/bounty cancel <id>`, `/bounty resume <id>` | Lancer ou retirer le préavis d'annulation |
| `/bounty list`, `/bounty info <id>`, `/bounty history` | Consulter les primes |
| `/bounty claim` | Récupérer tout ce qui vous attend |
| `/bounty admin list`, `admin cancel <id>`, `admin hearts <joueur>`, `admin clearimmunity <joueur>` | Opérateurs (maintenance) |

## Configuration serveur (`serverconfig/bloodcontracts-server.toml`)
| Option | Défaut | |
|---|---|---|
| `maxActiveBountiesPerSponsor` | 3 | Primes actives par joueur |
| `maxHunterListSize` | 20 | Chasseurs au plus sur une prime privée |
| `cancelNoticeMinutes` | 30 | Préavis d'annulation (temps réel) |
| `maxHeartsLostPerDeath` | 1 | Cœurs perdus par mort sous contrat |
| `maxHeartsLost` / `maxHeartsGained` | 5 / 5 | Plancher et plafond de cœurs |
| `heartRegenMode` | PROGRESSIVE | `PROGRESSIVE` ou `PERMANENT` |
| `heartRegenOnlineStepMinutes` | 90 | Un demi-cœur rendu toutes les… minutes de jeu |
| `heartRegenOfflineStepMinutes` | 720 | … ou minutes hors ligne |
| `targetImmunityMinutes` | 120 | Immunité après une prime encaissée (temps réel) |
| `attributionWindowTicks` | 200 | Un coup vous rend responsable d'une mort environnementale pendant 10 s |
| `cannonOperatorWindowTicks` | 200 | Un tir de canon est le vôtre 10 s après avoir touché ses commandes |
| `creditTamedPets` | true | Les kills des familiers paient leur maître |
| `rewardBlacklist` | [] | Objets interdits comme récompense |
| `showSponsor` / `alertRevealsSponsor` | false / true | Afficher le commanditaire sur le tableau / dans l'alerte |
| `historySize` / `repeatPairThreshold` | 200 / 2 | Taille de l'historique / signaler un chasseur qui encaisse souvent sur la même cible |

---

## Compatibilité
- **Minecraft 1.21.1, NeoForge 21.1.248+.** Requis **côté serveur et côté client**, dans la même version.
- Optionnels : **Create Big Cannons 5.11.4+** (avec Create 6.0.x et Ritchie's Projectile Library),
  **CBC Enchanced Shells 1.4.0+**. Sans eux, tout fonctionne pareil, sauf l'attribution des tirs de canon.
- **Mise à jour depuis la 1.0.x** : les primes, objets en séquestre et récompenses en attente sont migrés
  automatiquement. Sauvegardez le monde avant, et mettez à jour tous les clients en même temps.

## FAQ
**Deux potes peuvent-ils se farmer ?** Les objets, oui, mais ça ne rapporte rien de plus que de se les donner. Les
cœurs sont bornés par le plancher, le plafond et l'immunité de 2 h.

**Un allié de la cible peut-il encaisser ma prime ?** Sur une prime publique, oui. Pour l'éviter, faites-la privée.

**Et si je suis hors ligne quand je gagne ?** La récompense attend dans « À réclamer », même après un redémarrage.

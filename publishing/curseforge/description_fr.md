![Blood Contracts](HEADER_IMAGE_URL)

# Mettez un prix sur la tête de vos amis.

**Blood Contracts** ajoute à votre serveur des primes financées par les joueurs. Choisissez une cible, engagez de
vrais objets sur sa tête, et laissez tout le serveur la traquer. Celui qui l'élimine empoche le butin.

Le serveur ne crée jamais le moindre objet : chaque récompense sort de l'inventaire d'un joueur. Pensé pour les petits
serveurs entre potes (bases Create, chacun dans son coin) qui veulent une raison de sortir, s'équiper et régler leurs
comptes, sans événement organisé ni zone de guerre permanente.

---

## Comment ça marche

1. **Signez le contrat.** Touche **B** (ou `/bounty gui`). Choisissez n'importe quel joueur connu du serveur, *en ligne
   ou non*, déposez la récompense et signez. Besoin de plus ? **« + objets »** ouvre jusqu'à **9 piles différentes**.
2. **La cible est prévenue, et pas discrètement.** Une alerte plein écran : vignette rouge sang qui pulse, titre qui
   tremble, malédiction puis battements de cœur. Une cible hors ligne la reçoit dès sa connexion.
3. **Chassez.** Épée, arc, TNT, votre loup apprivoisé, une bonne poussette dans le vide ou la lave, ou un obus
   **Create Big Cannons** tiré à 300 blocs.
4. **Encaissez le prix du sang.** Les récompenses attendent dans l'onglet **« À réclamer »** du tableau. Un toast vous
   prévient, et rien n'arrive dans votre inventaire sans votre accord.

![Signez le contrat](FEATURE_CONTRACTS_URL)

---

## Fonctionnalités

### 🩸 Des contrats financés par les joueurs, jamais par le serveur
- Les objets partent en séquestre dès la signature : une pile, ou jusqu'à 9 différentes.
- Plusieurs joueurs peuvent cumuler des primes sur la même cible. Le chasseur les remporte **toutes**.
- Vous pouvez annuler votre contrat quand vous voulez, sauf si la cible a été touchée par un chasseur dans les
  30 dernières secondes : pas d'annulation en plein combat.
- Primes sur des **joueurs hors ligne** : quiconque s'est déjà connecté est une cible valable.

### 🎯 Des éliminations équitables
L'attribution ne paie jamais la mauvaise personne. Dans le doute, personne n'est payé et la prime reste ouverte.
- Flèches, tridents, TNT, potions : c'est le **joueur derrière** qui est payé, jamais le projectile.
- Loups apprivoisés : leur maître est payé (configurable).
- **Poussé dans le vide, la lave, une chute ou le feu ?** Le dernier joueur à avoir frappé la cible dans les
  10 secondes encaisse.
- `/kill`, monstres, machines et déployeurs Create (faux joueurs) : **personne**.
- Le commanditaire qui tue sa propre cible ne récupère **pas** son objet. Les primes des autres commanditaires, elles,
  le paient.

![Des éliminations équitables](FEATURE_HUNT_URL)

### 💥 Compatible Create Big Cannons (optionnel)
Dans CBC, les obus n'ont pas de propriétaire, alors Blood Contracts détermine qui a tiré :
- le joueur qui a **actionné le levier, le bouton ou la manivelle à côté de la monture** dans les 10 dernières
  secondes, ou celui qui **chevauche l'affût** ;
- impacts, explosions HE, shrapnel, mitraille et flak sont tous crédités ;
- un canon déclenché par une horloge redstone sans personne aux commandes ne crédite **personne**.

Gère aussi **CBC Enchanced Shells** (« More Shells ») : obus Mk.I à V, sous-munitions, fusées tirées depuis une base
de fusée, missile mounts.

### 🔔 Des alertes qui font peur, des récompenses discrètes
- La cible reçoit une alerte plein écran, pas une ligne de chat. Elle peut nommer le commanditaire ou non (config).
- Gains et remboursements attendent dans **« À réclamer »**, avec un toast. On récupère une pile ou tout d'un coup.
  Inventaire plein : les objets tombent à vos pieds, vous sont réservés et ne disparaissent jamais.

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
  une récompense en attente, ou un joueur.
- Le serveur fait autorité : le client n'envoie que des intentions (cible, identifiant de prime), jamais d'objet.
- Les primes et les récompenses en attente sont sauvegardées ensemble : un crash ne peut rien perdre ni dupliquer.
  Les objets d'un mod retiré sont conservés intacts jusqu'à son retour.
- **Plus de 100 tests automatisés** (JUnit et GameTests en jeu, dont un vrai canon Create Big Cannons assemblé puis
  tiré).

---

## Commandes
| Commande | |
|---|---|
| `/bounty gui` (ou touche **B**) | Ouvrir le tableau des primes |
| `/bounty place <joueur>` | Prime avec l'objet en main (cible en ligne ou non) |
| `/bounty list`, `/bounty info <id>`, `/bounty history` | Consulter les primes |
| `/bounty cancel <id>` | Annuler sa prime (le remboursement arrive dans « À réclamer ») |
| `/bounty claim` | Récupérer tout ce qui vous attend |
| `/bounty admin list`, `/bounty admin cancel <id>` | Opérateurs |

## Configuration serveur (`serverconfig/bloodcontracts-server.toml`)
| Option | Défaut | |
|---|---|---|
| `attributionWindowTicks` | 200 | Combien de temps un coup vous rend responsable d'une mort environnementale (10 s) |
| `cannonOperatorWindowTicks` | 200 | Délai après avoir touché les commandes pendant lequel un tir de canon est le vôtre (10 s) |
| `creditTamedPets` | true | Les kills des familiers paient leur maître |
| `cancelLockSeconds` | 30 | Pas d'annulation pendant que la cible est en combat |
| `maxActiveBountiesPerSponsor` | 5 | Primes ouvertes par joueur |
| `rewardBlacklist` | [] | Objets interdits comme récompense |
| `showSponsor` | false | Afficher le commanditaire sur le tableau |
| `alertRevealsSponsor` | true | L'alerte de la cible nomme le commanditaire |
| `historySize` | 200 | Primes closes gardées dans l'historique |

Les types de dégâts « environnementaux » sont un tag de datapack (`bloodcontracts:environmental`), extensible.

---

## Compatibilité
- **Minecraft 1.21.1, NeoForge 21.1.248+.** Requis **côté serveur et côté client**.
- Optionnels : **Create Big Cannons 5.11.4+** (avec Create 6.0.x et Ritchie's Projectile Library),
  **CBC Enchanced Shells 1.4.0+**. Sans eux, tout fonctionne pareil, sauf l'attribution des tirs de canon.

## FAQ
**Deux potes peuvent-ils se farmer ?** Oui, et ça ne leur rapporte rien de plus que de se donner l'objet : aucun objet
n'est jamais créé.

**Et si je suis hors ligne quand je gagne ?** La récompense attend dans « À réclamer », même après un redémarrage.

**Peut-on mettre une prime sur quelqu'un qui n'est pas connecté ?** Oui, sur tout joueur venu au moins une fois.

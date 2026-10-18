# Blood Contracts

Mod NeoForge 1.21.1 : des primes sur les joueurs, financées exclusivement par les joueurs. Le commanditaire engage un
objet de son inventaire ; celui qui tue la cible le reçoit. Le serveur ne crée jamais d'objet.

## Jouer
- `/bounty place <joueur>` : met une prime avec l'objet tenu en main principale.
- `/bounty list`, `/bounty info <id>`, `/bounty cancel <id>`, `/bounty history`, `/bounty gui`.
- Opérateurs : `/bounty admin list`, `/bounty admin cancel <id>`.
- Touche `B` : tableau des primes (création, annulation, historique).

Configuration serveur : `config/bloodcontracts-server.toml` dans le monde (fenêtre d'attribution, verrou d'annulation,
limite par commanditaire, blacklist, familiers, visibilité du commanditaire, taille de l'historique).
Les dégâts « environnementaux » (vide, chute, lave…) qui peuvent être attribués au dernier attaquant sont définis par le tag
de types de dégâts `bloodcontracts:environmental`, extensible par datapack.

Create et Create Big Cannons sont optionnels. Avec CBC, un tir est attribué au joueur qui a opéré le canon juste avant
(voir [docs/cbc-attribution.md](docs/cbc-attribution.md)).

## Développer
```bash
./gradlew test                      # JUnit (logique pure)
./gradlew runGameTestServer         # GameTests sans Create ni CBC
./gradlew runGameTestServerCompat   # GameTests avec Create, CBC et RPL
```
### Trois clients en LAN (commanditaire, chasseur, cible)
Trois configurations de lancement existent : `client` (`Joueur1`, dossier `run/`), `client2` (`Joueur2`, `run/client2`)
et `client3` (`Joueur3`, `run/client3`). Les noms différents donnent des UUID différents ; sinon les clients suivants seraient refusés.
1. Lancer le premier client : `./gradlew runClient`.
2. Dans un **deuxième** puis un **troisième terminal** : `./gradlew runClient2` et `./gradlew runClient3`. Depuis IntelliJ,
   on peut aussi utiliser les configurations « Client », « Client2 » et « Client3 » créées à la synchronisation Gradle.
3. Sur `Joueur1` : créer un monde (commandes autorisées), puis Échap → *Ouvrir au réseau local* → *Démarrer le monde en réseau local*.
   Le port s'affiche dans le chat.
4. Sur `Joueur2` et `Joueur3` : *Multijoueur*. Le monde apparaît dans la liste « réseau local », sinon *Connexion directe* → `localhost:<port>`.
5. Par exemple : `Joueur1` (commanditaire) appuie sur `B` → « Nouvelle prime », choisit `Joueur3`, dépose un objet,
   « Signer ». `Joueur2` (chasseur) tue `Joueur3` : il reçoit l'objet. Si `Joueur1` tue lui-même la cible, sa prime reste active.

État de la matrice de tests : [docs/TEST_STATUS.md](docs/TEST_STATUS.md). Spécification : [CLAUDE.md](CLAUDE.md).

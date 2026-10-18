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
État de la matrice de tests : [docs/TEST_STATUS.md](docs/TEST_STATUS.md). Spécification : [CLAUDE.md](CLAUDE.md).

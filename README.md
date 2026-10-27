# Blood Contracts

Mod NeoForge 1.21.1 : des primes sur les joueurs, financées exclusivement par les joueurs. Le commanditaire engage un
objet de son inventaire ; celui qui tue la cible le reçoit. Le serveur ne crée jamais d'objet.

## Jouer
- Touche `B` : tableau des primes. Onglets « Recherchés » (une affiche « avis de recherche » par cible, avec son visage,
  ses récompenses et ses tampons), « + Créer », « Mes primes », « À réclamer », « Historique » et « Comment ça marche ? ».
  - **Nouvelle prime** : le joueur choisit la cible parmi tous les joueurs déjà venus sur le serveur, **même hors ligne**,
    le type (**publique**, ou **privée** avec une liste secrète de chasseurs), puis dépose la récompense (un emplacement,
    ou 9 avec « + objets ») et signe. Une prime ne peut qu'être élargie (ajouter des chasseurs, la rendre publique).
  - **Annulation** : la prime reste encaissable pendant un préavis de 30 min, avec un compte à rebours, et peut être reprise.
  - **Cœurs** : une mort sous contrat coûte un cœur à la cible, gagné par le commanditaire ; ils reviennent petit à petit
    (plus vite en jouant). La cible est ensuite immunisée 2 h en temps réel.
  - **À réclamer** : les gains et les remboursements ne tombent jamais tout seuls dans l'inventaire. Ils attendent ici
    (une notification l'annonce) et se récupèrent avec « Réclamer » ou « Tout réclamer ».
- Une cible en ligne reçoit une **alerte plein écran** : vignette rouge pulsante, titre qui tremble, malédiction puis
  battements de cœur. Une cible hors ligne la reçoit à sa connexion.
- Commandes : `/bounty place <joueur> [public | private <joueurs…>]` (objet en main), `/bounty addhunter <id> <joueur>`,
  `/bounty makepublic <id>`, `/bounty cancel <id>` (préavis), `/bounty resume <id>`, `/bounty list`, `/bounty info <id>`,
  `/bounty claim`, `/bounty history`, `/bounty gui`. Opérateurs : `/bounty admin list`, `/bounty admin cancel <id>` (immédiat),
  `/bounty admin hearts <joueur>`.

Configuration serveur : `serverconfig/bloodcontracts-server.toml` dans le dossier du monde (attribution, limites, préavis,
cœurs et retour progressif, immunité, blacklist, familiers, visibilité du commanditaire, historique). Le détail des clés
et la mise à jour depuis la 1.0.x sont dans [docs/CHANGELOG_V2.md](docs/CHANGELOG_V2.md).
Les dégâts « environnementaux » (vide, chute, lave…) qui peuvent être attribués au dernier attaquant sont définis par le tag
de types de dégâts `bloodcontracts:environmental`, extensible par datapack.

Create et Create Big Cannons sont optionnels. Avec CBC, un tir est attribué au joueur qui a opéré le canon juste avant
(voir [docs/cbc-attribution.md](docs/cbc-attribution.md)).

## Développer
```bash
./gradlew test                      # JUnit (logique pure)
./gradlew runGameTestServer         # GameTests sans Create ni CBC
./gradlew runGameTestServerCompat   # GameTests avec Create, CBC, RPL et CBC Enchanced Shells
```
Les clients et le serveur de développement (`runClient`, `runClient2`, `runClient3`, `runServer`) chargent Create 6.0.10,
CBC 5.11.7, RPL 2.1.2 et CBC Enchanced Shells (« More Shells ») 1.4.0, pour tester les primes avec leurs objets.
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

## Licence
MIT, voir [LICENSE](LICENSE).

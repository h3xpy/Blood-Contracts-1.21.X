# Publier Blood Contracts 2.0.0 sur CurseForge

Tout ce qu'il faut est dans ce dossier. Les illustrations se régénèrent avec
`python tools/make_promo.py <textures vanilla> publishing/curseforge/images src/main/resources/bloodcontracts_logo.png`,
et les captures d'écran avec `./gradlew runBoardDebug` et `./gradlew runFxDebug` (dans `run/screenshots/`).

## 1. Le projet

**Si le projet n'existe pas encore** (authors.curseforge.com → Create Project → Minecraft → Mods) :

| Champ | Valeur |
|---|---|
| **Nom** | `Blood Contracts` |
| **Résumé** (summary, ≤ 150 car.) | `Put a price on your friends' heads with real items. Public or private bounties, hearts at stake, fair kill credit, Create Big Cannons support.` |
| **Logo / avatar** | `images/logo_400.png` (400×400) |
| **Catégorie principale** | Adventure and RPG |
| **Catégories secondaires** | Server Utility, Miscellaneous |
| **Licence** | **MIT** (fichier `LICENSE`, `mod_license=MIT`) |
| **Liens** | Source et Issues : https://github.com/h3xpy/Blood-Contracts-1.21.X |

**S'il existe déjà en 1.0.x** : remplace seulement le résumé ci-dessus, la description (étape 2) et la galerie
(étape 3), puis envoie le nouveau fichier (étape 4).

## 2. Description
Dans l'éditeur de description, choisis le mode **Markdown**, puis colle `description_en.md` (anglais, recommandé :
c'est la langue de la plupart des visiteurs). `description_fr.md` est la version française, à coller à la suite sous
un titre « 🇫🇷 Français » ou sur un autre site.

Les images : dans l'onglet **Images** du projet, envoie `header.png` et les quatre `feature_*.png`. Copie l'URL de
chacune et remplace dans la description :

| Marqueur | Image |
|---|---|
| `HEADER_IMAGE_URL` | `header.png` (1600×520) |
| `FEATURE_CONTRACTS_URL` | `screenshots/01_wanted_posters.png` (les affiches, plus parlant que l'ancienne illustration) |
| `FEATURE_HUNT_URL` | `screenshots/06_heart_gained.png` (un cœur gagné et la notification « contrat rempli ») |
| `FEATURE_ALERT_URL` | `screenshots/04_alert.png` |
| `FEATURE_CLAIM_URL` | `feature_claim.png` |

## 3. Galerie (onglet Images) : titres et légendes
Vraies captures du mod, prises en jeu (`images/screenshots/`, 854×480) :

| Image | Titre | Légende |
|---|---|---|
| `01_wanted_posters.png` | Wanted posters | One poster per target, with their real face, rewards and stamps: private, immune, expiring, collectable. |
| `02_poster_detail.png` | Every bounty on a target | Sponsors widen, cancel or resume their own bounty; recurring hunters are flagged. |
| `03_create_private.png` | Public or private | Pick the target, the rewards, and who may collect. |
| `04_alert.png` | A price on your head | The target is warned the moment they are back in the game. |
| `05_notifications.png` | Contract fulfilled | Notifications when rewards wait for you or your target goes down. |
| `06_heart_gained.png` | Hearts at stake | The sponsor takes a heart from the target; it comes back over time. |
| `07_how_it_works.png` | How to | The rules, with your server's settings. |
| `08_history.png` | History | Collected and withdrawn bounties, stamped. |

Illustrations (toujours valables) : `feature_contracts.png`, `feature_hunt.png`, `feature_alert.png`,
`feature_claim.png`. Les captures sont en anglais (langue par défaut du client) ; pour des captures en français,
change la langue dans le client avant de lancer les scénarios.

## 4. Envoyer le fichier
1. Compile : `./gradlew build`. Le fichier est `build/libs/bloodcontracts-neoforge-1.21.1-2.0.0.jar`.
2. **Upload File** sur la page du projet :

| Champ | Valeur |
|---|---|
| Display name | `Blood Contracts 2.0.0 (NeoForge 1.21.1)` |
| Release type | Release (ou Beta si tu préfères un premier retour) |
| Game versions | `1.21.1` + `NeoForge` |
| Environment | Client **et** Server (requis des deux côtés, même version) |
| Java | 21 |
| Changelog | contenu de `changelog_2.0.0.md` |
| Related projects | **Create Big Cannons** : *Optional Dependency* ; **CBC Enchanced Shells** : *Optional Dependency* ; **Create** : *Optional Dependency* |

## 5. Avant de cliquer sur Publier
- [ ] Remplacer les 5 marqueurs d'URL d'image dans la description.
- [ ] Vérifier que le jar uploadé est bien `bloodcontracts-neoforge-1.21.1-2.0.0.jar` (et non un jar de test ou la 1.0.2).
- [ ] Sur ton propre serveur : sauvegarder le monde, mettre à jour serveur et clients ensemble, et passer
      `maxActiveBountiesPerSponsor` à 3 dans la config si tu veux la nouvelle limite (voir `docs/CHANGELOG_V2.md`).

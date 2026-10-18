# Publier Blood Contracts sur CurseForge

Tout ce qu'il faut est dans ce dossier. Les images se régénèrent avec
`python tools/make_promo.py <textures vanilla> publishing/curseforge/images src/main/resources/bloodcontracts_logo.png`.

## 1. Créer le projet (authors.curseforge.com → Create Project → Minecraft → Mods)

| Champ | Valeur |
|---|---|
| **Nom** | `Blood Contracts` |
| **Résumé** (summary, ≤ 150 car.) | `Put a price on your friends' heads with real items. Fair kill attribution, claimable rewards, scary alerts and Create Big Cannons support.` |
| **Logo / avatar** | `images/logo_400.png` (400×400) |
| **Catégorie principale** | Adventure and RPG |
| **Catégories secondaires** | Server Utility, Miscellaneous |
| **Licence** | À choisir. Le projet est actuellement en « All Rights Reserved » (`mod_license` dans `gradle.properties`). Pour un mod entre potes, **MIT** ou **LGPL-3.0** sont courantes. Change aussi `mod_license` si tu changes la licence. |
| **Liens** | Source / Issues : ton dépôt GitHub, si tu le publies |

## 2. Description
Dans l'éditeur de description, choisis le mode **Markdown**, puis colle `description_en.md` (anglais, recommandé :
c'est la langue de la plupart des visiteurs). `description_fr.md` est la version française, à coller à la suite sous
un titre « 🇫🇷 Français » ou sur un autre site.

Les images : dans l'onglet **Images** du projet, envoie `header.png` et les quatre `feature_*.png`. Copie l'URL de
chacune et remplace dans la description :

| Marqueur | Image |
|---|---|
| `HEADER_IMAGE_URL` | `header.png` (1600×520) |
| `FEATURE_CONTRACTS_URL` | `feature_contracts.png` |
| `FEATURE_HUNT_URL` | `feature_hunt.png` |
| `FEATURE_ALERT_URL` | `feature_alert.png` |
| `FEATURE_CLAIM_URL` | `feature_claim.png` |

## 3. Galerie (onglet Images) : titres et légendes
| Image | Titre | Légende |
|---|---|---|
| `feature_contracts.png` | Sign the contract | Put real items on any player's head, online or not. Up to 9 different stacks. |
| `feature_hunt.png` | Fair kills only | Projectiles, TNT, pets, void and lava knock-offs are credited to the right player. Mobs, /kill and machines pay nobody. |
| `feature_alert.png` | You've been marked | The target gets a full-screen, heart-pounding alert. |
| `feature_claim.png` | Blood money | Rewards wait on the board until you claim them. |

Ces quatre visuels sont des **illustrations** du mod, pas des captures d'écran. CurseForge et les joueurs apprécient
aussi de vraies captures : prends-en 3 ou 4 en jeu (touche **F2**, puis dossier `run/screenshots`). Bons sujets :
1. le tableau des primes (touche **B**) avec 3 ou 4 contrats ouverts, têtes et objets visibles ;
2. l'écran « Avis de recherche » en mode **+ objets**, avec plusieurs objets Create ;
3. l'alerte « TA TÊTE EST MISE À PRIX » au moment où elle apparaît (le joueur ciblé appuie sur F2) ;
4. l'onglet « À réclamer » après une élimination, ou un canon CBC qui tire sur une cible.

## 4. Envoyer le fichier
1. Compile : `./gradlew build`. Le fichier est `build/libs/bloodcontracts-neoforge-1.21.1-1.0.0.jar`.
2. **Upload File** sur la page du projet :

| Champ | Valeur |
|---|---|
| Display name | `Blood Contracts 1.0.0 (NeoForge 1.21.1)` |
| Release type | Release (ou Beta si tu préfères un premier retour) |
| Game versions | `1.21.1` + `NeoForge` |
| Environment | Client **et** Server (requis des deux côtés) |
| Java | 21 |
| Changelog | contenu de `changelog_1.0.0.md` |
| Related projects | **Create Big Cannons** : *Optional Dependency* ; **CBC Enchanced Shells** : *Optional Dependency* ; **Create** : *Optional Dependency* |

## 5. Avant de cliquer sur Publier
- [ ] Choisir la licence (et mettre à jour `mod_license`).
- [ ] Remplacer les 5 marqueurs d'URL d'image dans la description.
- [ ] Ajouter 2 ou 3 vraies captures d'écran.
- [ ] Vérifier que le jar uploadé est bien celui de `build/libs` (et non un jar de test).

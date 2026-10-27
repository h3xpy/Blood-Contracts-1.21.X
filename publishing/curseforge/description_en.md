![Blood Contracts](HEADER_IMAGE_URL)

# Put a price on your friends' heads.

**Blood Contracts** adds player-funded bounties to your server. Pick a target, put real items on their head, and let
the server hunt them down. Whoever takes them out claims the loot, and the target loses a heart.

The server never creates a single item: every reward comes out of a player's inventory. It's made for small servers of
friends (Create bases, everyone in their corner) that want a reason to go out, gear up and settle scores, without
organised events or a permanent war zone.

---

## How it works

1. **Sign the contract.** Press **B**, tab **+ Create**. Choose any player the server knows (online *or offline*), drop
   the reward in (up to **9 different stacks**) and pick:
   - **public**: anyone can collect it;
   - **private**: only the hunters you name. Their list stays secret.
2. **The target is warned. Loudly.** A blood ribbon across the middle of the screen, a title slamming down, a curse
   sting and a racing heartbeat. If they are in a menu, the alert waits until they are back in the game.
3. **Hunt.** Sword, bow, TNT, your tamed wolf, a well-timed shove into the void or lava, or a **Create Big Cannons**
   shell from 300 blocks away.
4. **Claim the blood money.** The hunter gets the items in the **"To claim"** tab. The target loses a heart, which goes
   to the sponsor, and is then **immune for 2 hours**.

![Wanted posters](FEATURE_CONTRACTS_URL)

---

## Features

### 🩸 Contracts funded by players, not by the server
- Items go into escrow the moment you sign: one stack, or up to 9 different ones.
- **One bounty per target and sponsor**, at most 3 active bounties per player. Several players can target the same
  person: the hunter collects **every** bounty they are entitled to.
- **A bounty can only be widened**: add hunters, make it public. Nobody can snatch the reward from a hunter at the last
  second.
- **Cancelling comes with notice**: the bounty stays posted and collectable for **30 minutes**, with a countdown. The
  sponsor can resume it meanwhile.
- Bounties on **offline players**: anyone who ever joined is a valid target.

### ❤️ Hearts at stake
- A death under contract costs the target **1 heart**, gained by the sponsor (never more than one per death).
- Floor and ceiling: **−5 / +5 hearts**. Items are paid even when no heart can move any more.
- Hearts **come back bit by bit**: half a heart every **1h30 of play**, or every **12h offline**. Playing brings them
  back faster.
- **2-hour real-time immunity** after a collected bounty: logging out wastes it.
- A gained heart bursts above the hotbar and flies into its slot; a lost heart shatters before your eyes.

![Hearts at stake](FEATURE_HUNT_URL)

### 🎯 Fair kills only
Attribution never pays the wrong player. When in doubt, nobody is paid and the bounty stays open.
- Arrows, tridents, TNT, potions: the **player behind them** is paid, never the projectile.
- Tamed wolves: their owner is paid (configurable).
- **Knocked into the void, lava, a fall or fire?** The last player who hit the target within 10 seconds collects.
- `/kill`, mobs, machines and Create deployers (fake players), totem of undying: **nobody**.
- A sponsor who kills their own target does **not** get their item back.

### 💥 Create Big Cannons support (optional)
CBC shells have no owner, so Blood Contracts works out who fired:
- the player who **pulled the lever, pressed the button or turned the crank next to the mount** in the last
  10 seconds, or the one **riding the carriage**;
- impacts, HE blasts, shrapnel, grapeshot and flak are all credited;
- a cannon fired by a redstone clock with nobody at the controls credits **nobody**.

Also handles **CBC Enchanced Shells** ("More Shells"): Mk.I to V shells, submunitions, rockets, missile mounts.

### 📜 A western bounty board
- **Wanted**: one "wanted" poster per target, with their real face (even offline), all their rewards and stamps:
  **PRIVATE**, **IMMUNE — 1h12**, **EXPIRES IN 23 MIN**, **YOU CAN COLLECT**.
- Target detail, **My bounties**, **To claim**, **History** stamped "COLLECTED BY …" or "WITHDRAWN".
- **How to**: the rules, with the values set on your server.
- Notifications in the mod's style: items to claim, and "contract fulfilled" when your target goes down.

![A price on your head](FEATURE_ALERT_URL)
![Blood money](FEATURE_CLAIM_URL)

### 🏆 Advancements
| | Advancement | How |
|---|---|---|
| 📜 | **Blood Contracts** | Sign your first contract on a "friend"'s head |
| 💰 | **Blood Money** | Collect a bounty |
| 💀 | **Public Enemy No. 1** *(challenge)* | Have 3 active bounties on your head at once |
| 💣 | **Special Delivery** *(challenge, with Create Big Cannons)* | Collect a bounty with a cannon shot |

### 🔒 Safe by design
- **The server never creates an item.** At any time a pledged item is in exactly one place: escrow, a pending reward,
  or a player. Hearts neither: every heart lost is gained by someone.
- The server is authoritative: the client only sends intentions, never items.
- Everything is saved together: a crash can neither lose nor duplicate anything. Items from a removed mod are kept
  intact until it comes back.
- **Over 160 automated tests** (JUnit and in-game GameTests, including a real Create Big Cannons cannon assembled and
  fired), with abuse scenarios: double clicks, disconnecting mid-creation, totems, a clock going backwards,
  multi-megabyte rewards…

---

## Commands
| Command | |
|---|---|
| `/bounty gui` (or **B**) | Open the bounty board |
| `/bounty place <player> [public \| private <players…>]` | Bounty with the item in your main hand |
| `/bounty addhunter <id> <player>`, `/bounty makepublic <id>` | Widen your bounty |
| `/bounty cancel <id>`, `/bounty resume <id>` | Start or withdraw the cancel notice |
| `/bounty list`, `/bounty info <id>`, `/bounty history` | Browse bounties |
| `/bounty claim` | Collect everything waiting for you |
| `/bounty admin list`, `admin cancel <id>`, `admin hearts <player>`, `admin clearimmunity <player>` | Operators (maintenance) |

## Server config (`serverconfig/bloodcontracts-server.toml`)
| Option | Default | |
|---|---|---|
| `maxActiveBountiesPerSponsor` | 3 | Active bounties per player |
| `maxHunterListSize` | 20 | Max hunters on a private bounty |
| `cancelNoticeMinutes` | 30 | Cancel notice (real time) |
| `maxHeartsLostPerDeath` | 1 | Hearts lost per death under contract |
| `maxHeartsLost` / `maxHeartsGained` | 5 / 5 | Heart floor and ceiling |
| `heartRegenMode` | PROGRESSIVE | `PROGRESSIVE` or `PERMANENT` |
| `heartRegenOnlineStepMinutes` | 90 | Half a heart back every … minutes of play |
| `heartRegenOfflineStepMinutes` | 720 | … or minutes offline |
| `targetImmunityMinutes` | 120 | Immunity after a collected bounty (real time) |
| `attributionWindowTicks` | 200 | A hit makes you responsible for an environmental death for 10 s |
| `cannonOperatorWindowTicks` | 200 | A cannon shot is yours for 10 s after touching its controls |
| `creditTamedPets` | true | Pet kills pay their owner |
| `rewardBlacklist` | [] | Items that cannot be rewards |
| `showSponsor` / `alertRevealsSponsor` | false / true | Show the sponsor on the board / in the alert |
| `historySize` / `repeatPairThreshold` | 200 / 2 | History size / flag a hunter who keeps collecting on the same target |

---

## Compatibility
- **Minecraft 1.21.1, NeoForge 21.1.248+.** Required on **both server and client**, same version.
- Optional: **Create Big Cannons 5.11.4+** (with Create 6.0.x and Ritchie's Projectile Library),
  **CBC Enchanced Shells 1.4.0+**. Without them everything works the same, except cannon shot attribution.
- **Updating from 1.0.x**: bounties, escrowed items and pending rewards are migrated automatically. Back up your world
  first, and update every client at the same time.

## FAQ
**Can two friends farm each other?** Items, yes, but it gains nothing over just handing them over. Hearts are bounded
by the floor, the ceiling and the 2-hour immunity.

**Can an ally of the target collect my bounty?** On a public bounty, yes. Make it private to prevent that.

**What if I'm offline when I win?** The reward waits in "To claim", even across restarts.

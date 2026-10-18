![Blood Contracts](HEADER_IMAGE_URL)

# Put a price on your friends' heads.

**Blood Contracts** adds player-funded bounties to your server. Pick a target, put real items on their head, and
let the whole server hunt them down. Whoever takes them out claims the loot.

The server never creates a single item: every reward comes out of a player's inventory. It's made for small
servers of friends (Create bases, everyone in their corner) that want a reason to go out, gear up and settle scores,
without organised events or a permanent war zone.

---

## How it works

1. **Sign the contract.** Press **B** (or `/bounty gui`), choose any player the server knows (online *or offline*),
   drop the reward in the slot and sign. Need more? **"+ items"** opens up to **9 different stacks**.
2. **The target is warned. Loudly.** A full-screen alert: blood-red pulsing vignette, shaking title, a curse sting and
   a racing heartbeat. Offline targets get it the moment they log in.
3. **Hunt.** Sword, bow, TNT, your tamed wolf, a well-timed shove into the void or lava, or a **Create Big Cannons**
   shell from 300 blocks away.
4. **Claim the blood money.** Rewards wait in the board's **"To claim"** tab. A toast tells you, and nothing lands in
   your inventory unasked.

![Sign the contract](FEATURE_CONTRACTS_URL)

---

## Features

### 🩸 Contracts funded by players, not by the server
- Real items go into escrow the moment you sign: one stack, or up to 9 different ones.
- Several players can stack bounties on the same target. The hunter collects **all** of them.
- Cancel your own contract any time... unless the target took damage from a hunter in the last 30 seconds (no
  cancelling mid-fight).
- Bounties on **offline players**: anyone who ever joined the server is a valid target.

### 🎯 Fair kills only
Smart kill attribution that never pays the wrong person. When in doubt, nobody gets paid and the bounty stays open.
- Arrows, tridents, TNT, potions: the **player behind it** gets paid, never the projectile.
- Tamed wolves: their owner gets paid (configurable).
- **Knocked into the void, lava, a fall or fire?** The last player who hit the target within 10 seconds collects.
- `/kill`, mobs, machines and Create deployers (fake players): **nobody**.
- The sponsor killing their own target does **not** get their item back. Other sponsors' bounties still pay them.

![Fair kills only](FEATURE_HUNT_URL)

### 💥 Create Big Cannons support (optional)
Cannon shells have no owner in CBC, so Blood Contracts works out who fired:
- the player who **pulled the lever, pressed the button or turned the crank next to the cannon mount** within the last
  10 seconds, or the player **riding the cannon carriage**;
- impacts, HE blasts, shrapnel, grapeshot and flak are all credited;
- a cannon fired by a redstone clock with nobody at the controls credits **nobody**.

Also supports **CBC Enchanced Shells** ("More Shells"): Mk.I-V shells, cluster munitions, rockets launched from a
rocket base, missile mounts.

### 🔔 Scary alerts, quiet rewards
- The target gets a full-screen alert, not a chat line. Whether it names the sponsor is configurable.
- Rewards and refunds wait in the **"To claim"** tab, with a toast notification. You claim one stack or everything.
  If your inventory is full, the items drop at your feet, are reserved for you, and never despawn.

![You've been marked](FEATURE_ALERT_URL)
![Blood money](FEATURE_CLAIM_URL)

### 🏆 Advancements
| | Advancement | How |
|---|---|---|
| 📜 | **Blood Contracts** | Sign your first contract on a "friend"'s head |
| 💰 | **Blood Money** | Collect a bounty |
| 💀 | **Public Enemy No. 1** *(challenge)* | Have 3 active bounties on your head at once |
| 💣 | **Special Delivery** *(challenge, with Create Big Cannons)* | Collect a bounty with a cannon shot |

### 🔒 Safe by design
- **The server never creates items.** At any time, an escrowed item is in exactly one place: the escrow, a pending
  reward, or a player.
- The server is authoritative: the client only sends intentions (target, bounty id), never items.
- Bounties and pending rewards are saved together, so a crash can't lose or duplicate anything. Items from a removed
  mod are kept untouched until the mod comes back.
- Backed by **more than 100 automated tests** (JUnit and in-game GameTests, including a real Create Big Cannons cannon
  being assembled and fired).

---

## Commands
| Command | |
|---|---|
| `/bounty gui` (or key **B**) | Open the bounty board |
| `/bounty place <player>` | Bounty with the item in your main hand (online or offline target) |
| `/bounty list`, `/bounty info <id>`, `/bounty history` | Browse bounties |
| `/bounty cancel <id>` | Cancel your own bounty (refund goes to "To claim") |
| `/bounty claim` | Claim everything waiting for you |
| `/bounty admin list`, `/bounty admin cancel <id>` | Operators |

## Server config (`serverconfig/bloodcontracts-server.toml`)
| Option | Default | |
|---|---|---|
| `attributionWindowTicks` | 200 | How long a hit keeps you responsible for an environmental death (10 s) |
| `cannonOperatorWindowTicks` | 200 | How long after touching the controls a cannon shot is yours (10 s) |
| `creditTamedPets` | true | Pet kills pay the owner |
| `cancelLockSeconds` | 30 | No cancelling while the target is in combat |
| `maxActiveBountiesPerSponsor` | 5 | Open bounties per player |
| `rewardBlacklist` | [] | Items that can't be used as rewards |
| `showSponsor` | false | Show sponsor names on the board |
| `alertRevealsSponsor` | true | The target's alert names the sponsor |
| `historySize` | 200 | Closed bounties kept in the history |

The damage types counted as "environmental" are a datapack tag (`bloodcontracts:environmental`), so you can extend it.

---

## Compatibility
- **Minecraft 1.21.1, NeoForge 21.1.248+.** Required on **both server and client**.
- Optional: **Create Big Cannons 5.11.4+** (with Create 6.0.x and Ritchie's Projectile Library),
  **CBC Enchanced Shells 1.4.0+**. Without them, everything except cannon attribution works the same.

## FAQ
**Can two friends farm each other?** They can, and it gets them nothing they couldn't get by handing the item over:
no item is ever created.

**What if I'm offline when I win?** The reward waits safely in "To claim", even across restarts.

**Can I put a bounty on someone who isn't online?** Yes, on anyone who has joined the server at least once.

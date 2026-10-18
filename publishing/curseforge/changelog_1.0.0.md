## Blood Contracts 1.0.0, first release (Minecraft 1.21.1, NeoForge)

- **Player-funded bounties.** Put 1 to 9 different item stacks in escrow on any player the server knows, online or
  offline. Several sponsors can stack bounties on the same target.
- **Fair kill attribution.** Projectiles, TNT and tamed pets credit the player behind them. Void, lava, fall and fire
  deaths credit the last attacker within 10 s. Mobs, `/kill`, machines and fake players credit nobody, and the sponsor
  never collects their own bounty.
- **Create Big Cannons support** (optional): shots are credited to the player who operated the cannon in the last
  10 s, including blasts, fragments and CBC Enchanced Shells munitions and rockets.
- **Bounty board** (key B): open contracts, rewards to claim, history. Wanted-poster style, with target faces.
- **Target alert:** full-screen, pulsing and shaking, with heartbeat. Shown on login to offline targets.
- **Claimable rewards:** winnings and refunds wait on the board, announced by a toast.
- **4 advancements**, including 2 challenges.
- Commands: `/bounty place | list | info | cancel | claim | history | gui | admin`.
- Configurable: attribution windows, cancel lock, limits, blacklist, pet credit, sponsor visibility, history size.
- No item is ever created or duplicated by the server. Over 100 automated tests.

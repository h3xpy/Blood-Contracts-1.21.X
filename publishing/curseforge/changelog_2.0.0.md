## Blood Contracts 2.0.0

**Update every client together with the server** (the network protocol changed), and **back up your world first**.
Bounties, escrowed items and pending rewards from 1.0.x are migrated automatically; old bounties become public.

### Bounties
- **Public or private bounties.** A private bounty can only be collected by the hunters you name (up to 20); the list
  stays secret. Bounties can only be widened: add hunters, or make them public.
- **One bounty per target and sponsor**, at most **3 active bounties** per player (new installs; see config below).
- **Cancelling comes with a 30-minute notice**: the bounty stays collectable with a countdown, and can be resumed.
  The 1.0 combat lock is gone.

### Hearts and immunity
- A death under contract costs the target **1 heart**, gained by the sponsor of the oldest bounty. Bounds: −5 / +5.
- Hearts come back half a heart every **1h30 of play** or every **12h offline** (or never, in `PERMANENT` mode).
- After a collected bounty the target is **immune for 2 hours** of real time.
- Animations when you gain or lose a heart.

### Interface
- New bounty board: **wanted posters** with real faces and stamps (private, immune, expires in…, you can collect),
  target detail, my bounties, to claim, history, and a **How to** tab using your server's settings.
- Bookmark tabs; the creation screen gets a public/private panel with hunter selection.
- The "price on your head" alert is now a centered banner, and waits until you leave any menu (picture and sound).
- New notifications: items to claim, and "contract fulfilled" when your target goes down.

### Commands
- `place <player> [public | private <players…>]`, `addhunter`, `makepublic`, `resume`; `cancel` now starts the notice.
- Operators: `admin cancel` (immediate), `admin hearts <player>`, `admin clearimmunity <player>`.

### Config
- New: `cancelNoticeMinutes`, `maxHunterListSize`, `repeatPairThreshold`, `maxHeartsLostPerDeath`, `maxHeartsLost`,
  `maxHeartsGained`, `heartRegenMode`, `heartRegenOnlineStepMinutes`, `heartRegenOfflineStepMinutes`,
  `targetImmunityMinutes`.
- `maxActiveBountiesPerSponsor` defaults to 3 instead of 5. **An existing config file keeps 5**: change it by hand if
  you want the new limit.
- `cancelLockSeconds` is obsolete and ignored.

### Fixes
- Items left in the creation screen are no longer dropped on the ground when you disconnect.
- A name taken over by another player now targets its current owner.
- Rewards with huge contents (e.g. a shulker box full of books) no longer make the board disconnect its viewer.
- A death cancelled by another mod (revive mods) no longer pays bounties.

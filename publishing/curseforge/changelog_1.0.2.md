## Blood Contracts 1.0.2

- **Fix: the "price on your head" alert could be missed.** When it arrived during a loading screen (joining the server,
  changing dimension), its timer and sounds ran out before the world was visible. The alert now waits until you are
  actually in the world (plus 2 seconds after joining) before it starts, and it also shows with the HUD hidden (F1).
- **Real faces for offline players.** The bounty board and the creation screen used a default skin for anyone not
  online. The server now remembers each player's skin at login and sends it along; if none is known yet, the client
  looks it up from Mojang. Offline players keep their real head.

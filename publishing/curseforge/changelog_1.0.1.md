## Blood Contracts 1.0.1

- **Fix:** players who joined the world before Blood Contracts was installed could not be targeted until they logged in
  again. On startup, the server now registers every player who has a save on the world (names from the server's
  user cache), so they show up in the bounty screen and in `/bounty place` right away.

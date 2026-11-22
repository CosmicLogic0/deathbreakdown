# Changelog

## 1.1.0
### New
- **Death history:** your last 5 deaths are kept (set it from 1 to 20). Browse them with **<** and **>** in the recap screen, or `/db history` and click one. They're saved with the world, so they're still there after you leave, reload or restart.
- **Tip line** under the summary, for example "Lava did 80% of the damage." or "Your gear stopped 60% of the damage."
- **Copy as text:** a Copy button in the recap screen (and `[copy]` in chat for players without the mod, on 1.15+), ready to paste into Discord or a bug report.
- **Settings screen:** every option with a short explanation. Open it from the mod list's Config button or the recap screen's Settings button.
- **New commands:** `/db help`, `/db history`, `/db show <number>`, `/db copy`, and for ops `/db player <name>` to see another player's last recap.

### Improved
- The chat hover shows the newest 6 hits (older ones are summed up), so it always fits on the screen.
- Pressing K after leaving and rejoining a server brings back your saved recap.
- `/db reload` and the log say exactly what was wrong in the config file, and bad values fall back to the defaults. Your 1.0.0 settings are kept.
- The recap screen fits at big GUI scales and in small windows: the legend wraps and long names are shortened.

### Fixed
- The one-line chat summary after a death never appeared when you had the mod installed.
- The recap was lost when you left and rejoined, or reloaded a single-player world.
- The legend under the recap ran off both edges of the screen at GUI scale 4.
- The chat hover ran off the screen when you took 7 or more hits.
- `/deathbreakdown help`, `history` and the other commands showed nothing on servers with the `sendCommandFeedback` game rule turned off.
- Minecraft 1.21.11 and 26.x logged errors about the mod's resource pack info (NeoForge 26.x too).
- In the settings screen, the "Columns in the recap" label touched the buttons.

## 1.0.0
First release.
- Recap screen when you die: every hit from your last 10 seconds, one row each, with the final blow highlighted
- For each hit: who or what hit you (and with what), the damage type, the damage, and how much your armor, your enchantments and other things (Resistance, absorption hearts, shields) blocked, plus the health you had left
- Press **K** (or use `/deathbreakdown`) to open your last recap again
- Short chat summary after each death, with hover details. Players without the mod see it too.
- Config `config/deathbreakdown.properties`: time window, which columns to show, hearts or points, open on death, chat summary
- Forge 1.12.2 to 26.3 and NeoForge 1.20.1 to 26.3

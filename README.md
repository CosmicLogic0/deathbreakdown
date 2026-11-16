# Death Breakdown

When you die, see exactly what happened. A clean recap shows every hit from your last 10 seconds:
who or what hit you, the damage type, the damage, how much your armor and enchantments blocked, and the final blow.

- Keybind to reopen your last recap (default **K**)
- Config for the time window and which columns are shown
- Small and modpack friendly
- Paper version for servers with vanilla players: [deathbreakdown-plugin](https://github.com/CosmicLogic0/deathbreakdown-plugin)

| Folder | What's in it |
|---|---|
| `common/`, `forge/`, `neoforge/` | The mod (MultiLoader layout, build setup copied from LogicLib) |
| `core/` | Plain-Java core: hit log, armor/enchant math, labels, text, config. **Master copy**; the plugin repo has a copy. |
| `ci/` | Automatic tests, copied from the LogicLib repos |
| `docs/` | CurseForge page text |
| `art/` | Icons and the script that draws them |

Targets: Minecraft 26.3, 26.2, 26.1, 1.21.11, 1.21.1, 1.20.1, 1.19.2, 1.18.2, 1.16.5, 1.12.2.

MIT license.

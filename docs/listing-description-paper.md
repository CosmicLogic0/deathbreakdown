# Death Breakdown Plugin

**See exactly what killed you, on a vanilla client.**

⚠️ **Requires [LogicLib Plugin](https://www.curseforge.com/minecraft/bukkit-plugins/logiclib-plugin)** (CosmicLogic's shared library for Paper). Install it next to this plugin.

This is the Paper version of [Death Breakdown](https://www.curseforge.com/minecraft/mc-mods/death-breakdown). When a player dies, they get a recap of the last 10 seconds. Nobody needs to install a mod.

## What the recap shows

- **Who or what hit you**: the mob, player, projectile, block or effect
- **Damage type**: melee, arrow, fire, lava, fall, explosion, magic, poison, wither, drowning and more
- **Damage**: how much each hit took off
- **Blocked**: how much **armor** stopped and how much **enchantments** stopped, shown separately
- **Final blow**: highlighted

## How players see it

- **In chat**: a short summary line right after death. Hover over a hit for its details.
- **As a book**: click **[Open recap]** in chat to open the full recap as a book (nothing goes into the inventory).
- `/deathbreakdown` (or `/db`) reopens your last recap any time.

## Config

`config.yml`: the time window (default 10 seconds), chat or book or both, which columns are shown, and the message colors.

## Versions

One jar for Paper **1.12.2, 1.16.5, 1.18.2, 1.19.2, 1.20.1, 1.21.1, 1.21.11, 26.1.2, 26.2 and 26.3**.

## Requirements
- **[LogicLib Plugin](https://www.curseforge.com/minecraft/bukkit-plugins/logiclib-plugin)** in your `plugins` folder (required)
- Paper (or a Paper fork) on one of the versions above

MIT license.

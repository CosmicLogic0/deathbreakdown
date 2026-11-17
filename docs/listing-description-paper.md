![Death Breakdown Plugin](https://raw.githubusercontent.com/CosmicLogic0/deathbreakdown-plugin/main/docs/images/header-plugin.png)

**See exactly what killed you, on a vanilla client.**

> ⚠️ **Requires [LogicLib Plugin](https://www.curseforge.com/minecraft/bukkit-plugins/logiclib-plugin).** Put it in your `plugins` folder too.

Your players die and all they get is "slain by Skeleton". This is the **server plugin version** of [Death Breakdown](https://www.curseforge.com/minecraft/mc-mods/death-breakdown): every death comes with a recap of the last 10 seconds, and **nobody has to install a mod**.

---

🧩 **Works on vanilla clients. Nothing to install on the player's side.**

## 🔥 What it does

### 💬 A recap in chat
Right after dying, players get a one-line summary: who killed them, how much damage they took and what their armor and enchantments stopped. **Hover** any hit for the details.

### 📖 Or as a book
Click **[Open recap]** and the full recap opens as a book: one page per hit, final blow last. Nothing is added to the inventory. (On 1.12.2, which can't open books, the pages show in chat.)

### 🛡️ Armor and enchantments, separately
Every hit shows how much **armor** stopped and how much **enchantments** stopped, plus Resistance, absorption hearts and shields.

### 🔁 See it again
`/deathbreakdown` (or `/db`) reopens your last recap anytime.

### 💾 It remembers
Settings live in `plugins/DeathBreakdown/config.yml`: the time window, hearts or health points, chat on death, opening the book on respawn, and which columns show. `/db reload` applies changes.

## 🆚 Plugin vs mod
The plugin gives the same recap in **chat and a book**. The mod adds the **recap screen** and the **K key**, but players have to install it.

## 📦 Requirements
- **Paper** (or a Paper fork): Minecraft 1.12.2, 1.16.5, 1.18.2, 1.19.2, 1.20.1, 1.21.1, 1.21.11, 26.1, 26.2 and 26.3. One jar for all of them.
- **Requires [LogicLib Plugin](https://www.curseforge.com/minecraft/bukkit-plugins/logiclib-plugin)** (shared code for my plugins). The plugin won't start without it.
- Server only. Players don't need anything.

Source and bug reports: https://github.com/CosmicLogic0/deathbreakdown-plugin

---

## ❤️ Support the plugin
![Help keep this mod alive](https://raw.githubusercontent.com/CosmicLogic0/deathbreakdown-plugin/main/docs/images/donate.png)

Death Breakdown is free. Donations keep it up to date with fixes, new Minecraft versions and new features. [Donate with PayPal](https://www.paypal.com/paypalme/DylanEMason) - any amount helps. Thank you!

---

🎮 Want to see it in action? Come play on **Logic Survival** (`play.logicsurvival.fyi`), the survival server run by the maker of this plugin.

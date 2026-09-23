# The Settings Menu

Every setting can be changed in-game, through real Minecraft pop-up menus with sliders, switches and buttons. Changes take effect straight away and are written back to the settings file for you.

No resource pack. No fake menu made of chat messages. These are the real thing.

***

## Opening it

```
/betterend
```

Or `/betterend menu`, which is the same thing. You need `betterend.admin`, which ops have by default.

{% hint style="info" %}
**In-game only.** Pop-up menus are drawn by the player's game, so the menu can't open from the server console. From the console, use `/betterend list`, `info`, `reset` and the rest instead.
{% endhint %}

***

## The main menu

| Button | What it opens |
|---|---|
| **Elytra Frames** | Who can claim, what it costs, the floating note and the shimmer |
| **Choose Cost Item** | Pick what an elytra costs, straight from your inventory |
| **Per-player Loot** | Everyone's own chest copies, and how often they refresh |
| **Protection** | What players can and can't break or build in a city |
| **Finding Cities & Resets** | Finding cities, worlds to leave alone, saved copies |
| **Storage** | SQLite or MySQL, and the MySQL login |
| **Updates & Stats** | Update checks, anonymous stats, error reports, extra logging |
| **Setup Tour** | Run the guided walkthrough again |
| **Close** | Close without saving |

The menu also shows a line telling you where you stand, either "Claims are currently free." or "A claim currently costs 2 x ender pearl."

Every page has **Back**, **Save**, **Save & Close** and **Close**. Nothing changes until you press one of the Save buttons.

***

## Elytra Frames

* **Feature enabled** - turns renewable frames on or off entirely
* **Who can claim, how often** - once per ship, on every loot refresh, or once per player ever
* **Cost** - how many of the cost item a claim takes, `0` for none
* **XP levels per claim** - levels taken on top of the item, `0` for none
* **Price doubles with each elytra bought** - 10 levels, then 20, then 40. The price drops back once a loot refresh window has passed since each purchase
* **Floating hint above the frame** - the little label showing the cost, or "Punch to claim"
* **Shimmer around the frame** - a faint particle effect when a player is near, so it gets noticed
* **Choose Cost Item** - jumps to the item picker

{% hint style="warning" %}
Opening the cost-item picker from this screen **throws away changes you haven't saved**. Save first, then pick the item.
{% endhint %}

## Per-player Loot

* **Per-player chest loot** - the main on/off switch
* **Refresh window (hours, 0 = never)** - a slider from 0 to 168, which is one week

## Protection

* **Grief protection** - the main on/off switch
* **What is protected** - the whole city, or only the ship and the city's loot chests
* **Players may take the ship's dragon head** - once taken, resets never put it back
* **Reach past each tower** - how many blocks protection extends past a tower's walls. `3` fits vanilla cities
* **Also stop players building inside**
* **Also protect from explosions**
* **Tell players why their break or build was stopped** - the message just above the hotbar

## Finding Cities & Resets

* **Register new End Cities automatically**
* **Also check areas already loaded at startup**
* **Worlds to leave alone** - world names separated by commas, for example `world_the_end_2, event_end`
* **Save a copy of each city when it's found**
* **Put blocks back on every loot refresh** - off by default
* **Largest saved copy** - a safety limit, in millions of blocks. You should never need to change it

## Storage

SQLite or MySQL, plus the MySQL address, port, database name, username and password. **These take effect after a restart.** The password box always starts empty so it's never shown on screen; leave it empty to keep the saved one. Switching storage type starts from an empty database, nothing is copied across.

## Updates & Stats

* **When a new version comes out** - off, check quietly, tell staff, let staff download it, or download it by itself for the next restart
* **Check every (hours)**
* **Wait before taking a brand-new release**, and **how long to wait**
* **Send anonymous usage stats** and **send automatic error reports** - see [Metrics and Privacy](../configuration/metrics.md)
* **Extra logging** - noisy, for chasing a problem

Everything on this page except extra logging takes effect after a restart.

***

## Choosing a cost item

**Choose Cost Item** opens your inventory rather than a pop-up menu, because it has to show you your actual items.

Click any item in your inventory and that becomes the cost. This works with:

* Ordinary items
* Items with custom names, lore or enchantments
* Custom items from other plugins

The item is stored exactly as it is, so a specifically named, specifically enchanted item stays that exact item.

Then set how many with the slider. **The slider won't go past what that item can stack to**, so 64 for most things, 16 for ender pearls, 1 for a bed. Setting it to `0` makes claims free again.

{% hint style="info" %}
**Running Better Anti-Dupe?** The picker warns you if you choose an item it watches, because charging a watched item overlaps with how it tracks ownership.
{% endhint %}

***

## The setup tour

```
/betterend setup
```

Five screens, roughly two minutes, each with a plain explanation and one or two things to set. You move with **[Back] [Next] [Finish later]**, and **each step saves as you pass it**, so quitting halfway keeps what you've already answered.

The screens, in order:

1. **Elytra frames** - switch them on, and choose how often a player can claim
2. **Elytra cost** - what a claim costs in items and XP levels, and the floating note
3. **Per-player loot** - switch it on, and how long before loot comes back
4. **Protection** - grief protection, whole city or only the ship, and whether to tell players when a break is blocked
5. **Saved copies and resets** - saving a copy of each city, and putting blocks back

The welcome screen offers **Start the tour**, **Skip, defaults are fine**, or **Close**, which asks again next time.

{% hint style="info" %}
**The reminder.** Ops get a one-time nudge when they join, until the tour is finished or skipped. Skipping counts, so it stops either way. The tour really is optional and the defaults are ready to use.
{% endhint %}

***

## The menu and the settings file

Changes made in the menu are written straight back to `config.yml`, so the two never disagree. You can edit the file directly instead if you prefer. Run `/betterend reload` afterwards so the plugin picks the changes up.

{% hint style="warning" %}
**Don't edit the file and use the menu at the same time.** If you have `config.yml` open in an editor while you save from the in-game menu, your editor is holding an old copy and will overwrite the menu's changes when you save it. Reload after editing, and close the file before using the menu.
{% endhint %}

***

## What's next?

{% content-ref url="basic-configuration.md" %}
[basic-configuration.md](basic-configuration.md)
{% endcontent-ref %}

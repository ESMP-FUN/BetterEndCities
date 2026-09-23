# The Settings Menu

Change every setting in game, with sliders, switches and buttons. Changes are saved to `config.yml` for you. No resource pack needed.

***

## Opening it

```
/betterend
```

Or `/betterend menu`. You need `betterend.admin` (ops have it).

{% hint style="info" %}
**In game only.** From the console, use the [commands](../reference/commands.md) instead.
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

Every page has **Back**, **Save**, **Save & Close** and **Close**. Nothing changes until you press a Save button.

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
Opening the item picker from this page **discards unsaved changes**. Save first.
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

* **When a new version comes out** - off, check quietly, tell staff (who can then download it), or download it by itself for the next restart
* **Check every (hours)**
* **Wait before taking a brand-new release**, and **how long to wait**
* **Send anonymous usage stats** and **send automatic error reports** - see [Metrics and Privacy](../configuration/metrics.md)
* **Extra logging** - noisy, for chasing a problem

Everything on this page except extra logging takes effect after a restart.

***

## Choosing a cost item

1. Open **Choose Cost Item**. A chest screen opens.
2. Shift-click an item in your own inventory. It's copied as the cost, not taken.
3. Press **Save & set amount**. The Elytra Frames page opens; set **Cost** to how many.

Named, enchanted and custom items from other plugins are stored exactly. The amount can't go past what the item stacks to. **Reset to default** sets it back to a shulker shell.

{% hint style="info" %}
**Running Better Anti-Dupe?** The picker warns you if it watches the item you chose, because nobody could pay with it.
{% endhint %}

***

## The setup tour

```
/betterend setup
```

Five screens, each with a short explanation and a few settings. Move with **Back**, **Next** and **Finish later**. Each step saves when you press Next, and `/betterend setup` resumes where you stopped.

The screens, in order:

1. **Elytra frames** - switch them on, and choose how often a player can claim
2. **Elytra cost** - what a claim costs in items and XP levels, and the floating note
3. **Per-player loot** - switch it on, and how long before loot comes back
4. **Protection** - grief protection, whole city or only the ship, and whether to tell players when a break is blocked
5. **Saved copies and resets** - saving a copy of each city, and putting blocks back

The welcome screen offers **Start the tour**, **Skip, defaults are fine**, or **Close**.

{% hint style="info" %}
Ops get a reminder when they join until the tour is finished or skipped.
{% endhint %}

***

## The menu and the settings file

The menu saves straight to `config.yml`. If you edit the file by hand instead, run `/betterend reload` afterwards.

{% hint style="warning" %}
**Close `config.yml` in your editor before using the menu,** or saving the file later overwrites the menu's changes.
{% endhint %}

***

## What's next?

{% content-ref url="basic-configuration.md" %}
[basic-configuration.md](basic-configuration.md)
{% endcontent-ref %}

# Per-Player Loot

Each player who opens an End City chest gets their own copy of what's inside, so the first player through doesn't empty the city for everyone.

The first time a city chest is opened, its loot is rolled once. Every player then gets their own copy of that roll. What a player takes from or leaves in their copy only affects their copy.

***

## When loot comes back

Set `loot.refresh-hours` (default `12`, `0` = never).

Each city runs its own countdown:

1. The first player to loot the city starts its countdown.
2. Until it runs out, every player keeps their own copy.
3. After it runs out, the next player to open a chest clears everyone's copies and starts a new countdown.

Loot comes back on the first visit after the time is up, not at the exact moment. Cities don't all refresh at once, and a city nobody visits does no work.

| Value | How it feels |
|---|---|
| `6` to `12` | Cities are a regular activity |
| `24` to `72` | Roughly weekly |
| `168` | Once a week |
| `0` | Never. Each player loots each chest once |

***

## What counts as city loot

Only containers that came with the city get per-player copies.

* **The city's own chests** get copies
* **The ship's brewing stand** gets copies too, so every player finds its two healing potions
* **Chests players place** stay normal and shared, even inside a tower
* **A chest holding items but no unopened loot** stays normal too. On worlds played before the plugin was installed, that's a player's own storage
* **City chests looted before the plugin was installed** get fresh End City loot for each player

{% hint style="info" %}
**Hoppers can't reach city chests,** so nobody can drain the loot everyone else is about to get. Hoppers on a chest a player placed work as normal.
{% endhint %}

***

## Changing what a chest gives

Staff with `betterend.admin` can edit what every player's first copy of a chest starts with:

1. Sneak and right-click the city chest. The **Loot Template (shared)** screen opens.
2. Add, remove or change items.
3. Close it. The change applies to every player's next fresh copy, and stays through resets.

Copies players already have aren't changed. `/betterend reset <id>` or a refresh gives everyone the new contents.

***

## Turning it off

```yaml
loot:
  enabled: false
```

City chests go back to vanilla: shared, first come first served. Elytra frames keep working.

***

## Admin commands

| Command | What it does |
|---|---|
| `/betterend reset <id>` | Clear everyone's copies, and put the blocks back if a copy of the city is saved |
| `/betterend resetloot <id> <player>` | Clear one player's copies in that city |
| `/betterend info <id>` | Show how long ago the city's countdown started |

***

## With elytra claims

With `elytra.claim-mode: per-refresh`, claims come back together with the loot. With `per-ship` or `global` they are separate.

***

## What's next?

{% content-ref url="snapshots-and-resets.md" %}
[snapshots-and-resets.md](snapshots-and-resets.md)
{% endcontent-ref %}

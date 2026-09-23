# config.yml

Every setting in `plugins/BetterEndCities/config.yml`.

{% hint style="info" %}
**You may never need this page.** Everything here can be changed in-game from [the settings menu](../getting-started/config-menu.md), and those changes are written back to this file. This page is for people who'd rather edit the file.
{% endhint %}

After editing by hand, run `/betterend reload`. Lost the file? Delete it and restart, and a fresh one is written with every default.

***

## database

```yaml
database:
  type: sqlite
  mysql:
    host: localhost
    port: 3306
    database: betterend
    username: root
    password: ""
```

| Setting | Default | What it does |
|---|---|---|
| `type` | `sqlite` | `sqlite` needs no setup. `mysql` is for several servers sharing one End. A misspelling falls back to `sqlite` with a console warning |
| `mysql.*` | | Ignored completely when `type` is `sqlite` |

[More: Storage](storage.md)

***

## discovery

```yaml
discovery:
  enabled: true
  startup-sweep: true
  excluded-worlds: []
```

| Setting | Default | What it does |
|---|---|---|
| `enabled` | `true` | Register End Cities automatically as players travel near them |
| `startup-sweep` | `true` | Check areas already loaded when the server started, so cities there aren't missed |
| `excluded-worlds` | `[]` | World names where cities are never registered. Capital letters don't matter |

Cities registered before you excluded a world keep working. Remove them with `/betterend delete <id>`.

[More: Finding Cities](../guides/city-discovery.md)

***

## elytra

```yaml
elytra:
  enabled: true
  claim-mode: per-ship
  cost:
    item: ""
    amount: 0
    levels: 0
    double-each-claim: false
  text-display: true
  frame-aura: false
```

| Setting | Default | What it does |
|---|---|---|
| `enabled` | `true` | Renewable elytra frames, on or off |
| `claim-mode` | `per-ship` | `per-ship`: one per player per ship, ever. `per-refresh`: claimable again whenever that city's loot comes back. `global`: one per player in total, across every ship |
| `cost.item` | `""` | What a claim costs. **Set this in-game**, not by hand |
| `cost.amount` | `0` | How many it takes. `0` means no item |
| `cost.levels` | `0` | Experience levels a claim also takes. `0` means none |
| `cost.double-each-claim` | `false` | Each elytra a player buys costs double the one before. The price drops back once `loot.refresh-hours` has passed since each purchase |
| `text-display` | `true` | Float a small note above the frame showing the cost, or "Punch to claim". Right-clicking the frame always shows the player's own price |
| `frame-aura` | `false` | A faint shimmer of particles around the frame when a player is nearby |

{% hint style="warning" %}
`cost.item` stores a whole item, not just an item name. Use `/betterend`, **Choose Cost Item**. Editing it by hand will not work.
{% endhint %}

[More: Elytra Claims](../guides/elytra-claims.md)

***

## loot

```yaml
loot:
  enabled: true
  refresh-hours: 12
```

| Setting | Default | What it does |
|---|---|---|
| `enabled` | `true` | Every player gets their own copy of a city's chests |
| `refresh-hours` | `12` | How long before a city's loot comes back. `0` means never |

Each city counts down on its own, starting when someone first loots it, so cities never all refresh at once and cities nobody visits do no work. Only chests that came with the city count. Chests players place stay completely normal.

The in-game slider goes up to 168 hours, which is one week. Larger numbers are accepted in the file.

[More: Per-Player Loot](../guides/per-player-loot.md)

***

## protection

```yaml
protection:
  enabled: true
  scope: whole-city
  dragon-head-takeable: false
  piece-padding: 3
  block-place: true
  block-explosions: true
  notify-denied: true
```

| Setting | Default | What it does |
|---|---|---|
| `enabled` | `true` | Grief protection, the main switch |
| `scope` | `whole-city` | `whole-city`: every tower, bridge and the ship. `ship-only`: just the ship and the city's own loot chests, so towers and bridges can be broken, built on and blown up. Pair it with `snapshot.auto-reset-on-refresh` |
| `dragon-head-takeable` | `false` | Players can take the ship's dragon head. Once taken it is gone for good, and resets never put it back |
| `piece-padding` | `3` | How many blocks protection reaches past each tower, to cover its trim and decoration |
| `block-place` | `true` | Stop players building inside a protected part of the city |
| `block-explosions` | `true` | Protect city blocks from creepers, TNT and other explosions |
| `notify-denied` | `true` | Show a message just above the hotbar when a break or build is blocked |

Each tower, bridge and the ship is protected separately rather than as one big box, so the space between them stays buildable.

{% hint style="warning" %}
`betterend.bypass.protection` is given to **ops** by default. Testing protection while opped will make it look broken.
{% endhint %}

[More: Protection](../guides/protection.md)

***

## snapshot

```yaml
snapshot:
  max-cells: 3000000
  auto-capture: true
  auto-reset-on-refresh: false
```

| Setting | Default | What it does |
|---|---|---|
| `max-cells` | `3000000` | A limit on how many blocks one saved copy may hold. Far larger than any real city |
| `auto-capture` | `true` | Save a copy of each city as soon as it's found |
| `auto-reset-on-refresh` | `false` | Also put the blocks back whenever a city's loot comes back |

{% hint style="warning" %}
**`auto-reset-on-refresh` is off for a reason.** Putting blocks back rewrites the whole city, which can suffocate or shove aside players standing inside, and erases anything built in there. Turn it on if cities are just somewhere to farm. Leave it off if players make bases in them.
{% endhint %}

[More: Saved Copies and Resets](../guides/snapshots-and-resets.md)

***

## metrics

```yaml
metrics:
  enabled: true
  error-reporting: true
```

| Setting | Default | What it does |
|---|---|---|
| `enabled` | `true` | Anonymous usage numbers. Nothing about your players, just which settings are in use. Setting this to `false` also turns off error reporting |
| `error-reporting` | `true` | Report this plugin's own errors automatically, so bugs get fixed without you having to file them. Other plugins' errors are never captured, and addresses, file paths, database passwords and player UUIDs are stripped out first |

[More: Metrics and Privacy](metrics.md)

***

## update *(optional, not in the default file)*

Add this only if you want to change how update checking behaves. It overrides what the plugin ships with.

```yaml
update:
  mode: notify
  check-interval-hours: 6
```

| Setting | Default | What it does |
|---|---|---|
| `mode` | `notify` | `off`, `check-only`, `notify` (tells admins, downloads nothing), `download`, or `auto-stage` |
| `check-interval-hours` | `6` | How often to check |

`download` and `auto-stage` fetch updates and put them in place for the next restart. Nothing is ever swapped out underneath a running server.

[More: Commands](../reference/commands.md#how-much-it-does-on-its-own)

***

## setup

```yaml
setup:
  completed: false
```

Set to `true` for you once the `/betterend setup` tour has been finished or skipped. While it's `false`, ops get a one-time reminder when they join. Set it back to `false` to see the reminder again.

***

## debug

```yaml
debug:
  verbose-logging: false
```

Extra logging for chasing down a problem. Very noisy, so turn it on, reproduce the problem, then turn it back off. The output is useful to attach to a bug report.

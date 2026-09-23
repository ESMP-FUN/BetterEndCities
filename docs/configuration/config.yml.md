# config.yml

Every setting in `plugins/BetterEndCities/config.yml`.

{% hint style="info" %}
Every setting here is also in the [settings menu](../getting-started/config-menu.md), which saves back to this file.
{% endhint %}

After editing by hand, run `/betterend reload`. `database` and `metrics` changes, and the `update` section, need a restart instead. To get a fresh file with every default, delete it and restart.

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
| `mysql.*` | | Ignored when `type` is `sqlite` |

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
| `claim-mode` | `per-ship` | `per-ship`: one per player from each ship. `per-refresh`: again each time that city's loot comes back. `global`: one per player in total |
| `cost.item` | `""` | The item a claim costs. Set it in game with **Choose Cost Item** |
| `cost.amount` | `0` | How many of the item. `0` = no item |
| `cost.levels` | `0` | XP levels a claim takes. `0` = none |
| `cost.double-each-claim` | `false` | Each elytra a player buys costs double the one before. Each purchase stops counting after `loot.refresh-hours` |
| `text-display` | `true` | A note above the frame showing the price, or "Punch to claim" |
| `frame-aura` | `false` | A faint shimmer around the frame when a player is near |

{% hint style="warning" %}
`cost.item` holds a whole item, not a name. Editing it by hand won't work.
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
| `enabled` | `true` | Each player gets their own copy of a city's chests. Chests players place stay normal |
| `refresh-hours` | `12` | Hours before a city's loot comes back, counted from its first looting. `0` = never |

The menu slider goes up to 168 (a week). Larger numbers work in the file, and the slider then goes up to them.

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
| `enabled` | `true` | The main switch |
| `scope` | `whole-city` | `whole-city`: every tower, bridge and the ship. `ship-only`: only the ship and the city's loot chests |
| `dragon-head-takeable` | `false` | Players may take the ship's dragon head once. Resets never put it back |
| `piece-padding` | `3` | Blocks protection reaches past each tower's walls |
| `block-place` | `true` | Also stop building inside, including buckets |
| `block-explosions` | `true` | Also stop creepers, TNT and other explosions |
| `notify-denied` | `true` | Tell the player why, just above the hotbar |

{% hint style="warning" %}
Ops have `betterend.bypass.protection` by default, so protection looks broken when you test it as an op.
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
| `max-cells` | `3000000` | Most blocks one saved copy may hold. Far above any real city |
| `auto-capture` | `true` | Save a copy of each city when it's found |
| `auto-reset-on-refresh` | `false` | Also put the blocks back each time a city's loot comes back |

{% hint style="warning" %}
Putting blocks back can suffocate players inside and erases anything built there. Leave `auto-reset-on-refresh` off if players build bases in cities.
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
| `enabled` | `true` | Anonymous counts of which settings are in use. `false` also turns off error reporting |
| `error-reporting` | `true` | Send this plugin's own errors, with IP addresses, file paths, passwords and player ids removed |

Both take effect after a restart.

[More: Metrics and Privacy](metrics.md)

***

## update *(optional, not in the default file)*

Changes how updates are handled. The **Updates & Stats** page of `/betterend` writes it for you. Takes effect after a restart.

```yaml
update:
  mode: notify
  check-interval-hours: 6
  hold-new-updates: false
  hold-new-updates-hours: 18
```

| Setting | Default | What it does |
|---|---|---|
| `mode` | `notify` | `off`: never check. `check-only`: check quietly, see `/betterend update status`. `notify`: tell staff, who can download it with `/betterend update download`. `download`: the same as `notify`. `auto-stage`: download it for the next restart by itself |
| `check-interval-hours` | `6` | How often to check |
| `hold-new-updates` | `false` | Wait before taking a brand-new release, in case it turns out broken |
| `hold-new-updates-hours` | `18` | How long to wait |

A downloaded update is installed on the next restart, never while the server runs.

[More: Commands](../reference/commands.md#how-much-it-does-on-its-own)

***

## setup

```yaml
setup:
  completed: false
```

Set to `true` once the `/betterend setup` tour is finished or skipped. While it's `false`, ops get a one-time reminder when they join.

***

## debug

```yaml
debug:
  verbose-logging: false
```

Extra console logging for a bug report. Turn it on, make the problem happen, then turn it off.

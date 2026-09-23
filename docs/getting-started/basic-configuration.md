# Basic Configuration

The defaults work as they are. This page covers the few settings that change how your server plays. Every one of them can be set in the [settings menu](config-menu.md); the full list is in [config.yml](../configuration/config.yml.md).

***

## The short version

```yaml
elytra:
  claim-mode: per-ship     # per-ship, per-refresh or global
  cost:
    amount: 0              # 0 = no item. Pick the item in game
    levels: 0              # 0 = no XP levels

loot:
  enabled: true
  refresh-hours: 12        # 0 = never refresh

protection:
  enabled: true
  scope: whole-city        # whole-city or ship-only

snapshot:
  auto-capture: true
  auto-reset-on-refresh: false

discovery:
  enabled: true
  excluded-worlds: []
```

***

## How rare should elytras be?

Set `elytra.claim-mode`:

| Setting | What happens | Good for |
|---|---|---|
| `per-ship` | One elytra per player from each ship | **Default.** Finding more ships still pays off |
| `per-refresh` | Claim again each time the city's loot comes back | Elytras players come back for |
| `global` | One elytra per player in total | Keeping elytras rare |

{% hint style="info" %}
Switching modes keeps existing claims. To reopen one ship for everyone, use `/betterend clearclaims <id>`.
{% endhint %}

## Should an elytra cost something?

Free by default. In `/betterend`:

1. Open **Choose Cost Item** and click an item in your inventory.
2. Open **Elytra Frames** and set how many it takes, plus any XP levels.
3. Optionally turn on **Price doubles with each elytra bought**.

[More](../guides/elytra-claims.md#claim-cost)

## How often should loot come back?

Set `loot.refresh-hours` (default `12`):

* **12 to 24** - cities are something players do regularly
* **168** (a week) - cities are an occasional trip
* **0** - never, so each player loots each chest once

Each city's timer starts when someone first loots it, so cities don't all refresh at once. [More](../guides/per-player-loot.md)

## Should blocks come back too?

Set `snapshot.auto-reset-on-refresh` (default `false`).

* **Off** - a refresh brings back loot only
* **On** - every refresh also puts the city's blocks back from its saved copy

{% hint style="warning" %}
Putting blocks back can suffocate players inside and erases anything built there. Turn it on only if cities are for farming, not for bases.
{% endhint %}

## Only protect the ship?

Set `protection.scope` to `ship-only` to leave towers and bridges open while keeping the ship and the city's loot chests protected. Pair it with `snapshot.auto-reset-on-refresh: true` so the towers are rebuilt on each refresh. [More](../guides/protection.md)

## Leave a second End world alone

List its name under `discovery.excluded-worlds` (capital letters don't matter):

```yaml
discovery:
  excluded-worlds:
    - end_vanilla
    - resource_end
```

Cities there are never registered and behave like vanilla.

{% hint style="info" %}
Cities registered before you excluded a world keep working. Remove them with `/betterend delete <id>`.
{% endhint %}

***

## Settings to leave alone

* **`protection.piece-padding`** (`3`) - how far protection reaches past each tower's walls. Higher protects empty space nearby; lower leaves trim breakable
* **`snapshot.max-cells`** (`3000000`) - a memory limit far above any real city
* **`discovery.startup-sweep`** (`true`) - checks areas already loaded at startup. Turn off only if startup is noticeably slow
* **`debug.verbose-logging`** (`false`) - noisy, for bug reports only

***

## What's next?

{% content-ref url="../configuration/config.yml.md" %}
[config.yml.md](../configuration/config.yml.md)
{% endcontent-ref %}

{% content-ref url="../guides/elytra-claims.md" %}
[elytra-claims.md](../guides/elytra-claims.md)
{% endcontent-ref %}

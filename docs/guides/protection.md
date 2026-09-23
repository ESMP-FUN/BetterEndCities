# Protection

Stops players breaking, building in or blowing up End Cities, while the empty space between towers stays free to build in.

Each tower, bridge and the ship is protected on its own, by its exact shape, instead of one big box around the city. Every block inside is protected, whatever it is.

***

## Settings

| Setting | Default | What it does |
|---|---|---|
| `protection.enabled` | `true` | The main switch. Off: cities can be broken freely |
| `protection.scope` | `whole-city` | `whole-city`, or `ship-only` for only the ship and the city's loot chests |
| `protection.dragon-head-takeable` | `false` | Players may take the ship's dragon head once |
| `protection.piece-padding` | `3` | How many blocks protection reaches past each tower's walls |
| `protection.block-place` | `true` | Also stop building inside, including water and lava buckets |
| `protection.block-explosions` | `true` | Also stop creepers, TNT and other explosions |
| `protection.notify-denied` | `true` | Tell the player why, just above their hotbar |

Pistons, withers and falling blocks such as sand and anvils can't change a protected city either. The ship's elytra frame is always protected, as part of [elytra claims](elytra-claims.md#whats-protected).

***

## Only protect the ship

Set `protection.scope: ship-only` to leave towers and bridges open. The ship stays protected, and so do the city's own loot chests, so their loot can't be lost for good.

Pair it with `snapshot.auto-reset-on-refresh: true` so the towers are rebuilt each time the loot comes back. See [Saved Copies and Resets](snapshots-and-resets.md).

## Takeable dragon head

With `protection.dragon-head-takeable: true`, a player can break the ship's dragon head and keep it. Once taken, resetting the city never puts that head back.

## Reach past each tower

`protection.piece-padding` covers the trim that sits just outside a tower's edges. `3` fits vanilla cities. Higher starts protecting empty space nearby; lower leaves trim breakable.

***

## Cushions (Minecraft 26.3)

Cushions aren't blocks, so they need the **`-mc263` download**. With it, a cushion can't be placed in a protected city, taken, blown up or knocked off by a mob. Placing follows `protection.block-place`. Cushions the game removes itself, such as one whose support block is gone, are left alone.

{% hint style="info" %}
The `-mc26` download doesn't protect cushions. On 26.3, use `-mc263`.
{% endhint %}

***

## Letting staff through

`betterend.bypass.protection` lets a player break and build freely in cities. **Ops have it by default.** Give it to a builder rank rather than turning protection off.

{% hint style="warning" %}
**Testing as an op looks like protection is broken.** Use a normal account, or remove the permission from yourself:

```
/lp user <you> permission set betterend.bypass.protection false
```
{% endhint %}

***

## Turning it off

```yaml
protection:
  enabled: false
```

Cities can be broken freely. Finding cities, per-player loot, elytra claims and saved copies keep working.

{% hint style="info" %}
**Protection off and putting blocks back on** lets players wreck a city and have it rebuilt on the next refresh.
{% endhint %}

***

## What it doesn't do

* It doesn't read land-claim plugins (Residence, Lands, GriefPrevention)
* It doesn't read or create WorldGuard regions
* It doesn't control who can open chests; [per-player loot](per-player-loot.md) handles what's inside

If your server needs one of these, ask on [Discord](https://discord.gg/qwYcTpHsNC).

***

## What's next?

{% content-ref url="snapshots-and-resets.md" %}
[snapshots-and-resets.md](snapshots-and-resets.md)
{% endcontent-ref %}

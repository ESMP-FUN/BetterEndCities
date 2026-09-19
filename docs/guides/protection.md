# Protection

End Cities protect themselves from griefing, without fencing players out of the island they're on.

***

## Each tower is protected on its own

This is the design decision that matters.

Better End Cities doesn't draw one big box around a city. It protects **each part of the city separately**: every tower, every bridge, and the ship. It knows their exact shapes, because it reads them from the world itself.

So the towers can't be stripped, but **the empty space between them is still yours to build in**. Players can build on the island, bridge between towers, and set up a base in the gaps, without fighting the plugin.

Drawing one big box would protect a huge cube of mostly empty air and make the whole island read-only. This doesn't.

### Reaching a little further

```yaml
protection:
  piece-padding: 3
```

Protection reaches this many blocks past each tower, to cover the trim and decoration that sits just outside its edges.

`3` is a good default. Raising it starts protecting genuinely empty space between towers. Lowering it leaves decoration breakable.

***

## What's protected

Everything inside a protected part of the city, **whatever the block is**:

| Setting | Default | Stops |
|---|---|---|
| `protection.enabled` | `true` | Breaking city blocks (the main switch) |
| `protection.block-place` | `true` | Building inside the city |
| `protection.block-explosions` | `true` | Creepers, TNT, and other explosions |

The elytra item frame is protected separately and always, as part of the [claim system](elytra-claims.md#whats-protected).

{% hint style="info" %}
**"Whatever the block is" is deliberate.** There's no list of protected blocks to keep up to date. If it's part of the city, it's protected. That covers purpur, end stone bricks, chests, shulkers, banners, and anything a future Minecraft update adds.
{% endhint %}

***

## Telling players why

```yaml
protection:
  notify-denied: true
```

When a break or a build is blocked, a short message appears **just above the player's hotbar** instead of nothing happening at all. Silent failures turn into support tickets. A one-line explanation doesn't.

It goes above the hotbar rather than in chat on purpose. A player mining along a wall would otherwise fill their own chat with the same message.

***

## Letting staff through

`betterend.bypass.protection` lets a player build and break freely inside cities. **Ops have it by default.**

{% hint style="warning" %}
**Testing protection as an op will look broken.** You'll break blocks freely and conclude protection isn't working. Test on a normal account, or take the permission away from yourself:

```
/lp user <you> permission set betterend.bypass.protection false
```
{% endhint %}

For builders who need to work inside cities, give the permission to a staff rank rather than turning protection off.

***

## Turning it off

```yaml
protection:
  enabled: false
```

Cities become fully breakable. Finding cities, per-player loot, elytra claims and saved copies all keep working, because protection is separate from all of them.

Worth considering on anarchy-style servers, where you might still want renewable elytras and per-player loot without the "you can't break that" layer.

{% hint style="info" %}
**Protection off, putting blocks back on** is a sensible pairing: players can destroy a city freely, and it rebuilds itself on the next loot refresh. See [Saved Copies and Resets](snapshots-and-resets.md).
{% endhint %}

***

## What it deliberately doesn't do

Protection is self-contained. Three switches and one distance, and it doesn't talk to anything else:

* **It doesn't read land-claim plugins** such as Residence, Lands or GriefPrevention.
* **It doesn't use WorldGuard**, and neither reads nor creates WorldGuard regions.
* **It doesn't control who can open chests.** Protection covers blocks. What's *inside* a chest is handled by [per-player loot](per-player-loot.md) instead.

That's the point, not a gap. There's nothing to configure, nothing to keep in step with another plugin, and no behaviour that changes depending on what else you have installed. Protection works the same on every server running it.

If your setup genuinely needs one of these, say so on [Discord](https://discord.gg/qwYcTpHsNC).

***

## What's next?

{% content-ref url="snapshots-and-resets.md" %}
[snapshots-and-resets.md](snapshots-and-resets.md)
{% endcontent-ref %}

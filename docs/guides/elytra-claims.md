# Elytra Claims

Punching the End Ship's elytra frame gives the player a new elytra, and the frame keeps its own for the next player.

Nothing changes from the player's side: same frame, same punch. There is no vault block, key item or datapack, and the ship looks exactly like vanilla.

***

## What's protected

* Players can't break the ship's frame
* Explosions, mobs and arrows can't knock it down or empty it
* Its elytra can't be taken out and kept
* Right-clicking the frame shows the player's price instead of turning the elytra

{% hint style="info" %}
**Frames players hang themselves stay normal.** Only the frame that came with the ship is managed.
{% endhint %}

***

## Claim modes

`elytra.claim-mode` sets how often a player can claim.

* **`per-ship`** (default) - one elytra from each ship. A new ship means a new elytra
* **`per-refresh`** - claim again each time that city's [loot refreshes](per-player-loot.md). Suits a short `loot.refresh-hours`
* **`global`** - one elytra in total, across every ship

{% hint style="info" %}
Switching modes keeps existing claims. `/betterend clearclaims <id>` lets everyone claim from one ship again.
{% endhint %}

***

## Claim cost

Free by default. A claim can cost an item, XP levels, or both.

1. Run `/betterend` and open **Choose Cost Item**.
2. Click an item in your inventory. It is kept, only copied as the cost.
3. Press **Save & set amount**, then set **Cost** (how many of the item) and **XP levels per claim**.

The item is stored exactly, so custom items from other plugins work: a plain diamond won't pay for a named "Elytra Voucher". The amount can't go past what the item stacks to (64 for most items, 16 for ender pearls). `0` means no item.

```yaml
elytra:
  cost:
    item: ""                  # set in game, not by hand
    amount: 0                 # how many of the item, 0 = none
    levels: 0                 # XP levels, 0 = none
    double-each-claim: false
```

{% hint style="warning" %}
**Don't edit `cost.item` by hand.** It holds a whole item, not a name. Use the picker.
{% endhint %}

### Doubling price

With `elytra.cost.double-each-claim` on, each elytra a player buys costs double the one before: 10 levels, then 20, then 40. Items double the same way. Each purchase stops counting once `loot.refresh-hours` has passed since it, so the price drops back over time. With `loot.refresh-hours: 0` purchases count forever.

If a player can't pay, nothing is taken and they're told the price.

### Ideas

* **Ender pearls** - something the End produces plenty of
* **Diamond blocks** - a simple wealth check
* **A shop voucher** - sell an "Elytra Voucher" item and let your economy plugin set the price
* **XP levels** - no item needed; turn on doubling to slow down repeat buyers

***

## Floating note and shimmer

* **`elytra.text-display`** (on) - a label above the frame: "Punch to claim" when free, otherwise the price
* **`elytra.frame-aura`** (off) - a faint shimmer of particles around the frame when a player is near

Both are only visual. Claiming works the same with them off.

***

## Better Anti-Dupe

With [Better Anti-Dupe](https://github.com/ESMP-FUN/BetterAntiDupe) installed, each claimed elytra is marked as the claimer's own, so it's never flagged as a copy. Nothing to set up. The cost-item picker warns you if you pick an item Better Anti-Dupe watches, because nobody could pay with it.

***

## Admin commands

| Command | What it does |
|---|---|
| `/betterend clearclaims <id>` | Let everyone claim from that city's ship again |
| `/betterend info <id>` | Show whether the city has a ship |

`clearclaims` leaves loot and blocks alone. Use it for an event, or after changing the price.

***

## What's next?

{% content-ref url="per-player-loot.md" %}
[per-player-loot.md](per-player-loot.md)
{% endcontent-ref %}

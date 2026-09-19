# Elytra Claims

The headline feature: the End Ship's elytra item frame becomes renewable, without ever stopping being an item frame.

***

## How it works

In vanilla, an End Ship's elytra sits in an item frame. Punching the frame pops the elytra out, and the frame is empty forever after. First player wins.

Better End Cities steps in at that punch. The player gets a **brand new elytra** in their inventory, and the frame **keeps its own elytra** for the next player. From the player's side nothing looks or feels different. Same frame, same punch, same item.

What that gives you over the vault-block approach other plugins use:

* No datapack, no key item, no custom block
* The ship looks exactly like a vanilla ship
* Players already know how to do it, so there's nothing to explain
* Shaders, resource packs and map mods all behave normally

## What's protected

The frame matters, so it's protected:

* Players can't break it
* Explosions, mobs and arrows can't destroy it
* Its elytra can't be taken out and kept

{% hint style="info" %}
**Frames players put up themselves are never touched.** Only the frame that came with the ship is managed. A frame a player hangs inside a city behaves completely normally, and can be broken, filled and emptied as usual.
{% endhint %}

***

## Claim modes

`elytra.claim-mode` decides who can claim, and how often.

### `per-ship` (default)

Each player can claim one elytra from **each ship**, once. Find a new ship, get a new elytra.

Exploring keeps paying off, and a player arriving at a picked-over ship still gets theirs. This is what most servers want.

### `per-refresh`

Claims follow the city's [loot refresh](per-player-loot.md). When a city's loot comes back, everyone can claim from its ship again.

Elytras become something players return for. Works naturally with a short `loot.refresh-hours`.

### `global`

Each player can claim **one elytra, ever**, across every ship on the server.

Keeps elytras a milestone. Note that players can still get more the vanilla way. This limits claims through the plugin, not elytras in general.

{% hint style="info" %}
**Switching is safe.** Existing claim records aren't wiped. Going from `global` to `per-ship` means a player who used their one claim can now claim at ships they haven't visited. To deliberately reopen one ship for everyone, use `/betterend clearclaims <id>`.
{% endhint %}

***

## Claim cost

Free by default. To charge for a claim:

1. `/betterend`, then **Choose Cost Item**
2. Click any item in your inventory
3. Set how many with the slider

The item is stored exactly as it is, so **custom items work properly**. A named, enchanted item from another plugin stays exactly that item, and a player's plain diamond won't pay a cost set to a custom "Elytra Voucher".

The slider **won't go past what that item can stack to**: 64 for most items, 16 for ender pearls, 1 for a bed. Setting it to `0` makes claims free again.

```yaml
elytra:
  cost:
    item: ""      # Set this in-game, not by hand
    amount: 0     # 0 = free
```

{% hint style="warning" %}
**Don't edit `cost.item` by hand.** It stores a whole item, not just an item name. The in-game picker is the only supported way to set it.
{% endhint %}

### Ideas

* **A stack of ender pearls.** Fits the theme, and uses up something the End produces plenty of
* **Diamond blocks.** A simple wealth check
* **A voucher from your shop.** Sell an "Elytra Voucher" item. The picker accepts it, and your economy plugin handles the price
* **Phantom membranes.** Fits, since players need them for repairs anyway

***

## The floating note

`elytra.text-display`, on by default, floats a small label above the ship's frame:

* **"Punch to claim"** when claims are free
* The cost when you've set one

It needs no resource pack and doesn't get in the way of the frame. Turn it off for a completely untouched-looking ship. The claim still works, players just aren't told about it.

***

## Better Anti-Dupe

If [Better Anti-Dupe](https://github.com/ESMP-FUN/BetterAntiDupe) is installed, Better End Cities spots it automatically and **marks each claimed elytra as belonging to the player who claimed it**.

Without that, renewable elytras look exactly like cheating to an anti-dupe system, since many identical elytras appear from one place. The mark means every claim is correctly credited from the moment it's made.

Nothing to set up. The cost-item picker also warns you if you pick an item Better Anti-Dupe watches.

***

## Admin commands

| Command | What it does |
|---|---|
| `/betterend clearclaims <id>` | Let everyone claim that city's ship elytra again |
| `/betterend info <id>` | Show whether the city has a ship, and who has claimed |

`clearclaims` is the one to use for a fresh event, or after you've changed the cost and want to start over. It clears claim history for one city without touching loot or blocks.

***

## What's next?

{% content-ref url="per-player-loot.md" %}
[per-player-loot.md](per-player-loot.md)
{% endcontent-ref %}

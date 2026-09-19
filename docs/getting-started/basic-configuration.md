# Basic Configuration

The defaults are ready to use. This page is about the handful of settings that actually change how your server plays. For the full file with every setting explained, see [config.yml](../configuration/config.yml.md).

Everything here can be set in-game from [the settings menu](config-menu.md) instead of editing a file.

***

## The short version

```yaml
elytra:
  claim-mode: per-ship     # per-ship, per-refresh, or global
  cost:
    amount: 0              # 0 = free. Pick the item in-game

loot:
  enabled: true
  refresh-hours: 12        # 0 = never refresh

protection:
  enabled: true
  piece-padding: 3

snapshot:
  auto-capture: true
  auto-reset-on-refresh: false   # the one big opt-in

discovery:
  enabled: true
  excluded-worlds: []
```

***

## How rare should elytras be?

This is the decision that changes your server's feel the most.

| Setting | What happens | Good for |
|---|---|---|
| `per-ship` | One elytra per player, per ship, forever | **Default.** Exploring still pays off, because more ships found means more elytras |
| `per-refresh` | Players can claim again every time the city's loot comes back | Elytras as something players return for. Works well with a short refresh |
| `global` | One elytra per player, ever, across the whole server | Keeping elytras a genuine milestone |

`per-ship` is the sensible middle. Finding a *new* ship still matters, and nobody who arrives late is left with an empty frame.

{% hint style="info" %}
Changing this doesn't wipe who has already claimed. Switching from `global` to `per-ship` lets players who already claimed do so again at other ships. To deliberately clear one ship's history, use `/betterend clearclaims <id>`.
{% endhint %}

## Should claims cost something?

Free by default. A cost turns the elytra into something to spend on, useful if your economy has too much of anything.

Set the item in-game with `/betterend`, **Choose Cost Item**, then set how many with the slider. The slider won't go past what that item can stack to.

Common choices: a stack of ender pearls, a few diamond blocks, or a custom "Elytra Voucher" your shop sells. [More](../guides/elytra-claims.md#claim-cost)

## How often should loot come back?

`loot.refresh-hours`, 12 by default.

* **12 to 24 hours** - cities are something players do regularly
* **168, which is a week** - cities are an occasional event
* **0** - never. Each player gets exactly one copy of each city, forever

Each city runs its own timer, and the timer only starts when someone first loots that city. So cities don't all refresh at the same moment, and cities nobody visits cost nothing at all. [More](../guides/per-player-loot.md)

## Should blocks come back too?

`snapshot.auto-reset-on-refresh`, `false` by default.

With it off, a refresh brings back the *loot* only. Broken blocks stay broken, though with protection on there shouldn't be many.

With it on, every refresh also rebuilds the city from its saved copy, so griefing and player changes are undone.

{% hint style="warning" %}
**Why it's off by default.** Putting blocks back rewrites the whole city at once. Players standing inside can be suffocated or shoved out of the way, and anything they've built inside the city is erased. Turn it on if you want cities kept pristine. Leave it off if your players treat cities as bases.
{% endhint %}

## Do you have a second End world?

`discovery.excluded-worlds` takes a list of world names, and capital letters don't matter:

```yaml
discovery:
  excluded-worlds:
    - end_vanilla
    - resource_end
```

Cities in those worlds are never registered, so they behave exactly like vanilla. Useful when you want one End managed and one left alone.

{% hint style="info" %}
Cities registered **before** you excluded a world keep working. Remove them yourself with `/betterend delete <id>`.
{% endhint %}

***

## Settings you probably shouldn't touch

* **`protection.piece-padding`** (`3`) - how far protection reaches past each tower to cover its trim and decoration. Raising it can start protecting empty space between towers. Lowering it leaves trim breakable.
* **`snapshot.max-cells`** (`3000000`) - a safety limit on memory, already far larger than any real city.
* **`discovery.startup-sweep`** (`true`) - catches cities that were already loaded when the server started. Only turn it off if startup is noticeably slow.
* **`debug.verbose-logging`** - very noisy. For chasing down a problem, not for everyday running.

***

## What's next?

{% content-ref url="../configuration/config.yml.md" %}
[config.yml.md](../configuration/config.yml.md)
{% endcontent-ref %}

{% content-ref url="../guides/elytra-claims.md" %}
[elytra-claims.md](../guides/elytra-claims.md)
{% endcontent-ref %}

# Metrics and Privacy

Better End Cities sends two things through [FastStats](https://faststats.dev): anonymous usage numbers, and reports of its own errors. Both are on by default and both can be turned off. This page says exactly what's sent.

***

## Usage numbers

### What's sent

Four numbers, and nothing else:

| What | Example | Why it helps |
|---|---|---|
| Which storage you use | `sqlite`, `mysql` | Whether MySQL support is worth maintaining |
| Your claim setting | `per-ship`, `per-refresh`, `global` | Which settings people actually use |
| Whether per-player loot is on | `true`, `false` | Whether servers keep it switched on |
| Roughly how many cities you have | `0`, `1-5`, `6-20`, `21-50`, `51-100`, `100+` | Realistic sizes, so speed work targets the right scale |

Plus what FastStats sends for any plugin: your server software, version counts, and an anonymous server id.

### What isn't

* **Nothing about your players.** No names, no ids, no addresses, no chat, no locations.
* **Nothing about your world.** City coordinates are never sent. The city count is a rough range, not a list.
* **No other settings.** Your MySQL password, your cost item and your world names never leave your server.

The city count is deliberately a **range**. A server with 37 cities reports `21-50`, which is enough to know whether speed work should aim at tens or hundreds of cities, and nowhere near enough to identify anyone.

***

## Error reporting

```yaml
metrics:
  error-reporting: true
```

**On by default.** When something in the plugin goes wrong, the error is sent automatically, so bugs get fixed without you having to notice one and write it up. Most never would be: a saved copy that failed to load, or a loot copy that failed to save, logs a line and the server carries on.

**Only this plugin's errors are sent.** Errors from other plugins are never captured, even when they happen at the same moment.

### What's stripped out first

Before anything leaves your server, these are replaced with placeholders:

* IP addresses
* File paths containing your username
* Database usernames and passwords
* Player UUIDs

### What each report carries

* The plugin version and your Minecraft version
* Your storage type, `sqlite` or `mysql`
* Whether you're on Folia
* Roughly how many cities you have, as a range
* Which part of the plugin was running, for example `elytra-claim` or `snapshot-load`

That's enough to reproduce a bug on the right kind of setup. Player names, chat, inventories and anything about your world are never included.

### Turning it off

```yaml
metrics:
  error-reporting: false
```

Or server-wide for every FastStats plugin, with `submitErrors=false` in `plugins/faststats/config.properties`. Setting `metrics.enabled: false` turns off both usage numbers and error reports together.

***

## Why they exist

Small plugins otherwise get built on guesswork. The usage numbers answer questions that change what gets worked on:

* If almost nobody uses MySQL, that part doesn't need more effort.
* If the `global` claim setting turns out to be popular, it deserves more features.
* If servers routinely have over a hundred cities, speed matters far more than it seems to at five.

Error reports answer a different question: what's actually broken. Almost nobody opens a ticket, and the failures that matter most are the quiet ones where the plugin logged a warning and kept going. Those would otherwise never be found at all.

***

## Turning everything off

Two separate switches. **Either one** stops both usage numbers and error reports.

### Just this plugin

```yaml
metrics:
  enabled: false
```

In `plugins/BetterEndCities/config.yml`. Confirmed at startup:

```
[BetterEndCities] FastStats Metrics: Disabled (config)
```

### Every plugin on the server

```properties
enabled=false
```

In `plugins/faststats/config.properties`. Turns FastStats off for **every** plugin on your server that uses it.

{% hint style="success" %}
**The first server start sends nothing.** FastStats writes its settings file and submits nothing until the next restart. So you can always opt out before any data leaves your server. You don't have to decide in advance.
{% endhint %}

***

## Startup messages

The console tells you where things stand on every start:

| Message | Meaning |
|---|---|
| `FastStats Metrics: Enabled` | Sending |
| `FastStats Metrics: Disabled (config)` | Off via `metrics.enabled: false` |
| `FastStats Metrics: Disabled (no project token)` | Shouldn't happen in a proper release |
| `FastStats Metrics: Failed` | It couldn't start. A warning line follows with the reason |

`Failed` is harmless, because metrics can never affect the plugin itself. Do report it if you see it, though.

***

## The public numbers

The combined totals are public: [faststats.dev/project/better-end-cities](https://faststats.dev/project/better-end-cities).

[![Servers](https://img.shields.io/endpoint?url=https%3A%2F%2Ffaststats.dev%2Fapi%2Fshields%2Fbetter-end-cities%3Fmetric%3Dservers%26color%3Dblueviolet%26icon%3D1&style=flat)](https://faststats.dev/project/better-end-cities)

***

## A note about version 0.2.0

Before 0.2.0 this plugin used bStats. If you had opted out through `plugins/bStats/config.yml`, **that opt-out no longer applies**, because it only ever covered bStats.

To stay opted out, use either switch above. The `metrics.enabled: false` setting in `config.yml` carried over unchanged and still works.

{% hint style="info" %}
**The numbers lag behind reality.** Because the first start after an update sends nothing, a server that updates appears only after its next restart. A quiet dashboard just after a release is expected, not a fault.
{% endhint %}

# Metrics and Privacy

The plugin sends anonymous usage numbers and reports of its own errors through [FastStats](https://faststats.dev). Both are on by default; this page lists exactly what's sent and how to turn each off.

***

## Usage numbers

| What | Example |
|---|---|
| Storage type | `sqlite`, `mysql` |
| Claim mode | `per-ship`, `per-refresh`, `global` |
| Per-player loot on or off | `true`, `false` |
| Roughly how many cities | `0`, `1-5`, `6-20`, `21-50`, `51-100`, `100+` |

FastStats also sends what it sends for every plugin: server software, version and an anonymous server id.

Never sent: player names, ids, addresses, chat or locations; city coordinates; your password, cost item or world names.

***

## Error reports

`metrics.error-reporting` (on) sends this plugin's own errors, never another plugin's. Before sending, these are replaced with placeholders:

* IP addresses
* File paths containing your username
* Database usernames and passwords
* Player UUIDs

Each report also says the plugin and Minecraft version, `sqlite` or `mysql`, whether it's Folia, the city-count range, and which part of the plugin was running (for example `elytra-claim` or `snapshot-load`).

***

## Turning it off

Each of these stops both usage numbers and error reports, after a restart:

* **This plugin only:** `metrics.enabled: false` in `plugins/BetterEndCities/config.yml`, or the **Updates & Stats** page of `/betterend`
* **Every plugin using FastStats:** `enabled=false` in `plugins/faststats/config.properties`

To stop only error reports: `metrics.error-reporting: false`, or `submitErrors=false` in `plugins/faststats/config.properties` for every plugin.

{% hint style="success" %}
**The first start sends nothing.** FastStats waits until the next restart, so you can opt out before anything leaves the server.
{% endhint %}

***

## Startup messages

| Message | Meaning |
|---|---|
| `FastStats Metrics: Enabled` | Sending |
| `FastStats Metrics: Disabled (config)` | Off through `metrics.enabled: false` |
| `FastStats Metrics: Disabled (no project token)` | Shouldn't appear in a release |
| `FastStats Metrics: Failed` | Couldn't start; the next line says why. The plugin works normally |

***

## Public numbers

The combined totals are at [faststats.dev/project/better-end-cities](https://faststats.dev/project/better-end-cities).

[![Servers](https://img.shields.io/endpoint?url=https%3A%2F%2Ffaststats.dev%2Fapi%2Fshields%2Fbetter-end-cities%3Fmetric%3Dservers%26color%3Dblueviolet%26icon%3D1&style=flat)](https://faststats.dev/project/better-end-cities)

<details>
<summary>Updating from 0.1.0 (bStats)</summary>

Before 0.2.0 the plugin used bStats. An opt-out in `plugins/bStats/config.yml` no longer applies; use one of the switches above. `metrics.enabled: false` in `config.yml` still works as before.

</details>

# messages.yml

Change any text the plugin shows in game: chat messages, the text above the hotbar, the `/betterend` menu, the setup tour and the cost item picker.

The file is `plugins/BetterEndCities/messages.yml`. It's created on first start.

***

## Change a message

1. Open `messages.yml` and find the message. The note above each one says where it shows up.
2. Change the text between the double quotes.
3. Run `/betterend reload`.

```yaml
elytra:
  claimed: "<gold>Elytra claimed. Fly safe!"
```

{% hint style="info" %}
`command.description` is the one exception: it changes after a restart.
{% endhint %}

***

## Colours and styles

Messages use [MiniMessage](https://docs.advntr.dev/minimessage/format.html) tags such as `<red>`, `<gold>` and `<bold>`. Try your text at [webui.advntr.dev](https://webui.advntr.dev) before pasting it in.

In the `menu` and `setup` sections, titles show in dark aqua and other text in grey unless you give them a colour tag.

***

## Filled-in words

Words in curly brackets, like `{id}` or `{player}`, are filled in by the plugin. Keep them in your text. The note above each message lists the ones it can use.

`{prefix}` works in every message and shows the `prefix` at the top of the file. Out of the box only the discovery alert and the setup reminder use it. Add it to any other message to put the prefix there too:

```yaml
command:
  reload-done: "{prefix}<green>Settings and messages reloaded."
```

***

## Lists

Some messages are lists, one line per entry. `""` is an empty line.

```yaml
command:
  help:
    - "<dark_purple><bold>Better End Cities</bold></dark_purple> <gray>- admin commands"
    - "<white>/betterend</white> <gray>- open the settings menu"
```

***

## Updating the plugin

When you update, the plugin checks your `messages.yml` on start:

* **New messages** are added, with their notes.
* **Messages the plugin no longer uses** are removed.
* **Text you've changed is never touched.**

The server console lists what was added or removed.

To start over with every message at its default, delete `messages.yml` and restart.

{% hint style="warning" %}
**If the file has a mistake in it** (usually a TAB instead of spaces, or a missing quote), the plugin says so in the console and uses the built-in text until you fix it. Your file isn't changed.
{% endhint %}

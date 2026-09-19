# Storage

The plugin remembers which cities it has found, who has claimed an elytra where, and every player's loot copies. By default it keeps all this in a single file. You can point it at a MySQL database instead.

***

## The default

```yaml
database:
  type: sqlite
```

Nothing to set up. A `database.db` file appears in `plugins/BetterEndCities/` on first start, and that's it.

This is the right choice for almost every server, including large single-server survival ones. It handles this comfortably.

***

## MySQL

```yaml
database:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    database: betterend
    username: root
    password: ""
```

The console tells you which one started:

```
[BetterEndCities] Database pool initialized (MYSQL)
```

{% hint style="warning" %}
**A misspelled `type` quietly falls back to the default file**, with this warning:

```
[BetterEndCities] Invalid database.type, defaulting to SQLITE
```

If you set up MySQL and see `SQLITE` in the console, look for that line. A typo in `type` is almost always the cause, and your MySQL settings are being ignored completely.
{% endhint %}

### Setting it up

1. Create the database and a user:

   ```sql
   CREATE DATABASE betterend CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'betterend'@'%' IDENTIFIED BY 'a-real-password';
   GRANT ALL PRIVILEGES ON betterend.* TO 'betterend'@'%';
   FLUSH PRIVILEGES;
   ```

2. Fill in `config.yml`
3. Restart. Everything it needs is created for you

***

## Which should you use?

**Stay on the default unless you have a specific reason not to.** It's faster for a single server, needs no looking after, and backs up by copying one file.

Switch to MySQL when:

* **Several servers share one End.** This is the only really compelling reason. Loot copies and claim history then stay in step across your network
* **Your host requires it.** Some managed hosts don't give you somewhere permanent to keep files
* **You already back everything up through your database**

Don't switch just because MySQL sounds more serious. For one server it's more things that can go wrong, for no benefit.

***

## Switching between them

There's no built-in way to move your data across. Changing `database.type` starts from **empty**. Cities register themselves again as players travel, but **claim history and everyone's loot copies are lost**.

In practice:

* Players who had claimed an elytra can claim again
* Everyone's loot copies go back to fresh

{% hint style="warning" %}
**Plan the switch.** Do it during a quiet period, and tell players their End City loot will reset. If you use the `global` claim mode this hits harder, because everyone effectively gets a second elytra.
{% endhint %}

If you need a proper way to move the data across, say so on [Discord](https://discord.gg/qwYcTpHsNC).

***

## What's kept

| What | Notes |
|---|---|
| **Which cities are registered** | Their size, towers, world, and whether there's a ship. Comes back on its own if lost |
| **Elytra claims** | Which player claimed at which ship. Can't be recreated |
| **Per-player loot** | Each player's private copies, and the originals they're copied from |
| **Loot countdowns** | Where each city is in its cycle |

**Saved copies of cities are not in here.** They're separate files in `plugins/BetterEndCities/snapshots/`, so your database stays small no matter how many cities you have.

***

## Backups

Back up the **whole `plugins/BetterEndCities/` folder**, not just the database. The saved copies of your cities live alongside it, and they can't be recreated once a city's blocks have changed.

If you use MySQL, that means your usual database backup **plus** the `snapshots/` folder.

{% hint style="info" %}
**The list of cities is the cheap part.** If you lose it, cities register themselves again. Claim history and saved copies are the parts worth protecting.
{% endhint %}

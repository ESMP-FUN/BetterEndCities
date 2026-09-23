# Storage

Choose where the plugin keeps its cities, elytra claims and loot copies: a file on the server (default) or a MySQL database.

Stay on the default unless several servers share one End, or your host has no permanent file storage.

***

## The default: SQLite

```yaml
database:
  type: sqlite
```

Nothing to set up. `plugins/BetterEndCities/database.db` is created on first start.

***

## Switch to MySQL

1. Create a database and a user:

   ```sql
   CREATE DATABASE betterend CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'betterend'@'%' IDENTIFIED BY 'a-real-password';
   GRANT ALL PRIVILEGES ON betterend.* TO 'betterend'@'%';
   FLUSH PRIVILEGES;
   ```

2. Fill in the details, on the **Storage** page of `/betterend` or in `config.yml`:

   ```yaml
   database:
     type: mysql
     mysql:
       host: localhost
       port: 3306
       database: betterend
       username: betterend
       password: "a-real-password"
   ```

3. Restart. The tables are created for you, and the console shows:

   ```
   [BetterEndCities] Database pool initialized (MYSQL)
   ```

{% hint style="warning" %}
**Seeing `SQLITE` instead?** `type` is misspelled, and your MySQL settings are ignored. The console warns: `Invalid database.type, defaulting to SQLITE`.
{% endhint %}

***

## Switching loses history

Nothing is copied between SQLite and MySQL. After switching:

* Cities register again as players travel
* Players who claimed an elytra can claim again
* Everyone's loot copies start fresh

{% hint style="warning" %}
Switch during a quiet period and tell players. With `claim-mode: global`, everyone effectively gets a second elytra.
{% endhint %}

***

## What's stored

| What | Can it come back if lost? |
|---|---|
| **Registered cities** | Yes, they register again |
| **Elytra claims** | No |
| **Loot copies and loot templates** | No |
| **Loot countdowns** | They restart |

Saved copies of cities aren't in the database; they're files in `plugins/BetterEndCities/snapshots/`.

***

## Backups

Back up the whole `plugins/BetterEndCities/` folder. On MySQL, that's your database backup plus the `snapshots/` folder. Saved copies can't be recreated once a city has changed.

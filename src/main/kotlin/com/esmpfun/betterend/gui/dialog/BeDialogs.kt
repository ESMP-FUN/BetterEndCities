package com.esmpfun.betterend.gui.dialog

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.gui.CurrencyPickerView
import com.esmpfun.betterend.listeners.ElytraFrameListener
import com.esmpfun.betterend.managers.ElytraClaimManager
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.dialog.DialogResponseView
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

/** The `/betterend` settings dialogs. Saves write config.yml, which every feature reads live. */
@Suppress("UnstableApiUsage")
object BeDialogs {

    // Input keys reject dots and dashes, so config paths are sanitised here and mapped back on save.
    private fun String.dialogKey(): String = replace(Regex("[^A-Za-z0-9_]"), "_")

    fun toggle(key: String, label: String, initial: Boolean): DialogInput =
        DialogInput.bool(key.dialogKey(), Component.text(label)).initial(initial).build()

    // The client shows min + n*step in doubles, so only steps like 0.25, 0.5 or 1 display cleanly.
    fun slider(key: String, label: String, min: Float, max: Float, step: Float, initial: Float): DialogInput {
        val snapped = (min + Math.round((initial.coerceIn(min, max) - min) / step) * step).coerceIn(min, max)
        return DialogInput.numberRange(key.dialogKey(), Component.text(label), min, max)
            .step(step)
            .initial(snapped)
            .labelFormat("%s: %s")
            .width(220)
            .build()
    }

    fun text(key: String, label: String, initial: String, maxLength: Int = 64): DialogInput =
        DialogInput.text(key.dialogKey(), Component.text(label))
            .initial(initial)
            .maxLength(maxLength)
            .width(300)
            .build()

    /** A pick-one input; the chosen [Choice.id] comes back via `getText(key)`. */
    data class Choice(val id: String, val label: String)

    fun singleOption(key: String, label: String, choices: List<Choice>, selectedId: String): DialogInput =
        DialogInput.singleOption(
            key.dialogKey(),
            Component.text(label),
            choices.map { SingleOptionDialogInput.OptionEntry.create(it.id, Component.text(it.label), it.id == selectedId) },
        ).width(220).build()

    /** A settings page with Back, Save, Save & Close and Close; [onSave] runs for both saves. */
    fun showSettings(
        plugin: BetterEnd,
        player: Player,
        title: String,
        body: List<String>,
        inputs: List<DialogInput>,
        extraButtons: List<ActionButton> = emptyList(),
        needsRestart: Boolean = false,
        onSave: (DialogResponseView) -> Unit,
    ) {
        fun mapped(view: DialogResponseView): DialogResponseView = object : DialogResponseView {
            override fun payload() = view.payload()
            override fun getText(key: String) = view.getText(key.dialogKey())
            override fun getBoolean(key: String) = view.getBoolean(key.dialogKey())
            override fun getFloat(key: String) = view.getFloat(key.dialogKey())
        }

        fun saveAndApply(view: DialogResponseView) {
            onSave(mapped(view))
            plugin.scheduler.runTask(Runnable {
                plugin.saveConfig()
                ElytraFrameListener.refreshLoaded(plugin)
            })
            player.sendMessage(
                if (needsRestart) Component.text("Settings saved. They take effect the next time the server starts.", NamedTextColor.GREEN)
                else Component.text("Settings saved & applied.", NamedTextColor.GREEN)
            )
        }

        val back = button("Back", NamedTextColor.YELLOW, "Return to the Better End Cities menu (without saving)") { _ ->
            plugin.scheduler.runAtEntity(player, Runnable {
                if (player.isOnline) openMainMenu(plugin, player)
            })
        }
        val save = button("Save", NamedTextColor.GREEN, "Save, apply and return to the menu") { view ->
            saveAndApply(view)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (player.isOnline) openMainMenu(plugin, player)
            })
        }
        val saveClose = button("Save & Close", NamedTextColor.DARK_GREEN, "Save, apply, and close") { view ->
            saveAndApply(view)
            player.closeDialog()
        }
        val close = closeButton(player, "Close", "Close without saving")

        val base = DialogBase.builder(Component.text(title, NamedTextColor.DARK_AQUA))
            .body(body.map { DialogBody.plainMessage(Component.text(it, NamedTextColor.GRAY)) })
            .inputs(inputs)
            // NONE keeps the screen up so pages swap without closing, which means
            // an exit button must close itself (see closeButton). A pausing dialog
            // is rejected with NONE. Escape stays on so a throwing callback can't
            // strand the player behind spent single-use buttons.
            .pause(false)
            .canCloseWithEscape(true)
            .afterAction(DialogBase.DialogAfterAction.NONE)
            .build()

        val dialog = Dialog.create { factory ->
            factory.empty().base(base)
                .type(DialogType.multiAction(extraButtons + listOf(back, save, saveClose), close, 3))
        }
        player.showDialog(dialog)
    }

    fun button(
        label: String,
        color: NamedTextColor,
        tooltip: String,
        onClick: ((DialogResponseView) -> Unit)?,
    ): ActionButton {
        val b = ActionButton.builder(Component.text(label, color))
            .tooltip(Component.text(tooltip))
            .width(120)
        if (onClick != null) {
            b.action(
                DialogAction.customClick(
                    DialogActionCallback { view, _ -> onClick(view) },
                    // Single use: every button navigates or closes, and it stops a double-click saving twice.
                    ClickCallback.Options.builder().build(),
                ),
            )
        }
        return b.build()
    }

    /** With after-action NONE a button without an action does nothing, so exits close explicitly. */
    fun closeButton(player: Player, label: String, tooltip: String): ActionButton =
        button(label, NamedTextColor.RED, tooltip) { player.closeDialog() }

    // ── the /betterend menu ──────────────────────────────────────────────────

    fun openMainMenu(plugin: BetterEnd, player: Player) {
        val cities = plugin.cityManager.all().size
        val mode = plugin.elytraClaimManager.mode().label
        val cost = plugin.elytraClaimManager.costStack()

        fun page(label: String, color: NamedTextColor, tooltip: String, open: (BetterEnd, Player) -> Unit) =
            button(label, color, tooltip) { _ ->
                plugin.scheduler.runAtEntity(player, Runnable { if (player.isOnline) open(plugin, player) })
            }

        val elytra = page("Elytra Frames", NamedTextColor.AQUA, "Who can claim, what it costs, and the frame's hint", ::openElytra)
        val loot = page("Per-player Loot", NamedTextColor.LIGHT_PURPLE, "Everyone's own chest copies and how often they refresh", ::openLoot)
        val protection = page("Protection", NamedTextColor.RED, "What players can and can't break or build in a city", ::openProtection)
        val discovery = page("Finding Cities & Resets", NamedTextColor.DARK_PURPLE, "Finding cities, saved copies and putting blocks back", ::openDiscovery)
        val storage = page("Storage", NamedTextColor.GRAY, "Where the plugin keeps its data (SQLite or MySQL)", ::openStorage)
        val updates = page("Updates & Stats", NamedTextColor.GRAY, "Update checks, anonymous stats and extra logging", ::openUpdates)
        val costItem = button("Choose Cost Item", NamedTextColor.GOLD, "Pick which item an elytra claim costs, straight from your inventory") { _ ->
            player.closeDialog()
            plugin.scheduler.runAtEntity(player, Runnable {
                if (player.isOnline) CurrencyPickerView(plugin).open(player)
            })
        }
        val setup = button("Setup Tour", NamedTextColor.GREEN, "Walk through every setting, one plain question at a time") { _ ->
            plugin.scheduler.runAtEntity(player, Runnable {
                if (player.isOnline) com.esmpfun.betterend.setup.SetupTour.start(plugin, player)
            })
        }
        val close = closeButton(player, "Close", "Close the menu")

        val costLine = if (cost == null) "Claims are currently free."
        else "A claim currently costs ${cost.amount} x ${cost.type.name.lowercase().replace('_', ' ')}."

        val base = DialogBase.builder(Component.text("Better End Cities", NamedTextColor.DARK_AQUA))
            .body(
                listOf(
                    "Renewable End Cities: every player earns their own elytra",
                    "and their own loot, and the structures reset themselves.",
                    "",
                    "$cities End ${if (cities == 1) "City" else "Cities"} registered • Elytra: $mode",
                    costLine,
                ).map { DialogBody.plainMessage(Component.text(it, NamedTextColor.GRAY)) }
            )
            .pause(false)
            .canCloseWithEscape(true)
            .afterAction(DialogBase.DialogAfterAction.NONE)
            .build()

        val dialog = Dialog.create { factory ->
            factory.empty().base(base)
                .type(DialogType.multiAction(listOf(elytra, costItem, loot, protection, discovery, storage, updates, setup), close, 2))
        }
        player.showDialog(dialog)
    }

    // ── feature dialogs ──────────────────────────────────────────────────────

    // The amount slider tops out at the cost item's stack size. The item itself is picked in CurrencyPickerView.
    fun openElytra(plugin: BetterEnd, player: Player) {
        val cfg = plugin.config
        val item = plugin.elytraClaimManager.costItemOrDefault()
        val itemName = item.type.name.lowercase().replace('_', ' ')
        val maxStack = item.maxStackSize
        val currentAmount = cfg.getInt("elytra.cost.amount", 0).coerceIn(0, maxStack)
        val currentMode = ElytraClaimManager.ClaimMode.fromConfig(cfg.getString("elytra.claim-mode"))

        val pickItem = button("Choose Cost Item", NamedTextColor.GOLD, "Pick the cost item from your inventory (unsaved edits here are discarded)") { _ ->
            player.closeDialog()
            plugin.scheduler.runAtEntity(player, Runnable {
                if (player.isOnline) CurrencyPickerView(plugin).open(player)
            })
        }

        showSettings(
            plugin = plugin,
            player = player,
            title = "Elytra Frames",
            body = listOf(
                "The ship's elytra item frame becomes renewable: punching it",
                "puts an elytra in your inventory and the frame stays for the",
                "next player. Vanilla feel, nothing to relearn.",
                "",
                "Cost: 0 = free. Otherwise a claim consumes that many of the",
                "chosen item (currently: $itemName, stacks to $maxStack).",
                "XP levels are taken on top of the item. With doubling on,",
                "each elytra a player buys costs twice the one before, until",
                "a loot refresh window has passed since that purchase.",
            ),
            inputs = listOf(
                toggle("elytra.enabled", "Feature enabled", cfg.getBoolean("elytra.enabled", true)),
                singleOption(
                    "elytra.claim-mode", "Who can claim, how often",
                    ElytraClaimManager.ClaimMode.entries.map { Choice(it.key, it.label) },
                    currentMode.key,
                ),
                slider("elytra.cost.amount", "Cost ($itemName, 0 = free)", 0f, maxStack.toFloat(), 1f, currentAmount.toFloat()),
                levelsSlider(cfg.getInt("elytra.cost.levels", 0)),
                toggle("elytra.cost.double-each-claim", "Price doubles with each elytra bought", cfg.getBoolean("elytra.cost.double-each-claim", false)),
                toggle("elytra.text-display", "Floating hint above the frame", cfg.getBoolean("elytra.text-display", true)),
                toggle("elytra.frame-aura", "Shimmer around the frame when a player is near", cfg.getBoolean("elytra.frame-aura", false)),
            ),
            extraButtons = listOf(pickItem),
        ) { view ->
            view.getBoolean("elytra.enabled")?.let { cfg.set("elytra.enabled", it) }
            view.getText("elytra.claim-mode")?.let { cfg.set("elytra.claim-mode", it) }
            view.getFloat("elytra.cost.amount")?.let { cfg.set("elytra.cost.amount", it.toInt()) }
            view.getFloat("elytra.cost.levels")?.let { cfg.set("elytra.cost.levels", it.toInt()) }
            view.getBoolean("elytra.cost.double-each-claim")?.let { cfg.set("elytra.cost.double-each-claim", it) }
            view.getBoolean("elytra.text-display")?.let { cfg.set("elytra.text-display", it) }
            view.getBoolean("elytra.frame-aura")?.let { cfg.set("elytra.frame-aura", it) }
        }
    }

    // The slider tops out at 100 levels, or higher when config.yml already
    // holds more, so opening and saving never lowers a hand-set price.
    fun levelsSlider(current: Int): DialogInput =
        slider("elytra.cost.levels", "XP levels per claim (0 = none)", 0f, maxOf(100, current).toFloat(), 1f, current.toFloat())

    fun refreshSlider(label: String, current: Int): DialogInput =
        slider("loot.refresh-hours", label, 0f, maxOf(168, current).toFloat(), 1f, current.toFloat())

    val SCOPES = listOf(
        Choice("whole-city", "The whole city"),
        Choice("ship-only", "Only the ship and the city's chests"),
    )

    fun scopeKey(raw: String?): String = SCOPES.firstOrNull { it.id.equals(raw, ignoreCase = true) }?.id ?: "whole-city"

    /** Per-player chest loot and its refresh window. */
    fun openLoot(plugin: BetterEnd, player: Player) {
        val cfg = plugin.config
        showSettings(
            plugin = plugin,
            player = player,
            title = "Per-player Loot",
            body = listOf(
                "Every player who opens a city chest gets their own private",
                "copy of what's inside, so the first player through no longer",
                "empties the city for everyone else. Chests players place",
                "themselves stay normal and shared.",
                "",
                "Refresh window: how many hours before a city's loot comes",
                "back. Each city counts down on its own, starting when someone",
                "first loots it. 0 = never, so each player loots each chest once.",
            ),
            inputs = listOf(
                toggle("loot.enabled", "Per-player chest loot", cfg.getBoolean("loot.enabled", true)),
                refreshSlider("Refresh window (hours, 0 = never)", cfg.getInt("loot.refresh-hours", 12)),
            ),
        ) { view ->
            view.getBoolean("loot.enabled")?.let { cfg.set("loot.enabled", it) }
            view.getFloat("loot.refresh-hours")?.let { cfg.set("loot.refresh-hours", it.toInt()) }
        }
    }

    /** Grief protection. */
    fun openProtection(plugin: BetterEnd, player: Player) {
        val cfg = plugin.config
        val padding = cfg.getInt("protection.piece-padding", 3)
        showSettings(
            plugin = plugin,
            player = player,
            title = "Protection",
            body = listOf(
                "Stops players griefing cities. Every block in a tower, a",
                "bridge or the ship is protected, whatever the block is. The",
                "empty space between towers stays free to build in.",
                "",
                "'Only the ship' leaves towers and bridges open, but still",
                "guards the ship and the city's loot chests.",
                "",
                "Reach past each tower: how many blocks of protection extend",
                "beyond a tower's walls, to cover its trim. 3 fits vanilla",
                "cities. Higher starts protecting empty space nearby.",
                "",
                "Staff with the betterend.bypass.protection permission are",
                "never stopped.",
            ),
            inputs = listOf(
                toggle("protection.enabled", "Grief protection", cfg.getBoolean("protection.enabled", true)),
                singleOption("protection.scope", "What is protected", SCOPES, scopeKey(cfg.getString("protection.scope"))),
                toggle("protection.dragon-head-takeable", "Players may take the ship's dragon head (once, for good)", cfg.getBoolean("protection.dragon-head-takeable", false)),
                slider("protection.piece-padding", "Reach past each tower (blocks)", 0f, maxOf(16, padding).toFloat(), 1f, padding.toFloat()),
                toggle("protection.block-place", "Also stop players building inside", cfg.getBoolean("protection.block-place", true)),
                toggle("protection.block-explosions", "Also protect from creepers, TNT and other explosions", cfg.getBoolean("protection.block-explosions", true)),
                toggle("protection.notify-denied", "Tell players why their break or build was stopped", cfg.getBoolean("protection.notify-denied", true)),
            ),
        ) { view ->
            listOf(
                "protection.enabled", "protection.dragon-head-takeable", "protection.block-place",
                "protection.block-explosions", "protection.notify-denied",
            ).forEach { key -> view.getBoolean(key)?.let { cfg.set(key, it) } }
            view.getText("protection.scope")?.let { cfg.set("protection.scope", it) }
            view.getFloat("protection.piece-padding")?.let { cfg.set("protection.piece-padding", it.toInt()) }
        }
    }

    /** City discovery and snapshots. */
    fun openDiscovery(plugin: BetterEnd, player: Player) {
        val cfg = plugin.config
        val excluded = cfg.getStringList("discovery.excluded-worlds").joinToString(", ")
        // Shown in millions: a whole-block count doesn't fit a slider.
        val maxCells = cfg.getInt("snapshot.max-cells", 3_000_000)
        val maxMillions = maxCells / 1_000_000f
        val cellsSlider = slider(
            "snapshot.max-cells", "Largest saved copy (millions of blocks)",
            0.5f, maxOf(10f, maxMillions), 0.25f, maxMillions,
        )
        showSettings(
            plugin = plugin,
            player = player,
            title = "Finding Cities & Resets",
            body = listOf(
                "Cities register themselves as players travel near them, and",
                "start working straight away.",
                "",
                "Worlds to leave alone: world names, separated by commas, whose",
                "cities should behave exactly like vanilla. Cities already",
                "registered there keep working until you /betterend delete them.",
                "",
                "A saved copy of each city lets /betterend reset put its blocks",
                "back. Putting blocks back on every loot refresh can suffocate",
                "players inside and erases anything built there, so it's off",
                "by default. The size limit is far above any real city.",
            ),
            inputs = listOf(
                toggle("discovery.enabled", "Register new End Cities automatically", cfg.getBoolean("discovery.enabled", true)),
                toggle("discovery.startup-sweep", "Also check areas already loaded at startup", cfg.getBoolean("discovery.startup-sweep", true)),
                text("discovery.excluded-worlds", "Worlds to leave alone (comma separated)", excluded, 512),
                toggle("snapshot.auto-capture", "Save a copy of each city when it's found", cfg.getBoolean("snapshot.auto-capture", true)),
                toggle("snapshot.auto-reset-on-refresh", "Put blocks back on every loot refresh", cfg.getBoolean("snapshot.auto-reset-on-refresh", false)),
                cellsSlider,
            ),
        ) { view ->
            listOf(
                "discovery.enabled", "discovery.startup-sweep",
                "snapshot.auto-capture", "snapshot.auto-reset-on-refresh",
            ).forEach { key -> view.getBoolean(key)?.let { cfg.set(key, it) } }
            view.getText("discovery.excluded-worlds")?.let { raw ->
                cfg.set("discovery.excluded-worlds", raw.split(',').map { it.trim() }.filter { it.isNotEmpty() })
            }
            // Only written when moved, so a hand-set value between slider steps survives an unrelated save.
            view.getFloat("snapshot.max-cells")?.let { v ->
                val snapped = (0.5f + Math.round((maxMillions.coerceIn(0.5f, maxOf(10f, maxMillions)) - 0.5f) / 0.25f) * 0.25f)
                if (v != snapped) cfg.set("snapshot.max-cells", Math.round(v * 1_000_000.0).toInt())
            }
        }
    }

    /** Database backend. Read once at startup. */
    fun openStorage(plugin: BetterEnd, player: Player) {
        val cfg = plugin.config
        val types = listOf(
            Choice("sqlite", "SQLite: a file on this server, no setup"),
            Choice("mysql", "MySQL: a database server you run"),
        )
        val current = if (cfg.getString("database.type", "sqlite").equals("mysql", ignoreCase = true)) "mysql" else "sqlite"
        showSettings(
            plugin = plugin,
            player = player,
            title = "Storage",
            body = listOf(
                "Where the plugin remembers your cities, who has claimed an",
                "elytra, and everyone's loot. SQLite needs nothing and is right",
                "for almost every server. Use MySQL only if several servers",
                "need to share one End.",
                "",
                "The MySQL fields are ignored while SQLite is chosen. Leave the",
                "password empty to keep the one already saved.",
                "",
                "Switching starts from an empty database: nothing is copied",
                "across. Changes here take effect after a restart.",
            ),
            inputs = listOf(
                singleOption("database.type", "Storage", types, current),
                text("database.mysql.host", "MySQL address", cfg.getString("database.mysql.host", "localhost") ?: "localhost", 255),
                text("database.mysql.port", "MySQL port (usually 3306)", cfg.getInt("database.mysql.port", 3306).toString(), 5),
                text("database.mysql.database", "MySQL database name", cfg.getString("database.mysql.database", "betterend") ?: "betterend", 64),
                text("database.mysql.username", "MySQL username", cfg.getString("database.mysql.username", "root") ?: "root", 64),
                text("database.mysql.password", "MySQL password (empty = keep current)", "", 128),
            ),
            needsRestart = true,
        ) { view ->
            view.getText("database.type")?.let { cfg.set("database.type", it) }
            for (key in listOf("database.mysql.host", "database.mysql.database", "database.mysql.username")) {
                view.getText(key)?.trim()?.takeIf { it.isNotEmpty() }?.let { cfg.set(key, it) }
            }
            view.getText("database.mysql.port")?.trim()?.let { raw ->
                val port = raw.toIntOrNull()
                if (port != null && port in 1..65535) cfg.set("database.mysql.port", port)
                else player.sendMessage(Component.text("'$raw' isn't a port number (1 to 65535), so the port was left unchanged.", NamedTextColor.YELLOW))
            }
            view.getText("database.mysql.password")?.takeIf { it.isNotEmpty() }?.let { cfg.set("database.mysql.password", it) }
        }
    }

    /** Update checks (read once at startup), metrics and logging. */
    fun openUpdates(plugin: BetterEnd, player: Player) {
        val cfg = plugin.config
        val modes = listOf(
            Choice("off", "Off: never check"),
            Choice("check-only", "Check quietly (see /betterend update)"),
            Choice("notify", "Tell staff, who can download it"),
            Choice("auto-stage", "Download it for the next restart by itself"),
        )
        // "download" behaves exactly like "notify", so it isn't offered and shows as notify.
        val mode = cfg.getString("update.mode", "notify")?.lowercase().let { m -> modes.firstOrNull { it.id == m }?.id } ?: "notify"
        val interval = cfg.getInt("update.check-interval-hours", 6)
        val holdHours = cfg.getInt("update.hold-new-updates-hours", 18)
        showSettings(
            plugin = plugin,
            player = player,
            title = "Updates & Stats",
            body = listOf(
                "Updates: how the plugin handles a new version. Waiting on a",
                "brand-new release skips one that turns out broken and gets",
                "fixed within hours.",
                "",
                "Anonymous stats count which settings are in use. Error reports",
                "send this plugin's own errors with IP addresses, file paths,",
                "passwords and player ids removed. Neither includes anything",
                "about your players or your world.",
                "",
                "Changes on this page take effect after a restart, except extra",
                "logging, which applies at once.",
            ),
            inputs = listOf(
                singleOption("update.mode", "When a new version comes out", modes, mode),
                slider("update.check-interval-hours", "Check every (hours)", 1f, maxOf(48, interval).toFloat(), 1f, interval.toFloat()),
                toggle("update.hold-new-updates", "Wait before taking a brand-new release", cfg.getBoolean("update.hold-new-updates", false)),
                slider("update.hold-new-updates-hours", "How long to wait (hours)", 1f, maxOf(72, holdHours).toFloat(), 1f, holdHours.toFloat()),
                toggle("metrics.enabled", "Send anonymous usage stats", cfg.getBoolean("metrics.enabled", true)),
                toggle("metrics.error-reporting", "Send automatic error reports", cfg.getBoolean("metrics.error-reporting", true)),
                toggle("debug.verbose-logging", "Extra logging (noisy, for bug reports)", cfg.getBoolean("debug.verbose-logging", false)),
            ),
            needsRestart = true,
        ) { view ->
            view.getText("update.mode")?.let { cfg.set("update.mode", it) }
            view.getFloat("update.check-interval-hours")?.let { cfg.set("update.check-interval-hours", it.toInt()) }
            view.getFloat("update.hold-new-updates-hours")?.let { cfg.set("update.hold-new-updates-hours", it.toInt()) }
            listOf(
                "update.hold-new-updates", "metrics.enabled", "metrics.error-reporting", "debug.verbose-logging",
            ).forEach { key -> view.getBoolean(key)?.let { cfg.set(key, it) } }
        }
    }
}

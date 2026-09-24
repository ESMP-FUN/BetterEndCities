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

    fun toggle(key: String, label: Component, initial: Boolean): DialogInput =
        DialogInput.bool(key.dialogKey(), label).initial(initial).build()

    // The client shows min + n*step in doubles, so only steps like 0.25, 0.5 or 1 display cleanly.
    fun slider(key: String, label: Component, min: Float, max: Float, step: Float, initial: Float): DialogInput {
        val snapped = (min + Math.round((initial.coerceIn(min, max) - min) / step) * step).coerceIn(min, max)
        return DialogInput.numberRange(key.dialogKey(), label, min, max)
            .step(step)
            .initial(snapped)
            .labelFormat("%s: %s")
            .width(220)
            .build()
    }

    fun text(key: String, label: Component, initial: String, maxLength: Int = 64): DialogInput =
        DialogInput.text(key.dialogKey(), label)
            .initial(initial)
            .maxLength(maxLength)
            .width(300)
            .build()

    /** A pick-one input; the chosen [Choice.id] comes back via `getText(key)`. */
    data class Choice(val id: String, val label: Component)

    fun singleOption(key: String, label: Component, choices: List<Choice>, selectedId: String): DialogInput =
        DialogInput.singleOption(
            key.dialogKey(),
            label,
            choices.map { SingleOptionDialogInput.OptionEntry.create(it.id, it.label, it.id == selectedId) },
        ).width(220).build()

    /** The "Who can claim, how often" choices. */
    fun claimModes(plugin: BetterEnd): List<Choice> =
        ElytraClaimManager.ClaimMode.entries.map { Choice(it.key, plugin.messages.get("claim-modes.${it.key}")) }

    /** A dialog title, dark aqua unless the message sets a colour. */
    fun title(plugin: BetterEnd, key: String, vararg values: Pair<String, Any?>): Component =
        plugin.messages.get(key, *values).colorIfAbsent(NamedTextColor.DARK_AQUA)

    /** Dialog body lines, grey unless the message sets a colour. */
    fun body(plugin: BetterEnd, key: String, vararg values: Pair<String, Any?>): List<DialogBody> =
        plugin.messages.lines(key, *values).map { DialogBody.plainMessage(it.colorIfAbsent(NamedTextColor.GRAY)) }

    /** A settings page with Back, Save, Save & Close and Close; [onSave] runs for both saves. */
    fun showSettings(
        plugin: BetterEnd,
        player: Player,
        page: String,
        body: List<DialogBody>,
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
            player.sendMessage(plugin.messages.get(if (needsRestart) "menu.saved-restart" else "menu.saved"))
        }

        val back = button(plugin, "menu.buttons.back", NamedTextColor.YELLOW) { _ ->
            plugin.scheduler.runAtEntity(player, Runnable {
                if (player.isOnline) openMainMenu(plugin, player)
            })
        }
        val save = button(plugin, "menu.buttons.save", NamedTextColor.GREEN) { view ->
            saveAndApply(view)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (player.isOnline) openMainMenu(plugin, player)
            })
        }
        val saveClose = button(plugin, "menu.buttons.save-close", NamedTextColor.DARK_GREEN) { view ->
            saveAndApply(view)
            player.closeDialog()
        }
        val close = closeButton(plugin, player, "menu.buttons.close")

        val base = DialogBase.builder(title(plugin, "$page.title"))
            .body(body)
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
        label: Component,
        color: NamedTextColor,
        tooltip: Component,
        onClick: ((DialogResponseView) -> Unit)?,
    ): ActionButton {
        val b = ActionButton.builder(label.colorIfAbsent(color))
            .tooltip(tooltip)
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

    /** A button whose text comes from the `label` and `tooltip` messages under [key]. */
    fun button(
        plugin: BetterEnd,
        key: String,
        color: NamedTextColor,
        vararg values: Pair<String, Any?>,
        onClick: ((DialogResponseView) -> Unit)?,
    ): ActionButton =
        button(plugin.messages.get("$key.label", *values), color, plugin.messages.get("$key.tooltip", *values), onClick)

    /** With after-action NONE a button without an action does nothing, so exits close explicitly. */
    fun closeButton(plugin: BetterEnd, player: Player, key: String): ActionButton =
        button(plugin, key, NamedTextColor.RED) { player.closeDialog() }

    // ── the /betterend menu ──────────────────────────────────────────────────

    fun openMainMenu(plugin: BetterEnd, player: Player) {
        val m = plugin.messages
        val cities = plugin.cityManager.all().size
        val cost = plugin.elytraClaimManager.costStack()

        fun page(key: String, color: NamedTextColor, open: (BetterEnd, Player) -> Unit) =
            button(plugin, "menu.main.buttons.$key", color) { _ ->
                plugin.scheduler.runAtEntity(player, Runnable { if (player.isOnline) open(plugin, player) })
            }

        val elytra = page("elytra", NamedTextColor.AQUA, ::openElytra)
        val loot = page("loot", NamedTextColor.LIGHT_PURPLE, ::openLoot)
        val protection = page("protection", NamedTextColor.RED, ::openProtection)
        val discovery = page("discovery", NamedTextColor.DARK_PURPLE, ::openDiscovery)
        val storage = page("storage", NamedTextColor.GRAY, ::openStorage)
        val updates = page("updates", NamedTextColor.GRAY, ::openUpdates)
        val costItem = button(plugin, "menu.buttons.cost-item", NamedTextColor.GOLD) { _ ->
            player.closeDialog()
            plugin.scheduler.runAtEntity(player, Runnable {
                if (player.isOnline) CurrencyPickerView(plugin).open(player)
            })
        }
        val setup = page("setup", NamedTextColor.GREEN) { p, pl -> com.esmpfun.betterend.setup.SetupTour.start(p, pl) }
        val close = closeButton(plugin, player, "menu.main.buttons.close")

        val cityCount = if (cities == 1) m.get("menu.main.cities-one") else m.get("menu.main.cities", "count" to cities)
        val costLine = if (cost == null) m.get("menu.main.cost-free")
        else m.get("menu.main.cost", "amount" to cost.amount, "item" to itemName(cost))

        val base = DialogBase.builder(title(plugin, "menu.main.title"))
            .body(body(plugin, "menu.main.body", "cities" to cityCount, "mode" to m.get("claim-modes.${plugin.elytraClaimManager.mode().key}"), "cost" to costLine))
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

    /** "shulker shell" for the cost item's type. */
    fun itemName(item: org.bukkit.inventory.ItemStack): String = item.type.name.lowercase().replace('_', ' ')

    // ── feature dialogs ──────────────────────────────────────────────────────

    // The amount slider tops out at the cost item's stack size. The item itself is picked in CurrencyPickerView.
    fun openElytra(plugin: BetterEnd, player: Player) {
        val m = plugin.messages
        val cfg = plugin.config
        val item = plugin.elytraClaimManager.costItemOrDefault()
        val itemName = itemName(item)
        val maxStack = item.maxStackSize
        val currentAmount = cfg.getInt("elytra.cost.amount", 0).coerceIn(0, maxStack)
        val currentMode = ElytraClaimManager.ClaimMode.fromConfig(cfg.getString("elytra.claim-mode"))

        val pickItem = button(
            m.get("menu.buttons.cost-item.label"), NamedTextColor.GOLD, m.get("menu.elytra.cost-item-tooltip"),
        ) { _ ->
            player.closeDialog()
            plugin.scheduler.runAtEntity(player, Runnable {
                if (player.isOnline) CurrencyPickerView(plugin).open(player)
            })
        }

        val label = { key: String -> m.get("menu.elytra.inputs.$key") }
        showSettings(
            plugin = plugin,
            player = player,
            page = "menu.elytra",
            body = body(plugin, "menu.elytra.body", "item" to itemName, "stack" to maxStack),
            inputs = listOf(
                toggle("elytra.enabled", label("enabled"), cfg.getBoolean("elytra.enabled", true)),
                singleOption("elytra.claim-mode", label("claim-mode"), claimModes(plugin), currentMode.key),
                slider("elytra.cost.amount", m.get("menu.elytra.inputs.cost-amount", "item" to itemName), 0f, maxStack.toFloat(), 1f, currentAmount.toFloat()),
                levelsSlider(label("cost-levels"), cfg.getInt("elytra.cost.levels", 0)),
                toggle("elytra.cost.double-each-claim", label("double-each-claim"), cfg.getBoolean("elytra.cost.double-each-claim", false)),
                toggle("elytra.text-display", label("text-display"), cfg.getBoolean("elytra.text-display", true)),
                toggle("elytra.frame-aura", label("frame-aura"), cfg.getBoolean("elytra.frame-aura", false)),
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
    fun levelsSlider(label: Component, current: Int): DialogInput =
        slider("elytra.cost.levels", label, 0f, maxOf(100, current).toFloat(), 1f, current.toFloat())

    fun refreshSlider(label: Component, current: Int): DialogInput =
        slider("loot.refresh-hours", label, 0f, maxOf(168, current).toFloat(), 1f, current.toFloat())

    private val SCOPE_IDS = listOf("whole-city", "ship-only")

    fun scopes(plugin: BetterEnd): List<Choice> =
        SCOPE_IDS.map { Choice(it, plugin.messages.get("menu.protection.scopes.$it")) }

    fun scopeKey(raw: String?): String = SCOPE_IDS.firstOrNull { it.equals(raw, ignoreCase = true) } ?: "whole-city"

    /** Per-player chest loot and its refresh window. */
    fun openLoot(plugin: BetterEnd, player: Player) {
        val m = plugin.messages
        val cfg = plugin.config
        showSettings(
            plugin = plugin,
            player = player,
            page = "menu.loot",
            body = body(plugin, "menu.loot.body"),
            inputs = listOf(
                toggle("loot.enabled", m.get("menu.loot.inputs.enabled"), cfg.getBoolean("loot.enabled", true)),
                refreshSlider(m.get("menu.loot.inputs.refresh-hours"), cfg.getInt("loot.refresh-hours", 12)),
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
        val label = { key: String -> plugin.messages.get("menu.protection.inputs.$key") }
        showSettings(
            plugin = plugin,
            player = player,
            page = "menu.protection",
            body = body(plugin, "menu.protection.body"),
            inputs = listOf(
                toggle("protection.enabled", label("enabled"), cfg.getBoolean("protection.enabled", true)),
                singleOption("protection.scope", label("scope"), scopes(plugin), scopeKey(cfg.getString("protection.scope"))),
                toggle("protection.dragon-head-takeable", label("dragon-head-takeable"), cfg.getBoolean("protection.dragon-head-takeable", false)),
                slider("protection.piece-padding", label("piece-padding"), 0f, maxOf(16, padding).toFloat(), 1f, padding.toFloat()),
                toggle("protection.block-place", label("block-place"), cfg.getBoolean("protection.block-place", true)),
                toggle("protection.block-explosions", label("block-explosions"), cfg.getBoolean("protection.block-explosions", true)),
                toggle("protection.notify-denied", label("notify-denied"), cfg.getBoolean("protection.notify-denied", true)),
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
        val label = { key: String -> plugin.messages.get("menu.discovery.inputs.$key") }
        val excluded = cfg.getStringList("discovery.excluded-worlds").joinToString(", ")
        // Shown in millions: a whole-block count doesn't fit a slider.
        val maxCells = cfg.getInt("snapshot.max-cells", 3_000_000)
        val maxMillions = maxCells / 1_000_000f
        val cellsSlider = slider(
            "snapshot.max-cells", label("max-cells"),
            0.5f, maxOf(10f, maxMillions), 0.25f, maxMillions,
        )
        showSettings(
            plugin = plugin,
            player = player,
            page = "menu.discovery",
            body = body(plugin, "menu.discovery.body"),
            inputs = listOf(
                toggle("discovery.enabled", label("enabled"), cfg.getBoolean("discovery.enabled", true)),
                toggle("discovery.startup-sweep", label("startup-sweep"), cfg.getBoolean("discovery.startup-sweep", true)),
                text("discovery.excluded-worlds", label("excluded-worlds"), excluded, 512),
                toggle("snapshot.auto-capture", label("auto-capture"), cfg.getBoolean("snapshot.auto-capture", true)),
                toggle("snapshot.auto-reset-on-refresh", label("auto-reset-on-refresh"), cfg.getBoolean("snapshot.auto-reset-on-refresh", false)),
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
        val m = plugin.messages
        val cfg = plugin.config
        val label = { key: String -> m.get("menu.storage.inputs.$key") }
        val types = listOf("sqlite", "mysql").map { Choice(it, m.get("menu.storage.types.$it")) }
        val current = if (cfg.getString("database.type", "sqlite").equals("mysql", ignoreCase = true)) "mysql" else "sqlite"
        showSettings(
            plugin = plugin,
            player = player,
            page = "menu.storage",
            body = body(plugin, "menu.storage.body"),
            inputs = listOf(
                singleOption("database.type", label("type"), types, current),
                text("database.mysql.host", label("host"), cfg.getString("database.mysql.host", "localhost") ?: "localhost", 255),
                text("database.mysql.port", label("port"), cfg.getInt("database.mysql.port", 3306).toString(), 5),
                text("database.mysql.database", label("database"), cfg.getString("database.mysql.database", "betterend") ?: "betterend", 64),
                text("database.mysql.username", label("username"), cfg.getString("database.mysql.username", "root") ?: "root", 64),
                text("database.mysql.password", label("password"), "", 128),
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
                else player.sendMessage(m.get("menu.storage.bad-port", "value" to raw))
            }
            view.getText("database.mysql.password")?.takeIf { it.isNotEmpty() }?.let { cfg.set("database.mysql.password", it) }
        }
    }

    /** Update checks (read once at startup), metrics and logging. */
    fun openUpdates(plugin: BetterEnd, player: Player) {
        val m = plugin.messages
        val cfg = plugin.config
        val label = { key: String -> m.get("menu.updates.inputs.$key") }
        val modes = listOf("off", "check-only", "notify", "auto-stage").map { Choice(it, m.get("menu.updates.modes.$it")) }
        // "download" behaves exactly like "notify", so it isn't offered and shows as notify.
        val mode = cfg.getString("update.mode", "notify")?.lowercase().let { md -> modes.firstOrNull { it.id == md }?.id } ?: "notify"
        val interval = cfg.getInt("update.check-interval-hours", 6)
        val holdHours = cfg.getInt("update.hold-new-updates-hours", 18)
        showSettings(
            plugin = plugin,
            player = player,
            page = "menu.updates",
            body = body(plugin, "menu.updates.body"),
            inputs = listOf(
                singleOption("update.mode", label("mode"), modes, mode),
                slider("update.check-interval-hours", label("check-interval-hours"), 1f, maxOf(48, interval).toFloat(), 1f, interval.toFloat()),
                toggle("update.hold-new-updates", label("hold-new-updates"), cfg.getBoolean("update.hold-new-updates", false)),
                slider("update.hold-new-updates-hours", label("hold-new-updates-hours"), 1f, maxOf(72, holdHours).toFloat(), 1f, holdHours.toFloat()),
                toggle("metrics.enabled", label("metrics"), cfg.getBoolean("metrics.enabled", true)),
                toggle("metrics.error-reporting", label("error-reporting"), cfg.getBoolean("metrics.error-reporting", true)),
                toggle("debug.verbose-logging", label("verbose-logging"), cfg.getBoolean("debug.verbose-logging", false)),
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

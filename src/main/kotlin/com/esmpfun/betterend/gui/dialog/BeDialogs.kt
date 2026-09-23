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

/**
 * BetterEnd's native MC26 configuration Dialogs - real in-game sliders,
 * toggles and choice buttons, no resource pack, no hand-edited YAML. Same
 * pattern as Mantle's dialogs, verified against paper-api 26.1.2
 * (`io.papermc.paper.registry.data.dialog`).
 *
 * A settings dialog is a single "notice" screen: a description body, a list
 * of inputs, and Save buttons whose custom-click callback receives the
 * filled-in [DialogResponseView]. Save handlers write straight into
 * config.yml (`config.set` + `saveConfig`) - every feature reads its config
 * live, so changes apply immediately, no reload.
 */
@Suppress("UnstableApiUsage")
object BeDialogs {

    /**
     * Dialog input keys only allow identifier characters - config paths like
     * `elytra.claim-mode` are rejected and the whole dialog fails to build.
     * Inputs are registered under a sanitized key; [showSettings] wraps the
     * response view so save handlers can keep reading by the original path.
     */
    private fun String.dialogKey(): String = replace(Regex("[^A-Za-z0-9_]"), "_")

    fun toggle(key: String, label: String, initial: Boolean): DialogInput =
        DialogInput.bool(key.dialogKey(), Component.text(label)).initial(initial).build()

    /**
     * The client renders the value as `min + n*step` computed in doubles, so
     * only binary-exact steps (0.25 / 0.5 / whole numbers) display cleanly.
     * Keep steps on that grid; the initial value is snapped to it here for
     * the same reason.
     */
    fun slider(key: String, label: String, min: Float, max: Float, step: Float, initial: Float): DialogInput {
        val snapped = (min + Math.round((initial.coerceIn(min, max) - min) / step) * step).coerceIn(min, max)
        return DialogInput.numberRange(key.dialogKey(), Component.text(label), min, max)
            .step(step)
            .initial(snapped)
            .labelFormat("%s: %s")
            .width(220)
            .build()
    }

    /** A pick-one input; the chosen [Choice.id] comes back via `getText(key)`. */
    data class Choice(val id: String, val label: String)

    fun singleOption(key: String, label: String, choices: List<Choice>, selectedId: String): DialogInput =
        DialogInput.singleOption(
            key.dialogKey(),
            Component.text(label),
            choices.map { SingleOptionDialogInput.OptionEntry.create(it.id, Component.text(it.label), it.id == selectedId) },
        ).width(220).build()

    /**
     * Renders a settings dialog with the four-button footer:
     * **[Back] [Save] [Save & Close] [Close]** (+ optional [extraButtons]
     * rendered before them, e.g. "Choose cost item").
     *
     * - Back - return to the /betterend menu (unsaved edits discarded).
     * - Save - persist + apply, then return to the menu.
     * - Save & Close - persist + apply, dialog closes.
     * - Close - plain exit, nothing saved.
     *
     * [onSave] runs when either save button is clicked.
     */
    fun showSettings(
        plugin: BetterEnd,
        player: Player,
        title: String,
        body: List<String>,
        inputs: List<DialogInput>,
        extraButtons: List<ActionButton> = emptyList(),
        onSave: (DialogResponseView) -> Unit,
    ) {
        fun mapped(view: DialogResponseView): DialogResponseView = object : DialogResponseView {
            // Save handlers read by original config path; inputs were registered
            // under sanitized keys - bridge transparently.
            override fun payload() = view.payload()
            override fun getText(key: String) = view.getText(key.dialogKey())
            override fun getBoolean(key: String) = view.getBoolean(key.dialogKey())
            override fun getFloat(key: String) = view.getFloat(key.dialogKey())
        }

        fun saveAndApply(view: DialogResponseView) {
            onSave(mapped(view))
            plugin.scheduler.runTask(Runnable {
                plugin.saveConfig()
                // Push config-dependent visuals (frame hints) to loaded ships.
                ElytraFrameListener.refreshLoaded(plugin)
            })
            player.sendMessage(Component.text("Settings saved & applied.", NamedTextColor.GREEN))
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
            // after_action defaults to CLOSE: every click would close the
            // screen, the game would re-grab the mouse, THEN the next screen
            // opens. NONE keeps the dialog up so Back/Save swap screen-to-
            // screen - at the cost that a button with no action of its own
            // does nothing at all (see closeButton).
            //
            // pause defaults to true, and the server rejects a pausing dialog
            // whose after-action leaves it paused. A dedicated server never
            // pauses anyway, so false is both correct and legal here.
            //
            // Escape-to-close is set explicitly rather than inherited: buttons
            // are single-use (Adventure's default), so a callback that throws
            // before it navigates would otherwise strand the player in a
            // dialog whose buttons are already spent.
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
                    // Adventure defaults to uses = 1, which suits these
                    // screens: every button either navigates (the next screen
                    // is built fresh, with fresh callbacks) or closes, so no
                    // button is legitimately clicked twice on one instance -
                    // and single-use debounces double-clicks on Save for free.
                    ClickCallback.Options.builder().build(),
                ),
            )
        }
        return b.build()
    }

    /**
     * An exit button.
     *
     * These dialogs use after-action NONE so navigation buttons can swap in
     * the next screen instead of dismissing it. The consequence is that a
     * button with no action does **nothing at all** - it can't fall back on
     * the after-action to close. Every exit button must close the dialog
     * itself, which is what this does.
     */
    fun closeButton(player: Player, label: String, tooltip: String): ActionButton =
        button(label, NamedTextColor.RED, tooltip) { player.closeDialog() }

    // ── the /betterend menu ──────────────────────────────────────────────────

    /** Top-level menu: one button per settings screen, plus the setup tour. */
    fun openMainMenu(plugin: BetterEnd, player: Player) {
        val cities = plugin.cityManager.all().size
        val mode = plugin.elytraClaimManager.mode().label
        val cost = plugin.elytraClaimManager.costStack()

        val elytra = button("Elytra Frames", NamedTextColor.AQUA, "Claim rules, cost and the frame hint") { _ ->
            plugin.scheduler.runAtEntity(player, Runnable { if (player.isOnline) openElytra(plugin, player) })
        }
        val citiesBtn = button("End Cities", NamedTextColor.LIGHT_PURPLE, "Discovery, per-player loot, protection and resets") { _ ->
            plugin.scheduler.runAtEntity(player, Runnable { if (player.isOnline) openCities(plugin, player) })
        }
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
            // See showSettings for why these three are set explicitly.
            .pause(false)
            .canCloseWithEscape(true)
            .afterAction(DialogBase.DialogAfterAction.NONE)
            .build()

        val dialog = Dialog.create { factory ->
            factory.empty().base(base)
                .type(DialogType.multiAction(listOf(elytra, citiesBtn, costItem, setup), close, 2))
        }
        player.showDialog(dialog)
    }

    // ── feature dialogs ──────────────────────────────────────────────────────

    /**
     * Elytra frame settings. The cost AMOUNT slider is rebuilt on every open
     * with `max = the chosen item's max stack size` (16 for ender pearls, 1
     * for a bed, 64 for most things), so an impossible cost can't be set.
     * The cost ITEM itself is picked in the [CurrencyPickerView] - a real
     * ItemStack is the one thing a dialog can't express.
     */
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

    /** End City discovery, per-player loot, protection and snapshot resets. */
    fun openCities(plugin: BetterEnd, player: Player) {
        val cfg = plugin.config
        showSettings(
            plugin = plugin,
            player = player,
            title = "End Cities",
            body = listOf(
                "End Cities register themselves as players find them.",
                "Per-player loot: everyone gets their own copy of each chest,",
                "refreshed per city every 'refresh window' hours (0 = never).",
                "Protection keeps the towers grief-free; auto-restore also",
                "rebuilds the structure from its snapshot on each refresh.",
                "'Only the ship' leaves towers and bridges open to players.",
            ),
            inputs = listOf(
                toggle("discovery.enabled", "Register new End Cities automatically", cfg.getBoolean("discovery.enabled", true)),
                toggle("loot.enabled", "Per-player container loot", cfg.getBoolean("loot.enabled", true)),
                refreshSlider("Loot refresh window (hours, 0 = never)", cfg.getInt("loot.refresh-hours", 12)),
                toggle("protection.enabled", "Grief protection", cfg.getBoolean("protection.enabled", true)),
                singleOption("protection.scope", "What is protected", SCOPES, scopeKey(cfg.getString("protection.scope"))),
                toggle("protection.dragon-head-takeable", "Players may take the ship's dragon head", cfg.getBoolean("protection.dragon-head-takeable", false)),
                toggle("protection.block-place", "Also deny placing blocks", cfg.getBoolean("protection.block-place", true)),
                toggle("protection.block-explosions", "Protect from explosions", cfg.getBoolean("protection.block-explosions", true)),
                toggle("protection.notify-denied", "Tell players when a break is denied", cfg.getBoolean("protection.notify-denied", true)),
                toggle("snapshot.auto-capture", "Snapshot each city on discovery", cfg.getBoolean("snapshot.auto-capture", true)),
                toggle("snapshot.auto-reset-on-refresh", "Auto-restore blocks on loot refresh", cfg.getBoolean("snapshot.auto-reset-on-refresh", false)),
            ),
        ) { view ->
            listOf(
                "discovery.enabled", "loot.enabled", "protection.enabled",
                "protection.block-place", "protection.block-explosions",
                "protection.notify-denied", "snapshot.auto-capture",
                "snapshot.auto-reset-on-refresh", "protection.dragon-head-takeable",
            ).forEach { key -> view.getBoolean(key)?.let { cfg.set(key, it) } }
            view.getText("protection.scope")?.let { cfg.set("protection.scope", it) }
            view.getFloat("loot.refresh-hours")?.let { cfg.set("loot.refresh-hours", it.toInt()) }
        }
    }
}

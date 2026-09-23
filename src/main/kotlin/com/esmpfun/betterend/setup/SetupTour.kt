package com.esmpfun.betterend.setup

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.gui.CurrencyPickerView
import com.esmpfun.betterend.gui.dialog.BeDialogs
import com.esmpfun.betterend.managers.ElytraClaimManager
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.dialog.DialogResponseView
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * `/betterend setup` - a guided, zero-jargon tour through every setting, one
 * dialog screen at a time (the BetterTrialChambers `/trial setup` idea).
 *
 * Each step is a small dialog: a plain-English explanation, one or two
 * inputs, and **[Back] [Next] [Finish later]**. "Next" saves that step's
 * inputs immediately (config.set + saveConfig - everything reads live), so
 * quitting halfway loses nothing. Finishing (or skipping on the welcome
 * screen) sets `setup.completed`, which stops the op join reminder.
 *
 * Progress is per-player and in-memory; `/betterend setup` after a restart
 * simply starts from the top with every input pre-filled from the current
 * config - re-walking is harmless.
 */
@Suppress("UnstableApiUsage")
object SetupTour {

    private val progress = ConcurrentHashMap<UUID, Int>()

    /** One tour screen: title, explanation, inputs and a per-step save. */
    private class Step(
        val title: String,
        val body: List<String>,
        val inputs: (BetterEnd) -> List<DialogInput>,
        val onSave: (BetterEnd, DialogResponseView) -> Unit,
    )

    private val steps = listOf(
        Step(
            title = "Elytra frames",
            body = listOf(
                "The elytra item frame in an End Ship becomes renewable:",
                "a player punches it (exactly like vanilla), gets an elytra in",
                "their inventory, and the frame stays for the next player.",
                "",
                "'Who can claim, how often' is the big choice. 'Once per",
                "ship' is the classic, fair default.",
            ),
            inputs = { plugin ->
                listOf(
                    BeDialogs.toggle("elytra.enabled", "Renewable elytra frames", plugin.config.getBoolean("elytra.enabled", true)),
                    BeDialogs.singleOption(
                        "elytra.claim-mode", "Who can claim, how often",
                        ElytraClaimManager.ClaimMode.entries.map { BeDialogs.Choice(it.key, it.label) },
                        ElytraClaimManager.ClaimMode.fromConfig(plugin.config.getString("elytra.claim-mode")).key,
                    ),
                )
            },
            onSave = { plugin, view ->
                view.getBoolean("elytra.enabled")?.let { plugin.config.set("elytra.enabled", it) }
                view.getText("elytra.claim-mode")?.let { plugin.config.set("elytra.claim-mode", it) }
            },
        ),
        Step(
            title = "Elytra cost",
            body = listOf(
                "A claim can cost something, say 1 shulker shell or 10 XP",
                "levels, or be completely free (the default).",
                "",
                "0 on a slider = free. To charge a different ITEM than the",
                "current one, use 'Choose Cost Item' in /betterend after the",
                "tour: you pick it straight from your inventory there.",
            ),
            inputs = { plugin ->
                val item = plugin.elytraClaimManager.costItemOrDefault()
                val name = item.type.name.lowercase().replace('_', ' ')
                listOf(
                    BeDialogs.slider(
                        "elytra.cost.amount", "Cost ($name, 0 = free)",
                        0f, item.maxStackSize.toFloat(), 1f,
                        plugin.config.getInt("elytra.cost.amount", 0).toFloat(),
                    ),
                    BeDialogs.levelsSlider(plugin.config.getInt("elytra.cost.levels", 0)),
                    BeDialogs.toggle("elytra.text-display", "Floating hint above the frame", plugin.config.getBoolean("elytra.text-display", true)),
                )
            },
            onSave = { plugin, view ->
                view.getFloat("elytra.cost.amount")?.let { plugin.config.set("elytra.cost.amount", it.toInt()) }
                view.getFloat("elytra.cost.levels")?.let { plugin.config.set("elytra.cost.levels", it.toInt()) }
                view.getBoolean("elytra.text-display")?.let { plugin.config.set("elytra.text-display", it) }
            },
        ),
        Step(
            title = "Per-player loot",
            body = listOf(
                "Every player gets their own private copy of each End City",
                "chest, so the second player to arrive doesn't find gutted",
                "containers.",
                "",
                "The refresh window is per city: that many hours after a city",
                "is first looted, everyone's copies reset and the loot is",
                "fresh again. 0 = copies never refresh.",
            ),
            inputs = { plugin ->
                listOf(
                    BeDialogs.toggle("loot.enabled", "Per-player container loot", plugin.config.getBoolean("loot.enabled", true)),
                    BeDialogs.refreshSlider("Refresh window (hours, 0 = never)", plugin.config.getInt("loot.refresh-hours", 12)),
                )
            },
            onSave = { plugin, view ->
                view.getBoolean("loot.enabled")?.let { plugin.config.set("loot.enabled", it) }
                view.getFloat("loot.refresh-hours")?.let { plugin.config.set("loot.refresh-hours", it.toInt()) }
            },
        ),
        Step(
            title = "Protection",
            body = listOf(
                "Keeps End City structures grief-free: blocks inside the",
                "generated towers, bridges and the ship can't be broken or",
                "built over. The empty void between them stays fully",
                "buildable, and ops can bypass with a permission.",
                "",
                "'Only the ship' leaves towers and bridges open, but still",
                "guards the ship and the city's loot chests.",
            ),
            inputs = { plugin ->
                listOf(
                    BeDialogs.toggle("protection.enabled", "Grief protection", plugin.config.getBoolean("protection.enabled", true)),
                    BeDialogs.singleOption("protection.scope", "What is protected", BeDialogs.SCOPES, BeDialogs.scopeKey(plugin.config.getString("protection.scope"))),
                    BeDialogs.toggle("protection.notify-denied", "Tell players when a break is denied", plugin.config.getBoolean("protection.notify-denied", true)),
                )
            },
            onSave = { plugin, view ->
                view.getBoolean("protection.enabled")?.let { plugin.config.set("protection.enabled", it) }
                view.getText("protection.scope")?.let { plugin.config.set("protection.scope", it) }
                view.getBoolean("protection.notify-denied")?.let { plugin.config.set("protection.notify-denied", it) }
            },
        ),
        Step(
            title = "Snapshots & resets",
            body = listOf(
                "A snapshot is a saved copy of a city's blocks, taken when",
                "the city is discovered. '/betterend reset <id>' restores it",
                "any time.",
                "",
                "Auto-restore additionally rebuilds the city on every loot",
                "refresh. Nice for a truly renewable End, but it rewrites",
                "blocks while players may be inside, so it ships off.",
            ),
            inputs = { plugin ->
                listOf(
                    BeDialogs.toggle("snapshot.auto-capture", "Snapshot each city on discovery", plugin.config.getBoolean("snapshot.auto-capture", true)),
                    BeDialogs.toggle("snapshot.auto-reset-on-refresh", "Auto-restore blocks on loot refresh", plugin.config.getBoolean("snapshot.auto-reset-on-refresh", false)),
                )
            },
            onSave = { plugin, view ->
                view.getBoolean("snapshot.auto-capture")?.let { plugin.config.set("snapshot.auto-capture", it) }
                view.getBoolean("snapshot.auto-reset-on-refresh")?.let { plugin.config.set("snapshot.auto-reset-on-refresh", it) }
            },
        ),
    )

    /** Opens the welcome screen (or resumes where this player left off). */
    fun start(plugin: BetterEnd, player: Player) {
        val at = progress[player.uniqueId]
        if (at != null && at in steps.indices) openStep(plugin, player, at) else openWelcome(plugin, player)
    }

    private fun openWelcome(plugin: BetterEnd, player: Player) {
        val begin = BeDialogs.button("Start the tour", NamedTextColor.GREEN, "${steps.size} quick screens, ~2 minutes") { _ ->
            progress[player.uniqueId] = 0
            plugin.scheduler.runAtEntity(player, Runnable { if (player.isOnline) openStep(plugin, player, 0) })
        }
        val skip = BeDialogs.button("Skip, defaults are fine", NamedTextColor.YELLOW, "Everything works out of the box; you can re-run this anytime") { _ ->
            markCompleted(plugin)
            player.sendMessage(Component.text("Setup skipped, the defaults are live. /betterend reopens the menu anytime.", NamedTextColor.GRAY))
            player.closeDialog()
        }
        val close = BeDialogs.closeButton(player, "Close", "Ask me again next time")

        val base = DialogBase.builder(Component.text("Better End Cities - first-time setup", NamedTextColor.DARK_AQUA))
            .body(
                listOf(
                    "Welcome! Better End Cities makes the End multiplayer-friendly:",
                    "",
                    "• Every player earns their own elytra from each End Ship.",
                    "• Every player gets their own copy of End City loot.",
                    "• Structures are protected, snapshotted, and resettable.",
                    "",
                    "This tour walks through each setting in plain words.",
                    "Everything already works with the defaults.",
                ).map { DialogBody.plainMessage(Component.text(it, NamedTextColor.GRAY)) }
            )
            // See BeDialogs.showSettings for why these three are set explicitly.
            .pause(false)
            .canCloseWithEscape(true)
            .afterAction(DialogBase.DialogAfterAction.NONE)
            .build()

        player.showDialog(Dialog.create { factory ->
            factory.empty().base(base).type(DialogType.multiAction(listOf(begin, skip), close, 1))
        })
    }

    private fun openStep(plugin: BetterEnd, player: Player, index: Int) {
        val step = steps[index]
        progress[player.uniqueId] = index

        fun saveStep(view: DialogResponseView) {
            step.onSave(plugin, mapped(view))
            plugin.scheduler.runTask(Runnable {
                plugin.saveConfig()
                com.esmpfun.betterend.listeners.ElytraFrameListener.refreshLoaded(plugin)
            })
        }

        val buttons = mutableListOf<ActionButton>()
        if (index > 0) {
            buttons += BeDialogs.button("Back", NamedTextColor.YELLOW, "Previous step (this screen's edits are not saved)") { _ ->
                plugin.scheduler.runAtEntity(player, Runnable { if (player.isOnline) openStep(plugin, player, index - 1) })
            }
        }
        val lastStep = index == steps.lastIndex
        buttons += BeDialogs.button(
            if (lastStep) "Save & finish" else "Next",
            NamedTextColor.GREEN,
            if (lastStep) "Save this step and finish the tour" else "Save this step and continue",
        ) { view ->
            saveStep(view)
            if (lastStep) {
                progress.remove(player.uniqueId)
                markCompleted(plugin)
                plugin.scheduler.runAtEntity(player, Runnable { if (player.isOnline) openFinish(plugin, player) })
            } else {
                plugin.scheduler.runAtEntity(player, Runnable { if (player.isOnline) openStep(plugin, player, index + 1) })
            }
        }
        val finishLater = BeDialogs.closeButton(player, "Finish later", "Close the tour. /betterend setup resumes right here")

        val base = DialogBase.builder(
            Component.text("Setup (${index + 1}/${steps.size}): ${step.title}", NamedTextColor.DARK_AQUA)
        )
            .body(step.body.map { DialogBody.plainMessage(Component.text(it, NamedTextColor.GRAY)) })
            .inputs(step.inputs(plugin))
            // See BeDialogs.showSettings for why these three are set explicitly.
            .pause(false)
            .canCloseWithEscape(true)
            .afterAction(DialogBase.DialogAfterAction.NONE)
            .build()

        player.showDialog(Dialog.create { factory ->
            factory.empty().base(base).type(DialogType.multiAction(buttons, finishLater, buttons.size.coerceAtMost(2)))
        })
    }

    private fun openFinish(plugin: BetterEnd, player: Player) {
        val cost = plugin.elytraClaimManager.costStack()
        val menu = BeDialogs.button("Open the menu", NamedTextColor.GREEN, "Everything from the tour lives in /betterend") { _ ->
            plugin.scheduler.runAtEntity(player, Runnable { if (player.isOnline) BeDialogs.openMainMenu(plugin, player) })
        }
        val pickItem = BeDialogs.button("Choose Cost Item", NamedTextColor.GOLD, "Pick which item a claim costs, straight from your inventory") { _ ->
            player.closeDialog()
            plugin.scheduler.runAtEntity(player, Runnable { if (player.isOnline) CurrencyPickerView(plugin).open(player) })
        }
        val close = BeDialogs.closeButton(player, "Done", "Close. Happy flying!")

        val costLine = if (cost == null) "Claims are free" else "A claim costs ${cost.amount} x ${cost.type.name.lowercase().replace('_', ' ')}"
        val base = DialogBase.builder(Component.text("Setup complete", NamedTextColor.DARK_AQUA))
            .body(
                listOf(
                    "That's everything, Better End Cities is live:",
                    "",
                    "• Elytra: ${plugin.elytraClaimManager.mode().label}",
                    "• $costLine",
                    "• Loot refresh: every ${plugin.config.getInt("loot.refresh-hours", 12)}h (per city)",
                    "",
                    "Change any of it later with /betterend.",
                ).map { DialogBody.plainMessage(Component.text(it, NamedTextColor.GRAY)) }
            )
            // See BeDialogs.showSettings for why these three are set explicitly.
            .pause(false)
            .canCloseWithEscape(true)
            .afterAction(DialogBase.DialogAfterAction.NONE)
            .build()

        player.showDialog(Dialog.create { factory ->
            factory.empty().base(base).type(DialogType.multiAction(listOf(menu, pickItem), close, 2))
        })
    }

    private fun markCompleted(plugin: BetterEnd) {
        plugin.scheduler.runTask(Runnable {
            plugin.config.set("setup.completed", true)
            plugin.saveConfig()
        })
    }

    private fun mapped(view: DialogResponseView): DialogResponseView = object : DialogResponseView {
        private fun String.dialogKey(): String = replace(Regex("[^A-Za-z0-9_]"), "_")
        override fun payload() = view.payload()
        override fun getText(key: String) = view.getText(key.dialogKey())
        override fun getBoolean(key: String) = view.getBoolean(key.dialogKey())
        override fun getFloat(key: String) = view.getFloat(key.dialogKey())
    }
}

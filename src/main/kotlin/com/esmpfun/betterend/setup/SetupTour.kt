package com.esmpfun.betterend.setup

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.gui.CurrencyPickerView
import com.esmpfun.betterend.gui.dialog.BeDialogs
import com.esmpfun.betterend.managers.ElytraClaimManager
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.dialog.DialogResponseView
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * `/betterend setup`: one small dialog per step, each saved on Next.
 * Progress is kept in memory only; finishing or skipping sets `setup.completed`.
 */
@Suppress("UnstableApiUsage")
object SetupTour {

    private val progress = ConcurrentHashMap<UUID, Int>()

    // [key] names the step's section under `setup.steps` in messages.yml.
    private class Step(
        val key: String,
        val inputs: (BetterEnd, (String) -> Component) -> List<DialogInput>,
        val onSave: (BetterEnd, DialogResponseView) -> Unit,
    )

    private val steps = listOf(
        Step(
            key = "elytra",
            inputs = { plugin, label ->
                listOf(
                    BeDialogs.toggle("elytra.enabled", label("enabled"), plugin.config.getBoolean("elytra.enabled", true)),
                    BeDialogs.singleOption(
                        "elytra.claim-mode", label("claim-mode"),
                        BeDialogs.claimModes(plugin),
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
            key = "cost",
            inputs = { plugin, label ->
                val item = plugin.elytraClaimManager.costItemOrDefault()
                listOf(
                    BeDialogs.slider(
                        "elytra.cost.amount",
                        plugin.messages.get("setup.steps.cost.inputs.cost-amount", "item" to BeDialogs.itemName(item)),
                        0f, item.maxStackSize.toFloat(), 1f,
                        plugin.config.getInt("elytra.cost.amount", 0).toFloat(),
                    ),
                    BeDialogs.levelsSlider(label("cost-levels"), plugin.config.getInt("elytra.cost.levels", 0)),
                    BeDialogs.toggle("elytra.text-display", label("text-display"), plugin.config.getBoolean("elytra.text-display", true)),
                )
            },
            onSave = { plugin, view ->
                view.getFloat("elytra.cost.amount")?.let { plugin.config.set("elytra.cost.amount", it.toInt()) }
                view.getFloat("elytra.cost.levels")?.let { plugin.config.set("elytra.cost.levels", it.toInt()) }
                view.getBoolean("elytra.text-display")?.let { plugin.config.set("elytra.text-display", it) }
            },
        ),
        Step(
            key = "loot",
            inputs = { plugin, label ->
                listOf(
                    BeDialogs.toggle("loot.enabled", label("enabled"), plugin.config.getBoolean("loot.enabled", true)),
                    BeDialogs.refreshSlider(label("refresh-hours"), plugin.config.getInt("loot.refresh-hours", 12)),
                )
            },
            onSave = { plugin, view ->
                view.getBoolean("loot.enabled")?.let { plugin.config.set("loot.enabled", it) }
                view.getFloat("loot.refresh-hours")?.let { plugin.config.set("loot.refresh-hours", it.toInt()) }
            },
        ),
        Step(
            key = "protection",
            inputs = { plugin, label ->
                listOf(
                    BeDialogs.toggle("protection.enabled", label("enabled"), plugin.config.getBoolean("protection.enabled", true)),
                    BeDialogs.singleOption("protection.scope", label("scope"), BeDialogs.scopes(plugin), BeDialogs.scopeKey(plugin.config.getString("protection.scope"))),
                    BeDialogs.toggle("protection.notify-denied", label("notify-denied"), plugin.config.getBoolean("protection.notify-denied", true)),
                )
            },
            onSave = { plugin, view ->
                view.getBoolean("protection.enabled")?.let { plugin.config.set("protection.enabled", it) }
                view.getText("protection.scope")?.let { plugin.config.set("protection.scope", it) }
                view.getBoolean("protection.notify-denied")?.let { plugin.config.set("protection.notify-denied", it) }
            },
        ),
        Step(
            key = "snapshots",
            inputs = { plugin, label ->
                listOf(
                    BeDialogs.toggle("snapshot.auto-capture", label("auto-capture"), plugin.config.getBoolean("snapshot.auto-capture", true)),
                    BeDialogs.toggle("snapshot.auto-reset-on-refresh", label("auto-reset-on-refresh"), plugin.config.getBoolean("snapshot.auto-reset-on-refresh", false)),
                )
            },
            onSave = { plugin, view ->
                view.getBoolean("snapshot.auto-capture")?.let { plugin.config.set("snapshot.auto-capture", it) }
                view.getBoolean("snapshot.auto-reset-on-refresh")?.let { plugin.config.set("snapshot.auto-reset-on-refresh", it) }
            },
        ),
    )

    /** Resumes where this player left off, or opens the welcome screen. */
    fun start(plugin: BetterEnd, player: Player) {
        val at = progress[player.uniqueId]
        if (at != null && at in steps.indices) openStep(plugin, player, at) else openWelcome(plugin, player)
    }

    private fun openWelcome(plugin: BetterEnd, player: Player) {
        val begin = BeDialogs.button(plugin, "setup.welcome.start", NamedTextColor.GREEN, "steps" to steps.size) { _ ->
            progress[player.uniqueId] = 0
            plugin.scheduler.runAtEntity(player, Runnable { if (player.isOnline) openStep(plugin, player, 0) })
        }
        val skip = BeDialogs.button(plugin, "setup.welcome.skip", NamedTextColor.YELLOW) { _ ->
            markCompleted(plugin)
            player.sendMessage(plugin.messages.get("setup.skipped"))
            player.closeDialog()
        }
        val close = BeDialogs.closeButton(plugin, player, "setup.welcome.close")

        val base = DialogBase.builder(BeDialogs.title(plugin, "setup.welcome.title"))
            .body(BeDialogs.body(plugin, "setup.welcome.body"))
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
        val section = "setup.steps.${step.key}"
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
            buttons += BeDialogs.button(plugin, "setup.buttons.back", NamedTextColor.YELLOW) { _ ->
                plugin.scheduler.runAtEntity(player, Runnable { if (player.isOnline) openStep(plugin, player, index - 1) })
            }
        }
        val lastStep = index == steps.lastIndex
        buttons += BeDialogs.button(plugin, if (lastStep) "setup.buttons.finish" else "setup.buttons.next", NamedTextColor.GREEN) { view ->
            saveStep(view)
            if (lastStep) {
                progress.remove(player.uniqueId)
                markCompleted(plugin)
                plugin.scheduler.runAtEntity(player, Runnable { if (player.isOnline) openFinish(plugin, player) })
            } else {
                plugin.scheduler.runAtEntity(player, Runnable { if (player.isOnline) openStep(plugin, player, index + 1) })
            }
        }
        val finishLater = BeDialogs.closeButton(plugin, player, "setup.buttons.finish-later")

        val title = BeDialogs.title(
            plugin, "setup.step-title",
            "number" to index + 1, "total" to steps.size, "title" to plugin.messages.get("$section.title"),
        )
        val base = DialogBase.builder(title)
            .body(BeDialogs.body(plugin, "$section.body"))
            .inputs(step.inputs(plugin) { key -> plugin.messages.get("$section.inputs.$key") })
            .pause(false)
            .canCloseWithEscape(true)
            .afterAction(DialogBase.DialogAfterAction.NONE)
            .build()

        player.showDialog(Dialog.create { factory ->
            factory.empty().base(base).type(DialogType.multiAction(buttons, finishLater, buttons.size.coerceAtMost(2)))
        })
    }

    private fun openFinish(plugin: BetterEnd, player: Player) {
        val m = plugin.messages
        val cost = plugin.elytraClaimManager.costStack()
        val menu = BeDialogs.button(plugin, "setup.finish.menu", NamedTextColor.GREEN) { _ ->
            plugin.scheduler.runAtEntity(player, Runnable { if (player.isOnline) BeDialogs.openMainMenu(plugin, player) })
        }
        val pickItem = BeDialogs.button(plugin, "setup.finish.cost-item", NamedTextColor.GOLD) { _ ->
            player.closeDialog()
            plugin.scheduler.runAtEntity(player, Runnable { if (player.isOnline) CurrencyPickerView(plugin).open(player) })
        }
        val close = BeDialogs.closeButton(plugin, player, "setup.finish.done")

        val costLine = if (cost == null) m.get("setup.finish.cost-free")
        else m.get("setup.finish.cost", "amount" to cost.amount, "item" to BeDialogs.itemName(cost))
        val base = DialogBase.builder(BeDialogs.title(plugin, "setup.finish.title"))
            .body(
                BeDialogs.body(
                    plugin, "setup.finish.body",
                    "mode" to m.get("claim-modes.${plugin.elytraClaimManager.mode().key}"),
                    "cost" to costLine,
                    "hours" to plugin.config.getInt("loot.refresh-hours", 12),
                )
            )
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

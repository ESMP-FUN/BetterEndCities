package com.esmpfun.betterend.gui

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.gui.dialog.BeDialogs
import com.esmpfun.betterend.gui.framework.BaseHolder
import com.esmpfun.betterend.gui.framework.VcGui
import com.esmpfun.betterend.gui.framework.VcGuiItem
import com.esmpfun.betterend.listeners.ElytraFrameListener
import com.esmpfun.betterend.utils.AntiDupeCompat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/** Picks the elytra cost item by shift-clicking it in your own inventory; the item is copied, not taken. */
class CurrencyPickerView(private val plugin: BetterEnd) : VcGui(
    rows = 3,
    title = Component.text("Elytra Cost Item", NamedTextColor.DARK_AQUA),
    holder = Holder(),
    requiredPermission = "betterend.admin",
) {
    class Holder : BaseHolder()

    private var draft: ItemStack = plugin.elytraClaimManager.costItemOrDefault()

    init { layout() }

    private fun layout() {
        set(
            13,
            VcGuiItem(
                stack = renderDraft(),
                acceptsBottomShiftClick = true,
                onClick = { ctx ->
                    if (ctx.isBottomInv) {
                        ctx.currentItem?.takeIf { !it.type.isAir }?.let {
                            draft = it.clone().apply { amount = 1 }
                            // An owner tag would make it match only this admin's copies.
                            AntiDupeCompat.stripOwnership(draft)
                        }
                        layout()
                        update()
                    }
                },
            ),
        )

        set(
            11,
            VcGuiItem.of(
                material = Material.RED_DYE,
                name = "<red>Reset to default",
                lore = listOf("<gray>Shulker Shell"),
                onClick = { _ ->
                    draft = ItemStack(Material.SHULKER_SHELL)
                    layout()
                    update()
                },
            ),
        )

        set(
            15,
            VcGuiItem.of(
                material = Material.GREEN_CONCRETE,
                name = "<green>Save & set amount",
                lore = listOf(
                    "<gray>Saves the item, then reopens the",
                    "<gray>Elytra settings so you can set how",
                    "<gray>many a claim costs (slider).",
                ),
                onClick = { ctx ->
                    plugin.elytraClaimManager.saveCostItem(draft)
                    ElytraFrameListener.refreshLoaded(plugin)
                    ctx.player.sendMessage(
                        Component.text("Cost item saved: ", NamedTextColor.GREEN)
                            .append(draft.effectiveName()),
                    )
                    if (AntiDupeCompat.isTrackedMaterial(draft.type)) {
                        ctx.player.sendMessage(
                            Component.text(
                                "Warning: AntiDupePro tracks ${draft.type.name}. Every player's copy is marked as theirs, " +
                                    "so it won't count as this cost item and nobody could pay. Pick an item AntiDupePro doesn't track.",
                                NamedTextColor.RED,
                            ),
                        )
                    }
                    ctx.player.closeInventory()
                    plugin.scheduler.runAtEntity(ctx.player, Runnable {
                        if (ctx.player.isOnline) BeDialogs.openElytra(plugin, ctx.player)
                    })
                },
            ),
        )

        set(
            18,
            VcGuiItem.of(
                material = Material.ARROW,
                name = "<yellow>Back",
                lore = listOf("<gray>Back to the Elytra settings (item not saved)"),
                onClick = { ctx ->
                    ctx.player.closeInventory()
                    plugin.scheduler.runAtEntity(ctx.player, Runnable {
                        if (ctx.player.isOnline) BeDialogs.openElytra(plugin, ctx.player)
                    })
                },
            ),
        )
        set(
            26,
            VcGuiItem.of(
                material = Material.BARRIER,
                name = "<red>Close",
                onClick = { ctx -> ctx.player.closeInventory() },
            ),
        )
    }

    private fun renderDraft(): ItemStack {
        val shown = draft.clone()
        shown.editMeta { meta ->
            meta.lore(
                listOf(
                    Component.text("Current cost item (stacks to ${draft.maxStackSize})", NamedTextColor.AQUA),
                    Component.empty(),
                    Component.text("Shift-click an item in YOUR inventory", NamedTextColor.GRAY),
                    Component.text("to make it the cost (item is kept).", NamedTextColor.GRAY),
                    Component.text("The amount is set in the Elytra dialog.", NamedTextColor.GRAY),
                ).map { it.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false) },
            )
        }
        return shown
    }
}

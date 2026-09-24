package com.esmpfun.betterend.gui

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.gui.dialog.BeDialogs
import com.esmpfun.betterend.gui.framework.BaseHolder
import com.esmpfun.betterend.gui.framework.VcGui
import com.esmpfun.betterend.gui.framework.VcGuiItem
import com.esmpfun.betterend.listeners.ElytraFrameListener
import com.esmpfun.betterend.utils.AntiDupeCompat
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/** Picks the elytra cost item by shift-clicking it in your own inventory; the item is copied, not taken. */
class CurrencyPickerView(private val plugin: BetterEnd) : VcGui(
    rows = 3,
    title = plugin.messages.get("cost-picker.title"),
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
                name = plugin.messages.raw("cost-picker.reset.name"),
                lore = plugin.messages.rawLines("cost-picker.reset.lore"),
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
                name = plugin.messages.raw("cost-picker.save.name"),
                lore = plugin.messages.rawLines("cost-picker.save.lore"),
                onClick = { ctx ->
                    plugin.elytraClaimManager.saveCostItem(draft)
                    ElytraFrameListener.refreshLoaded(plugin)
                    ctx.player.sendMessage(plugin.messages.get("cost-picker.saved", "item" to draft.effectiveName()))
                    if (AntiDupeCompat.isTrackedMaterial(draft.type)) {
                        ctx.player.sendMessage(plugin.messages.get("cost-picker.anti-dupe-warning", "material" to draft.type.name))
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
                name = plugin.messages.raw("cost-picker.back.name"),
                lore = plugin.messages.rawLines("cost-picker.back.lore"),
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
                name = plugin.messages.raw("cost-picker.close.name"),
                onClick = { ctx -> ctx.player.closeInventory() },
            ),
        )
    }

    private fun renderDraft(): ItemStack {
        val shown = draft.clone()
        shown.editMeta { meta ->
            meta.lore(
                plugin.messages.lines("cost-picker.current-lore", "stack" to draft.maxStackSize)
                    .map { it.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false) },
            )
        }
        return shown
    }
}

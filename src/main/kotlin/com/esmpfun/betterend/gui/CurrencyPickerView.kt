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

/**
 * VcGui picker for the elytra claim **cost item** - the one setting a Dialog
 * can't express, because it's a real [ItemStack]: any item (custom/NBT items
 * from other plugins included).
 *
 * - **Shift-click any item in your own inventory** to stamp it as the cost
 *   item (the item is NOT consumed - stamp semantics via
 *   `acceptsBottomShiftClick`).
 * - The AMOUNT is set on the slider in the Elytra dialog, which clamps
 *   itself to this item's max stack size - so the two screens can't
 *   contradict each other.
 * - Save persists the item and returns to the Elytra dialog with the slider
 *   already re-scaled.
 */
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
        // The cost-item slot - stamp target.
        set(
            13,
            VcGuiItem(
                stack = renderDraft(),
                acceptsBottomShiftClick = true,
                onClick = { ctx ->
                    if (ctx.isBottomInv) {
                        ctx.currentItem?.takeIf { !it.type.isAir }?.let {
                            draft = it.clone().apply { amount = 1 }
                            // An AntiDupePro ownership tag on the cost item would
                            // make it match only the stamping admin's own copies -
                            // unpayable for everyone else (matching is isSimilar).
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
                        // Every player's copy of a tracked item carries their own
                        // ownership tag, so it can never match this cost item -
                        // nobody could pay. Warn loudly.
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

    /** The cost slot's rendered stack: the draft itself with an instruction lore. */
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

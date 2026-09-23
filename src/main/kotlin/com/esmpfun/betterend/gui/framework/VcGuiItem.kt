package com.esmpfun.betterend.gui.framework

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * One slot's worth of "this item is here, here's what to do when it's
 * clicked." Wraps the rendered [ItemStack] together with the click
 * handler and the two flags that gate the framework's partial-cancel
 * pattern.
 *
 * The flags are the unique value-add over external GUI frameworks
 * (InventoryFramework, InvUI, TriumphGUI): those expose raw
 * `InventoryClickEvent` to the consumer and force them to write the
 * listener manually. Here, declaring `acceptsBottomShiftClick = true`
 * is enough - the central [VcGuiListener] handles the rest.
 *
 * @property stack The item rendered into the slot. The framework calls
 *   `Inventory.setItem(slot, stack)` directly - no cloning, no mutation
 *   on the framework side.
 * @property onClick Fired by the listener after permission re-check.
 *   Receives a [ClickContext] snapshot. Return value is ignored (Kotlin
 *   `Unit`) - mutation goes through closure-captured state.
 * @property acceptsBottomShiftClick When true, a shift-click on ANY
 *   item in the player's own inventory while this GUI is open routes
 *   the clicked item to *this* slot's [onClick] with
 *   [ClickContext.isBottomInv] = true. The bottom-inventory item is
 *   NOT moved - it stays in the player's inventory. Use this for the
 *   "stamp" pattern (e.g. "click any item in your inventory to add it
 *   to this loot pool, keeping the item").
 * @property acceptsDrag When true, an [org.bukkit.event.inventory.InventoryDragEvent]
 *   that lands on this exact slot fires [VcGui.handleDrag] with a [DragContext].
 *   The cursor is NOT consumed - same stamp semantics. Drag
 *   configurations touching multiple top slots are always cancelled by
 *   the listener as the dup-exploit guard. For the bulk-deposit
 *   pattern, see [VcGui.handleDrag] override instead.
 */
data class VcGuiItem(
    val stack: ItemStack,
    val onClick: ((ClickContext) -> Unit)? = null,
    val acceptsBottomShiftClick: Boolean = false,
    val acceptsDrag: Boolean = false,
) {
    companion object {
        private val mm: MiniMessage = MiniMessage.miniMessage()

        /** Convenience: build a non-italicised display-name + lore item from MiniMessage strings. */
        fun of(
            material: Material,
            name: String,
            lore: List<String> = emptyList(),
            onClick: ((ClickContext) -> Unit)? = null,
            acceptsBottomShiftClick: Boolean = false,
            acceptsDrag: Boolean = false,
        ): VcGuiItem {
            val stack = ItemStack(material, 1)
            stack.editMeta { meta ->
                meta.displayName(mm.deserialize(name).decoration(TextDecoration.ITALIC, false))
                if (lore.isNotEmpty()) {
                    meta.lore(lore.map { mm.deserialize(it).decoration(TextDecoration.ITALIC, false) })
                }
            }
            return VcGuiItem(stack, onClick, acceptsBottomShiftClick, acceptsDrag)
        }

        /** Convenience: build from a pre-built [Component] pair. */
        fun of(
            material: Material,
            name: Component,
            lore: List<Component> = emptyList(),
            onClick: ((ClickContext) -> Unit)? = null,
            acceptsBottomShiftClick: Boolean = false,
            acceptsDrag: Boolean = false,
        ): VcGuiItem {
            val stack = ItemStack(material, 1)
            stack.editMeta { meta ->
                meta.displayName(name.decoration(TextDecoration.ITALIC, false))
                if (lore.isNotEmpty()) {
                    meta.lore(lore.map { it.decoration(TextDecoration.ITALIC, false) })
                }
            }
            return VcGuiItem(stack, onClick, acceptsBottomShiftClick, acceptsDrag)
        }

        /** Convenience: wrap a pre-built [ItemStack] (the common case in TCP, where
         *  GuiComponents
         *  already produces fully-rendered `ItemStack`s from `gui.<view>.*`
         *  messages.yml keys).
         *
         *  `onClick` is the LAST parameter so trailing-lambda syntax binds to it:
         *  `VcGuiItem.wrap(stack) { ctx -> ... }` works without naming. */
        fun wrap(
            stack: ItemStack,
            acceptsBottomShiftClick: Boolean = false,
            acceptsDrag: Boolean = false,
            onClick: ((ClickContext) -> Unit)? = null,
        ): VcGuiItem = VcGuiItem(stack, onClick, acceptsBottomShiftClick, acceptsDrag)
    }
}

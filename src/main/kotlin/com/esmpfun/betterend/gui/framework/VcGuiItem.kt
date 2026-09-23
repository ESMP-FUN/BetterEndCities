package com.esmpfun.betterend.gui.framework

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * One slot: what it shows and what a click does. With [acceptsBottomShiftClick]
 * a shift-click in the player's own inventory lands here without moving the item.
 */
data class VcGuiItem(
    val stack: ItemStack,
    val onClick: ((ClickContext) -> Unit)? = null,
    val acceptsBottomShiftClick: Boolean = false,
    val acceptsDrag: Boolean = false,
) {
    companion object {
        private val mm: MiniMessage = MiniMessage.miniMessage()

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

        fun wrap(
            stack: ItemStack,
            acceptsBottomShiftClick: Boolean = false,
            acceptsDrag: Boolean = false,
            onClick: ((ClickContext) -> Unit)? = null,
        ): VcGuiItem = VcGuiItem(stack, onClick, acceptsBottomShiftClick, acceptsDrag)
    }
}

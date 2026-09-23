package com.esmpfun.betterend.gui.framework

import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.ItemStack

/**
 * Snapshot of a click event passed to a [VcGuiItem.onClick] handler.
 *
 * The framework clones [currentItem] and [cursor] before constructing
 * this so handlers can stash references safely (Bukkit's underlying
 * `ItemStack` objects mutate next tick).
 *
 * @property isBottomInv `true` when the click happened in the player's
 *   own (bottom) inventory and was routed here via the
 *   `acceptsBottomShiftClick` mechanism. `false` for a regular top-
 *   inventory click on this item's slot.
 * @property event The original Bukkit event. Use only when you genuinely
 *   need state not exposed by the snapshot (e.g. `event.view`); avoid
 *   mutating the event from a handler - the framework has already
 *   decided cancellation by the time `onClick` fires.
 */
data class ClickContext(
    val player: Player,
    val click: ClickType,
    val action: InventoryAction,
    val slot: Int,
    val isBottomInv: Boolean,
    val currentItem: ItemStack?,
    val cursor: ItemStack?,
    val event: InventoryClickEvent,
)

/**
 * Snapshot of a drag event routed to [VcGui.handleDrag].
 *
 * Fired only when the drag landed on a single top-slot with
 * [VcGuiItem.acceptsDrag] = true (the dup-exploit guard cancels every
 * other configuration). The cursor is **not** consumed - `acceptsDrag`
 * semantics are "stamp the item identity, don't transfer it."
 *
 * For the bulk-deposit pattern (drag many items in, close to commit),
 * the GUI overrides [VcGui.handleDrag] to allow the drag through (do
 * not cancel) and reads items off the inventory on close.
 */
data class DragContext(
    val player: Player,
    val rawSlots: Set<Int>,
    val newItems: Map<Int, ItemStack>,
    val targetSlot: Int,
    val depositedItem: ItemStack,
    val event: InventoryDragEvent,
)

package com.esmpfun.betterend.gui.framework

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent

/**
 * Central click / drag / close dispatcher for every [VcGui]-based GUI.
 *
 * Implements the canonical Paper partial-cancel pattern:
 *
 * - **Cross-inventory actions** ([InventoryAction.MOVE_TO_OTHER_INVENTORY],
 *   [InventoryAction.COLLECT_TO_CURSOR],
 *   [InventoryAction.HOTBAR_SWAP],
 *   [InventoryAction.HOTBAR_MOVE_AND_READD]) are **always cancelled**,
 *   regardless of which inventory was clicked. These are the dup-exploit
 *   family - letting any of them through would let a player move items
 *   between their inventory and our GUI in ways we can't reason about.
 *
 * - **`MOVE_TO_OTHER_INVENTORY` (shift-click) in the bottom inventory**
 *   gets one extra check first: if any slot in the GUI declared
 *   [VcGuiItem.acceptsBottomShiftClick] = true, that slot's [VcGuiItem.onClick]
 *   handler is invoked with [ClickContext.isBottomInv] = true and a
 *   snapshot of the clicked item. The event is cancelled either way -
 *   the bottom-inv item stays where it is (stamp, not transfer). This is
 *   the "click any item in your inventory to add it to this pool, keeping
 *   the item" affordance.
 *
 * - **Clicks in the top inventory** are always cancelled and dispatched
 *   to [VcGuiItem.onClick] for the clicked slot.
 *
 * - **Clicks in the bottom inventory with safe actions** (PICKUP_*,
 *   PLACE_*, SWAP_WITH_CURSOR, DROP_*, CLONE_STACK) are **not** cancelled.
 *   The player can manipulate their own inventory normally while our GUI
 *   is open. This is the change that unblocks "pick up an item onto your
 *   cursor while a GUI is open" - InventoryFramework's blanket
 *   setOnGlobalClick cancelled these and broke the cursor flow.
 *
 * Drag handling:
 * - Drags that touch only the bottom inventory pass through unchanged.
 * - Drags landing on a single top-inv slot with [VcGuiItem.acceptsDrag]
 *   fire [VcGui.handleDrag] (and are cancelled so the cursor isn't
 *   consumed - stamp semantics).
 * - Any other configuration (multi-slot top drag, drag onto a non-
 *   accepting slot) is cancelled outright.
 *
 * Permission check: every click against a GUI with a non-null
 * [VcGui.requiredPermission] re-checks the player's permission. If the
 * player no longer holds it (e.g. revoked mid-session), the GUI is closed
 * and a chat hint is sent.
 */
class VcGuiListener : Listener {

    private val mm: MiniMessage = MiniMessage.miniMessage()

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.getHolder(false) as? BaseHolder ?: return
        val gui = holder.gui
        val player = event.whoClicked as? Player ?: return

        // Bulk-deposit / paint-bucket GUIs hand event handling back to vanilla
        // Bukkit - all clicks land, no cancellation, no dispatch. The subclass
        // reads the final inventory in handleClose. Permission is still checked
        // so a freely-editable GUI can't be left open by a de-permed player.
        if (gui.freelyEditable) {
            if (gui.requiredPermission != null && !player.hasPermission(gui.requiredPermission)) {
                event.isCancelled = true
                player.closeInventory()
                player.sendMessage(mm.deserialize("<red>You no longer have permission to use this GUI."))
            }
            return
        }

        if (gui.requiredPermission != null && !player.hasPermission(gui.requiredPermission)) {
            event.isCancelled = true
            player.closeInventory()
            player.sendMessage(mm.deserialize("<red>You no longer have permission to use this GUI."))
            return
        }

        // Cross-inventory actions need careful handling - some are always-cancel
        // (the dup-exploit family), some are only-cancel-when-touching-top.
        when (event.action) {
            InventoryAction.MOVE_TO_OTHER_INVENTORY -> {
                // Shift-click. Always cancelled at the end (it would move items
                // between top and bottom). Special cases:
                //   - Bottom-inv shift-click may be routed to a top-inv item
                //     with acceptsBottomShiftClick (the stamp-add affordance).
                //   - Top-inv shift-click falls through to the normal top-inv
                //     dispatch path so the slot's onClick handler can react
                //     to shift-click (e.g. "shift-click to remove entry").
                //     Previously this branch short-circuited with an early
                //     return, swallowing the click before onClick fired.
                val clickedBottom = event.clickedInventory != null
                    && event.clickedInventory != event.inventory
                if (clickedBottom) {
                    routeBottomShiftClick(gui, player, event)
                    event.isCancelled = true
                    return
                }
                // Top-inv shift-click: fall through to top dispatch below.
                // (The dispatch path also calls event.isCancelled = true.)
            }
            InventoryAction.COLLECT_TO_CURSOR -> {
                // Double-click sweep. ALWAYS cancel, regardless of where the
                // double-click originated. The sweep pulls matching items from
                // BOTH inventories - even a double-click in the bottom inv can
                // suck items out of our top inventory. Don't be tempted to
                // gate this on clickedInventory == top; it would re-open the
                // dup-exploit vector.
                event.isCancelled = true
                return
            }
            InventoryAction.HOTBAR_SWAP,
            InventoryAction.HOTBAR_MOVE_AND_READD -> {
                // Number-key swap with hotbar. Only a dup vector when the
                // hovered slot is in OUR top inventory (it would yank our item
                // into the player's hotbar). A swap entirely within the bottom
                // inventory is safe player inventory management - allow it so
                // the GUI doesn't break number-key rearrangement.
                if (event.clickedInventory == event.inventory) {
                    event.isCancelled = true
                }
                return
            }
            else -> { /* fall through */ }
        }

        val clickedTop = event.clickedInventory == event.inventory
        if (clickedTop) {
            event.isCancelled = true
            val slot = event.slot
            if (slot < 0 || slot >= gui.items().size) return
            val item = gui.items()[slot] ?: return
            val handler = item.onClick ?: return
            handler(ClickContext(
                player = player,
                click = event.click,
                action = event.action,
                slot = slot,
                isBottomInv = false,
                currentItem = event.currentItem?.clone(),
                cursor = event.cursor.clone(),
                event = event,
            ))
            return
        }
        // clickedTop == false -> click in player's bottom inventory with a
        // safe action (PICKUP_*, PLACE_*, SWAP_WITH_CURSOR, DROP_*,
        // CLONE_STACK, NOTHING). Let it through unchanged.
    }

    private fun routeBottomShiftClick(gui: VcGui, player: Player, event: InventoryClickEvent) {
        val stamped = event.currentItem ?: return
        if (stamped.type.isAir) return
        // First top-slot with the flag wins. Most GUIs have at most one
        // "+ Add"-style receiver, so this is unambiguous; if a future GUI
        // needs per-item type routing it can inspect ClickContext and
        // dispatch internally. We pass the RECEIVER's top-inv slot in the
        // ClickContext (not the bottom-inv slot the player clicked on) so
        // the handler can easily repaint itself via gui.update() etc.
        val items = gui.items()
        val receiverSlot = items.indices.firstOrNull { items[it]?.acceptsBottomShiftClick == true } ?: return
        val receiver = items[receiverSlot] ?: return
        val handler = receiver.onClick ?: return
        handler(ClickContext(
            player = player,
            click = event.click,
            action = event.action,
            slot = receiverSlot,
            isBottomInv = true,
            currentItem = stamped.clone(),
            cursor = event.cursor.clone(),
            event = event,
        ))
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onDrag(event: InventoryDragEvent) {
        val holder = event.inventory.getHolder(false) as? BaseHolder ?: return
        val gui = holder.gui
        if (gui.freelyEditable) return  // pass through, see onClick comment
        val topSize = event.inventory.size
        val topSlotsTouched = event.rawSlots.filter { it < topSize }

        if (topSlotsTouched.isEmpty()) {
            // Drag in bottom inventory only - allow.
            return
        }

        if (topSlotsTouched.size == 1) {
            val targetSlot = topSlotsTouched.single()
            val item = gui.items().getOrNull(targetSlot)
            if (item?.acceptsDrag == true) {
                val deposited = event.newItems[targetSlot]
                if (deposited != null && !deposited.type.isAir) {
                    gui.handleDrag(DragContext(
                        player = event.whoClicked as Player,
                        rawSlots = event.rawSlots,
                        newItems = event.newItems,
                        targetSlot = targetSlot,
                        depositedItem = deposited.clone(),
                        event = event,
                    ))
                    // handleDrag decides cancellation. Default-implementation
                    // cancels (stamp semantics); deposit-pattern overrides
                    // leave it uncancelled so the item actually lands.
                    return
                }
            }
        }
        // Any other configuration: dup-guard cancel.
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onClose(event: InventoryCloseEvent) {
        val holder = event.inventory.getHolder(false) as? BaseHolder ?: return
        val player = event.player as? Player ?: return
        holder.gui.handleClose(player)
    }
}

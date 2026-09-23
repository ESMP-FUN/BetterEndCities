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
 * Routes clicks, drags and closes for every [VcGui]. Clicks in the GUI are
 * always cancelled and dispatched; the player's own inventory stays usable,
 * except for the moves that could pull items out of the GUI.
 */
class VcGuiListener : Listener {

    private val mm: MiniMessage = MiniMessage.miniMessage()

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.getHolder(false) as? BaseHolder ?: return
        val gui = holder.gui
        val player = event.whoClicked as? Player ?: return

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

        when (event.action) {
            InventoryAction.MOVE_TO_OTHER_INVENTORY -> {
                // A shift-click from the player's inventory goes to the slot that accepts it, and never moves.
                val clickedBottom = event.clickedInventory != null
                    && event.clickedInventory != event.inventory
                if (clickedBottom) {
                    routeBottomShiftClick(gui, player, event)
                    event.isCancelled = true
                    return
                }
            }
            InventoryAction.COLLECT_TO_CURSOR -> {
                // A double-click sweep pulls from both inventories, wherever it started.
                event.isCancelled = true
                return
            }
            InventoryAction.HOTBAR_SWAP,
            InventoryAction.HOTBAR_MOVE_AND_READD -> {
                if (event.clickedInventory == event.inventory) {
                    event.isCancelled = true
                }
                return
            }
            else -> {}
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
    }

    private fun routeBottomShiftClick(gui: VcGui, player: Player, event: InventoryClickEvent) {
        val stamped = event.currentItem ?: return
        if (stamped.type.isAir) return
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
        if (gui.freelyEditable) return
        val topSize = event.inventory.size
        val topSlotsTouched = event.rawSlots.filter { it < topSize }

        if (topSlotsTouched.isEmpty()) {
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
                    return
                }
            }
        }
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onClose(event: InventoryCloseEvent) {
        val holder = event.inventory.getHolder(false) as? BaseHolder ?: return
        val player = event.player as? Player ?: return
        holder.gui.handleClose(player)
    }
}

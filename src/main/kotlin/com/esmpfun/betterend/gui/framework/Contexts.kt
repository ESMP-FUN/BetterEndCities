package com.esmpfun.betterend.gui.framework

import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.ItemStack

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

data class DragContext(
    val player: Player,
    val rawSlots: Set<Int>,
    val newItems: Map<Int, ItemStack>,
    val targetSlot: Int,
    val depositedItem: ItemStack,
    val event: InventoryDragEvent,
)

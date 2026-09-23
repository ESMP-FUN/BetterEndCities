package com.esmpfun.betterend.listeners

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.models.EndCity
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.TileState
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.persistence.PersistentDataType

/**
 * A block is protected when it lies inside a padded piece box, whatever its
 * type; a material list would be trivial to spoof with purpur. `ship-only`
 * narrows this to the ship and the city's own loot containers.
 */
class ProtectionListener(private val plugin: BetterEnd) : Listener {

    private companion object {
        val CONTAINERS = setOf(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.DISPENSER, Material.DROPPER,
        )
    }

    /** Same key [ContainerLootListener] stamps on player-placed containers. */
    private val playerPlacedKey = NamespacedKey("betterend", "player_placed_container")

    private fun pad() = plugin.config.getInt("protection.piece-padding", 3)
    private fun enabled() = plugin.config.getBoolean("protection.enabled", true)

    private fun shipOnly() = plugin.config.getString("protection.scope", "whole-city").equals("ship-only", ignoreCase = true)
    private fun headTakeable() = plugin.config.getBoolean("protection.dragon-head-takeable", false)

    private fun protectingCity(block: Block): EndCity? {
        val city = plugin.cityManager.getCachedCityInPaddedRegion(block.location, pad()) ?: return null
        if (!city.inStructurePiece(block.location, pad())) return null
        if (!shipOnly()) return city
        return if (city.inShip(block.location, pad()) || isCityContainer(city, block)) city else null
    }

    /** Breaking one would lose its loot for good. */
    private fun isCityContainer(city: EndCity, block: Block): Boolean {
        if (block.type !in CONTAINERS || !city.inStructurePiece(block.location)) return false
        val state = block.state as? TileState ?: return false
        return !state.persistentDataContainer.has(playerPlacedKey, PersistentDataType.BYTE)
    }

    private fun isShipHead(city: EndCity, block: Block): Boolean =
        (block.type == Material.DRAGON_HEAD || block.type == Material.DRAGON_WALL_HEAD) &&
            (city.inShip(block.location, pad()) || city.shipAnchor == null)

    private fun isProtected(block: Block): Boolean = protectingCity(block) != null

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (!enabled() || !plugin.isReady) return
        if (event.player.hasPermission("betterend.bypass.protection")) return
        val city = protectingCity(event.block) ?: return
        if (headTakeable() && isShipHead(city, event.block)) return
        event.isCancelled = true
        notifyDenied(event.player, city, event.block)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHeadTaken(event: BlockBreakEvent) {
        if (!headTakeable() || !plugin.isReady) return
        val type = event.block.type
        if (type != Material.DRAGON_HEAD && type != Material.DRAGON_WALL_HEAD) return
        val city = plugin.cityManager.getCachedCityInPaddedRegion(event.block.location, pad()) ?: return
        if (!isShipHead(city, event.block)) return
        plugin.launchAsync { plugin.cityManager.setHeadTaken(city.id) }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (!enabled() || !plugin.isReady) return
        if (event.player.hasPermission("betterend.bypass.protection")) return
        if (!plugin.config.getBoolean("protection.block-place", true)) return
        val city = protectingCity(event.block) ?: return
        // The placed block is already in the world here, so a new chest would pass as a city one.
        if (shipOnly() && !city.inShip(event.block.location, pad())) return
        event.isCancelled = true
        notifyDenied(event.player, city, event.block)
    }

    // A bucket places water or lava without a BlockPlaceEvent.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBucketEmpty(event: PlayerBucketEmptyEvent) {
        if (!enabled() || !plugin.isReady) return
        if (event.player.hasPermission("betterend.bypass.protection")) return
        if (!plugin.config.getBoolean("protection.block-place", true)) return
        val city = protectingCity(event.block) ?: return
        if (shipOnly() && !city.inShip(event.block.location, pad())) return
        event.isCancelled = true
        notifyDenied(event.player, city, event.block)
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (!enabled() || !plugin.isReady) return
        if (movesProtected(event.blocks, event.direction)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (!enabled() || !plugin.isReady) return
        if (movesProtected(event.blocks, event.direction)) event.isCancelled = true
    }

    // Both neighbours, since a retract event's direction is the piston's facing, not the pull.
    private fun movesProtected(blocks: List<Block>, direction: BlockFace): Boolean = blocks.any {
        isProtected(it) || isProtected(it.getRelative(direction)) || isProtected(it.getRelative(direction.oppositeFace))
    }

    // Withers, and falling sand or anvils landing in the city.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        if (!enabled() || !plugin.isReady) return
        if (event.entity is Player) return
        if (isProtected(event.block)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (!enabled() || !plugin.isReady) return
        if (!plugin.config.getBoolean("protection.block-explosions", true)) return
        event.blockList().removeIf { isProtected(it) }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        if (!enabled() || !plugin.isReady) return
        if (!plugin.config.getBoolean("protection.block-explosions", true)) return
        event.blockList().removeIf { isProtected(it) }
    }

    private fun notifyDenied(player: Player, city: EndCity, block: Block) {
        if (!plugin.config.getBoolean("protection.notify-denied", true)) return
        val text = when {
            !shipOnly() -> "§cThis End City is protected."
            city.inShip(block.location, pad()) -> "§cThe End Ship is protected."
            else -> "§cThe city's loot chests can't be broken."
        }
        player.sendActionBar(net.kyori.adventure.text.Component.text(text))
    }
}

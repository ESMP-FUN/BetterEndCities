package com.esmpfun.betterend.listeners

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.models.EndCity
import io.papermc.paper.event.entity.EntityBreakByEntityEvent
import io.papermc.paper.event.entity.EntityBreakEvent
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.TileState
import org.bukkit.entity.Cushion
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.EntityPlaceEvent
import org.bukkit.persistence.PersistentDataType

/**
 * Griefing protection for registered End Cities — bounds-based per structure
 * piece, NOT palette-based.
 *
 * End cities are built mostly from plain purpur and end stone bricks, so a
 * material allow-list would leave the structure trivially spoofable. Instead
 * a block is protected iff it falls inside a `city_pieces` bounding box
 * (expanded by `protection.piece-padding` to cover edge decoration + a thin
 * shell), regardless of its type. The void *between* the towers stays fully
 * buildable.
 *
 * `protection.scope: ship-only` narrows this to the ship plus the city's own
 * loot containers; towers and bridges are left open (a weekly reset puts them
 * back). With `protection.dragon-head-takeable` the ship's dragon head can be
 * broken once, and resets never restore it.
 *
 * Players with `betterend.bypass.protection` (default op) are exempt.
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

    /** The city protecting [block], or null. Fast region reject, then the
     *  per-piece padded test. */
    private fun protectingCity(block: Block): EndCity? {
        val city = plugin.cityManager.getCachedCityInPaddedRegion(block.location, pad()) ?: return null
        if (!city.inStructurePiece(block.location, pad())) return null
        if (!shipOnly()) return city
        return if (city.inShip(block.location, pad()) || isCityContainer(city, block)) city else null
    }

    /** A loot container that generated with the city. Breaking one would lose its loot for good. */
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

    /** Remembers a taken ship head so the next reset leaves it gone. */
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

    // ── cushions (26.3) ──────────────────────────────────────────────────────

    /**
     * A cushion is an entity, not a block, so none of the block handlers above
     * ever see one — without this a city that can't be built in at all could
     * still be carpeted in cushions.
     */
    private fun protectedCushionAt(location: Location): EndCity? {
        val city = plugin.cityManager.getCachedCityInPaddedRegion(location, pad()) ?: return null
        if (!city.inStructurePiece(location, pad())) return null
        return if (!shipOnly() || city.inShip(location, pad())) city else null
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onCushionPlace(event: EntityPlaceEvent) {
        if (!enabled() || !plugin.isReady) return
        if (event.entity !is Cushion) return
        if (!plugin.config.getBoolean("protection.block-place", true)) return
        val player = event.player
        if (player != null && player.hasPermission("betterend.bypass.protection")) return
        val city = protectedCushionAt(event.entity.location) ?: return
        event.isCancelled = true
        player?.let { notifyDenied(it, city, event.entity.location.block) }
    }

    /**
     * Every way a cushion is removed arrives here: taken by hand, blown up,
     * knocked out by a mob, covered over, or its support block going away.
     *
     * Only the first two are refused. OBSTRUCTION and PHYSICS are the game
     * tidying up after itself, and refusing those would strand a cushion that
     * nothing could then remove.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onCushionBreak(event: EntityBreakEvent) {
        if (!enabled() || !plugin.isReady) return
        if (event.entity !is Cushion) return

        val remover = (event as? EntityBreakByEntityEvent)?.remover
        if (remover is Player && remover.hasPermission("betterend.bypass.protection")) return

        when (event.cause) {
            EntityBreakEvent.RemoveCause.ENTITY -> {}
            EntityBreakEvent.RemoveCause.EXPLOSION ->
                if (!plugin.config.getBoolean("protection.block-explosions", true)) return
            else -> return
        }

        val city = protectedCushionAt(event.entity.location) ?: return
        event.isCancelled = true
        (remover as? Player)?.let { notifyDenied(it, city, event.entity.location.block) }
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

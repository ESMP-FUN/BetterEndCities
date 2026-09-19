package com.esmpfun.betterend.listeners

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.models.EndCity
import io.papermc.paper.event.entity.EntityBreakByEntityEvent
import io.papermc.paper.event.entity.EntityBreakEvent
import org.bukkit.Location
import org.bukkit.block.Block
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
 * Players with `betterend.bypass.protection` (default op) are exempt.
 */
class ProtectionListener(private val plugin: BetterEnd) : Listener {

    private fun pad() = plugin.config.getInt("protection.piece-padding", 3)
    private fun enabled() = plugin.config.getBoolean("protection.enabled", true)

    /** The city protecting [block], or null. Fast region reject, then the
     *  per-piece padded test. */
    private fun protectingCity(block: Block): EndCity? {
        val city = plugin.cityManager.getCachedCityInPaddedRegion(block.location, pad()) ?: return null
        return if (city.inStructurePiece(block.location, pad())) city else null
    }

    private fun isProtected(block: Block): Boolean = protectingCity(block) != null

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (!enabled() || !plugin.isReady) return
        if (event.player.hasPermission("betterend.bypass.protection")) return
        protectingCity(event.block) ?: return
        event.isCancelled = true
        notifyDenied(event.player)
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (!enabled() || !plugin.isReady) return
        if (event.player.hasPermission("betterend.bypass.protection")) return
        if (!plugin.config.getBoolean("protection.block-place", true)) return
        protectingCity(event.block) ?: return
        event.isCancelled = true
        notifyDenied(event.player)
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
    private fun protectedCushionAt(location: Location): Boolean {
        val city = plugin.cityManager.getCachedCityInPaddedRegion(location, pad()) ?: return false
        return city.inStructurePiece(location, pad())
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onCushionPlace(event: EntityPlaceEvent) {
        if (!enabled() || !plugin.isReady) return
        if (event.entity !is Cushion) return
        if (!plugin.config.getBoolean("protection.block-place", true)) return
        val player = event.player
        if (player != null && player.hasPermission("betterend.bypass.protection")) return
        if (!protectedCushionAt(event.entity.location)) return
        event.isCancelled = true
        player?.let { notifyDenied(it) }
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

        if (!protectedCushionAt(event.entity.location)) return
        event.isCancelled = true
        (remover as? Player)?.let { notifyDenied(it) }
    }

    private fun notifyDenied(player: Player) {
        if (!plugin.config.getBoolean("protection.notify-denied", true)) return
        player.sendActionBar(net.kyori.adventure.text.Component.text("§cThis End City is protected."))
    }
}

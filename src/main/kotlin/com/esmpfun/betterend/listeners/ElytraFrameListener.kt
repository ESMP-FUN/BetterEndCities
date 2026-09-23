package com.esmpfun.betterend.listeners

import com.esmpfun.betterend.BetterEnd
import com.esmpfun.betterend.utils.AntiDupeCompat
import com.esmpfun.betterend.utils.StructureUtil
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Display
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.hanging.HangingBreakEvent
import org.bukkit.event.hanging.HangingPlaceEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.generator.structure.Structure
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * Renewable elytras, the item-frame way: the ship's elytra **item frame stays
 * an item frame**. Punching it (the vanilla pick-up interaction) puts a fresh
 * elytra straight into the puncher's inventory while the frame - elytra and
 * all - stays put for the next player. No vault block, no datapack, nothing
 * to relearn.
 *
 * - **Identification**: when a chunk's entities load in the End, any item
 *   frame displaying an elytra inside an END_CITY structure is PDC-tagged as
 *   a ship frame. Player-placed frames are tagged at place time and never
 *   converted, so builds inside a city are safe.
 * - **Claiming**: [PlayerItemFrameChangeEvent] (REMOVE) is cancelled - the
 *   frame never loses its elytra - and the claim rules run instead: claim
 *   mode (per-ship / per-refresh / global), then the optional cost item is
 *   consumed, then a fresh elytra is handed over.
 * - **AntiDupe compat**: the handed-out elytra is pre-stamped with the
 *   claimer's AntiDupePro ownership tag (no-op when ADP isn't installed), so
 *   ADP sees a normally-owned item instead of an untracked pickup.
 * - **Protection**: the frame can't be broken or emptied by non-players
 *   while the feature is enabled.
 * - **Hint**: an optional floating text above the frame shows the cost (or
 *   "Punch to claim" when free).
 * - **Price**: XP levels and/or an item, optionally doubling with each claim the
 *   player made within one loot refresh. Right-clicking the frame shows it.
 */
class ElytraFrameListener(private val plugin: BetterEnd) : Listener {

    companion object {
        private fun frameTag(plugin: BetterEnd) = NamespacedKey(plugin, "ship_elytra_frame")
        private fun playerPlacedTag(plugin: BetterEnd) = NamespacedKey(plugin, "player_placed_frame")
        private fun displayTag(plugin: BetterEnd) = NamespacedKey(plugin, "elytra_frame_text")

        /** The floating hint for the current config: cost line or claim line. */
        private fun hintText(plugin: BetterEnd): Component {
            val cost = plugin.elytraClaimManager.costStack()
            val levels = plugin.config.getInt("elytra.cost.levels", 0)
            if (cost == null && levels <= 0) return Component.text("Punch to claim your Elytra", NamedTextColor.GRAY)
            var text = Component.text("Elytra costs ", NamedTextColor.GRAY)
            if (levels > 0) text = text.append(Component.text("$levels levels", NamedTextColor.AQUA))
            if (levels > 0 && cost != null) text = text.append(Component.text(" and ", NamedTextColor.GRAY))
            if (cost != null) {
                text = text.append(Component.text("${cost.amount} x ", NamedTextColor.AQUA))
                    .append(cost.effectiveName().color(NamedTextColor.AQUA))
            }
            return text
        }

        /**
         * Push the current config onto every already-loaded hint display (text
         * refresh, or removal when the hint/feature is off) and spawn missing
         * hints above loaded ship frames. Called on save from the dialog and
         * the cost picker; unloaded ships catch up as their entities load.
         */
        fun refreshLoaded(plugin: BetterEnd) {
            val fTag = frameTag(plugin)
            val dTag = displayTag(plugin)
            val wantHint = plugin.config.getBoolean("elytra.enabled", true) &&
                plugin.config.getBoolean("elytra.text-display", true)
            if (plugin.scheduler.isFolia) {
                // Folia has no thread that may scan a whole world, so each
                // tracked frame updates its own hint on its own region.
                for (frame in loadedShipFrames) {
                    plugin.scheduler.runAtEntity(frame, Runnable {
                        val hints = frame.world.getNearbyEntitiesByType(TextDisplay::class.java, hintLocation(frame), 1.5) {
                            it.persistentDataContainer.has(dTag, PersistentDataType.BYTE)
                        }
                        when {
                            !wantHint -> hints.forEach { it.remove() }
                            hints.isEmpty() -> spawnHintIfMissing(plugin, frame)
                            else -> hints.forEach { it.text(hintText(plugin)) }
                        }
                    })
                }
                return
            }
            for (world in plugin.server.worlds) {
                if (world.environment != World.Environment.THE_END) continue
                for (display in world.getEntitiesByClass(TextDisplay::class.java)) {
                    if (!display.persistentDataContainer.has(dTag, PersistentDataType.BYTE)) continue
                    if (wantHint) display.text(hintText(plugin)) else display.remove()
                }
                if (!wantHint) continue
                for (frame in world.getEntitiesByClass(ItemFrame::class.java)) {
                    if (!frame.persistentDataContainer.has(fTag, PersistentDataType.BYTE)) continue
                    spawnHintIfMissing(plugin, frame)
                }
            }
        }

        private fun spawnHintIfMissing(plugin: BetterEnd, frame: ItemFrame) {
            val dTag = displayTag(plugin)
            val loc = hintLocation(frame)
            val world = frame.world
            val existing = world.getNearbyEntitiesByType(TextDisplay::class.java, loc, 1.5) {
                it.persistentDataContainer.has(dTag, PersistentDataType.BYTE)
            }
            if (existing.isNotEmpty()) return
            world.spawn(loc, TextDisplay::class.java) { display ->
                display.text(hintText(plugin))
                display.billboard = Display.Billboard.CENTER
                display.isSeeThrough = false
                display.persistentDataContainer.set(dTag, PersistentDataType.BYTE, 1)
            }
        }

        private fun hintLocation(frame: ItemFrame): Location =
            frame.location.clone().add(0.0, 0.9, 0.0)

        private val loadedShipFrames: MutableSet<ItemFrame> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    }

    private val frameTag = frameTag(plugin)
    private val playerPlacedTag = playerPlacedTag(plugin)

    private fun enabled() = plugin.config.getBoolean("elytra.enabled", true)
    private fun textDisplay() = plugin.config.getBoolean("elytra.text-display", true)

    // ── identification ───────────────────────────────────────────────────────

    /** Tag ship frames (and keep hint displays current) as chunk entities load. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onEntitiesLoad(event: EntitiesLoadEvent) {
        if (!enabled()) return
        if (event.world.environment != World.Environment.THE_END) return
        for (entity in event.entities) {
            if (entity is TextDisplay &&
                entity.persistentDataContainer.has(displayTag(plugin), PersistentDataType.BYTE)
            ) {
                // Re-render existing hints with the CURRENT config, so a cost
                // change reaches every ship as its entities load back in.
                if (textDisplay()) entity.text(hintText(plugin)) else entity.remove()
                continue
            }
            val frame = entity as? ItemFrame ?: continue
            if (!isShipFrame(frame)) continue
            loadedShipFrames.add(frame)
            rememberShip(frame)
            if (textDisplay()) spawnHintIfMissing(plugin, frame)
        }
    }

    // ── aura ─────────────────────────────────────────────────────────────────

    init {
        // Frames already loaded before the plugin started never fire EntitiesLoadEvent.
        if (!plugin.scheduler.isFolia) {
            for (world in plugin.server.worlds) {
                if (world.environment != World.Environment.THE_END) continue
                world.getEntitiesByClass(ItemFrame::class.java)
                    .filterTo(loadedShipFrames) { it.persistentDataContainer.has(frameTag, PersistentDataType.BYTE) }
            }
        }
        plugin.scheduler.runTaskTimer(Runnable { auraTick() }, 40L, 10L)
    }

    /** A sparse, eerie shimmer round each ship frame with a player close by, so players notice it. */
    private fun auraTick() {
        // Pruned even with the aura off: every chunk reload adds a fresh frame object.
        loadedShipFrames.removeIf { !it.isValid }
        if (!enabled() || !plugin.config.getBoolean("elytra.frame-aura", false)) return
        for (frame in loadedShipFrames) {
            plugin.scheduler.runAtEntity(frame, Runnable {
                val loc = frame.location.toCenterLocation()
                val world = frame.world
                if (world.getNearbyPlayers(loc, 24.0).isEmpty()) return@Runnable
                val random = java.util.concurrent.ThreadLocalRandom.current()
                world.spawnParticle(org.bukkit.Particle.REVERSE_PORTAL, loc, 6, 0.35, 0.35, 0.35, 0.02)
                if (random.nextInt(100) < 35) {
                    world.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, loc, 2, 0.25, 0.25, 0.25, 0.005)
                }
                if (random.nextInt(100) < 4) {
                    world.playSound(loc, Sound.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS, 0.5f, 0.55f)
                }
            })
        }
    }

    /** Player-placed frames are tagged so they always keep vanilla behaviour. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHangingPlace(event: HangingPlaceEvent) {
        val frame = event.entity as? ItemFrame ?: return
        if (frame.world.environment != World.Environment.THE_END) return
        frame.persistentDataContainer.set(playerPlacedTag, PersistentDataType.BYTE, 1)
    }

    /**
     * Whether [frame] is a ship's elytra frame - cheap tag check first, then a
     * one-time structure test that stamps the tag for next time.
     */
    private fun isShipFrame(frame: ItemFrame): Boolean {
        if (frame.persistentDataContainer.has(frameTag, PersistentDataType.BYTE)) return true
        if (frame.persistentDataContainer.has(playerPlacedTag, PersistentDataType.BYTE)) return false
        if (frame.item.type != Material.ELYTRA) return false
        if (frame.world.environment != World.Environment.THE_END) return false
        // Only ship frames - never player-built elytra displays elsewhere.
        // (Frames placed by players inside the city are caught by the
        // player-placed tag above.)
        if (!StructureUtil.isNear(frame.location, Structure.END_CITY, 8.0)) return false
        frame.persistentDataContainer.set(frameTag, PersistentDataType.BYTE, 1)
        // A ship elytra frame is proof of a ship - flag the city (belt &
        // braces beside the snapshot capture's dragon-head fingerprint, e.g.
        // when snapshot.auto-capture is off).
        plugin.cityManager.getCachedCityAt(frame.location)?.takeIf { !it.hasShip }?.let { city ->
            plugin.launchAsync { plugin.cityManager.setHasShip(city.id, true) }
        }
        return true
    }

    // ── claiming ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onFrameChange(event: PlayerItemFrameChangeEvent) {
        if (!enabled() || !plugin.isReady) return
        val frame = event.itemFrame
        if (!isShipFrame(frame)) return

        // The frame's elytra NEVER leaves - everything below hands out copies.
        // A right-click would spin the elytra, so it shows the price instead.
        event.isCancelled = true
        val player = event.player

        val city = plugin.cityManager.getCachedCityAt(frame.location)
        if (city == null) {
            // Discovery registers the city asynchronously moments after its
            // chunks first load; a punch can only lose that race right after
            // generation.
            player.sendActionBar(Component.text("This ship is still being registered. Try again in a moment.", NamedTextColor.YELLOW))
            return
        }
        rememberShip(frame)

        if (plugin.elytraClaimManager.hasClaimed(city.id, player.uniqueId)) {
            val msg = when (plugin.elytraClaimManager.mode()) {
                com.esmpfun.betterend.managers.ElytraClaimManager.ClaimMode.PER_SHIP ->
                    "You've already claimed this ship's elytra."
                com.esmpfun.betterend.managers.ElytraClaimManager.ClaimMode.PER_REFRESH ->
                    "Already claimed. You can claim here again after this city's loot refreshes."
                com.esmpfun.betterend.managers.ElytraClaimManager.ClaimMode.GLOBAL ->
                    "You've already claimed your elytra."
            }
            player.sendActionBar(Component.text(msg, NamedTextColor.RED))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f)
            return
        }

        val price = plugin.elytraClaimManager.priceFor(player.uniqueId)
        if (event.action == PlayerItemFrameChangeEvent.ItemFrameChangeAction.ROTATE) {
            player.sendMessage(priceMessage(price))
            return
        }
        if (event.action != PlayerItemFrameChangeEvent.ItemFrameChangeAction.REMOVE) return

        val item = price.item
        val missingItems = item != null && price.items > 0 && !player.inventory.containsAtLeast(item, price.items)
        if (player.level < price.levels || missingItems) {
            player.sendMessage(priceMessage(price))
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f)
            return
        }
        if (price.levels > 0) player.giveExpLevels(-price.levels)
        if (item != null) {
            var left = price.items
            while (left > 0) {
                val take = minOf(left, item.maxStackSize)
                player.inventory.removeItem(item.clone().apply { amount = take })
                left -= take
            }
        }

        val elytra = ItemStack(Material.ELYTRA)
        // AntiDupePro tracks ELYTRA: pre-stamp the claimer as owner so ADP sees
        // a normally-owned pickup. No-op when ADP isn't installed.
        AntiDupeCompat.tagOwner(elytra, player.uniqueId)
        giveOrDrop(player, elytra)

        plugin.elytraClaimManager.record(city.id, player.uniqueId)
        player.sendActionBar(Component.text("Elytra claimed. Happy flying!", NamedTextColor.GREEN))
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f)
        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[Elytra] ${player.name} claimed at city #${city.id} (${frame.location.blockX},${frame.location.blockY},${frame.location.blockZ})")
        }
    }

    /** "This elytra costs 20 levels and 10 x Netherite Scrap", plus how the doubling works. */
    private fun priceMessage(price: com.esmpfun.betterend.managers.ElytraClaimManager.Price): Component {
        val parts = buildList {
            if (price.levels > 0) add(Component.text("${price.levels} levels", NamedTextColor.AQUA))
            val item = price.item
            if (item != null && price.items > 0) {
                add(Component.text("${price.items} x ", NamedTextColor.AQUA).append(item.effectiveName().color(NamedTextColor.AQUA)))
            }
        }
        var line = Component.text("This elytra ", NamedTextColor.GRAY)
        line = if (parts.isEmpty()) line.append(Component.text("is free. Punch the frame to take it.", NamedTextColor.GRAY))
        else {
            line = line.append(Component.text("costs ", NamedTextColor.GRAY)).append(parts[0])
            if (parts.size > 1) line = line.append(Component.text(" and ", NamedTextColor.GRAY)).append(parts[1])
            line.append(Component.text(". Punch the frame to buy it.", NamedTextColor.GRAY))
        }
        if (parts.isNotEmpty() && plugin.config.getBoolean("elytra.cost.double-each-claim", false)) {
            val hours = plugin.config.getInt("loot.refresh-hours", 12)
            val span = when {
                hours <= 0 -> null
                hours % 24 == 0 -> (hours / 24).let { if (it == 1) "a day" else "$it days" }
                else -> if (hours == 1) "an hour" else "$hours hours"
            }
            val rule = if (span == null) " Every elytra you buy doubles the price of the next one."
            else " Every elytra you buy doubles the price of the next one, until $span after you bought it."
            line = line.append(Component.text(rule, NamedTextColor.DARK_GRAY))
        }
        return line
    }

    /** Lets protection pick the ship out of the city's pieces. */
    private fun rememberShip(frame: ItemFrame) {
        val city = plugin.cityManager.getCachedCityAt(frame.location) ?: return
        if (city.shipAnchor != null) return
        val b = frame.location.block
        plugin.launchAsync { plugin.cityManager.setShipAnchor(city.id, b.x, b.y, b.z) }
    }

    private fun giveOrDrop(player: Player, item: ItemStack) {
        val leftover = player.inventory.addItem(item)
        for (rest in leftover.values) {
            player.world.dropItemNaturally(player.location, rest)
        }
    }

    // ── frame protection ─────────────────────────────────────────────────────

    /** Ship frames can't be broken (explosions, obstruction, non-player causes). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onHangingBreak(event: HangingBreakEvent) {
        if (!enabled()) return
        val frame = event.entity as? ItemFrame ?: return
        if (isShipFrame(frame)) event.isCancelled = true
    }

    /**
     * Non-player damage (skeleton arrows, dispenser projectiles, ...) would pop
     * the elytra with no claim flow - block it. Player punches pass through so
     * [onFrameChange] can run the claim.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onFrameDamage(event: EntityDamageByEntityEvent) {
        if (!enabled()) return
        val frame = event.entity as? ItemFrame ?: return
        if (event.damager is Player) return
        if (isShipFrame(frame)) event.isCancelled = true
    }
}

package com.esmpfun.betterend.setup

import com.esmpfun.betterend.BetterEnd
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * One gentle nudge per session: while the setup tour has never been completed
 * (or skipped), admins get a clickable hint on join. Finishing OR skipping
 * the tour sets `setup.completed` and this goes quiet forever.
 */
class SetupReminderListener(private val plugin: BetterEnd) : Listener {

    private val remindedThisSession = ConcurrentHashMap.newKeySet<UUID>()

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (plugin.config.getBoolean("setup.completed", false)) return
        val player = event.player
        if (!player.hasPermission("betterend.admin")) return
        if (!remindedThisSession.add(player.uniqueId)) return

        // Delayed a moment so it lands after the join-message noise.
        plugin.scheduler.runAtEntityLater(player, Runnable {
            if (!player.isOnline || plugin.config.getBoolean("setup.completed", false)) return@Runnable
            player.sendMessage(
                MiniMessage.miniMessage().deserialize(
                    "<light_purple>[BetterEndCities]</light_purple> <gray>First time? Take the 2-minute " +
                        "<click:run_command:'/betterend setup'><hover:show_text:'<gray>Opens the guided setup'>" +
                        "<green>[setup tour]</green></hover></click><gray> — or ignore this; the defaults already work."
                )
            )
        }, 60L)
    }
}

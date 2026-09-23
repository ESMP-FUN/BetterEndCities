package com.esmpfun.betterend

import com.esmpfun.betterend.commands.BeCommand
import com.esmpfun.betterend.database.DatabaseManager
import com.esmpfun.betterend.gui.framework.VcGuiListener
import com.esmpfun.betterend.integrations.MetricsService
import com.esmpfun.betterend.listeners.CityDiscoveryListener
import com.esmpfun.betterend.listeners.ContainerLootListener
import com.esmpfun.betterend.listeners.ElytraFrameListener
import com.esmpfun.betterend.listeners.ProtectionListener
import com.esmpfun.betterend.managers.CityDiscoveryManager
import com.esmpfun.betterend.managers.CityManager
import com.esmpfun.betterend.managers.ContainerLootManager
import com.esmpfun.betterend.managers.ElytraClaimManager
import com.esmpfun.betterend.managers.SnapshotManager
import com.esmpfun.betterend.scheduler.SchedulerAdapter
import com.esmpfun.betterend.setup.SetupReminderListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * BetterEnd - turns the End into renewable, multiplayer-friendly content:
 * per-player elytras straight from the ship's item frame (no vault block, no
 * datapack), per-player End City loot, grief protection, and snapshot-based
 * structure resets.
 *
 * Standalone plugin. Reuses the proven BetterAncientCities architecture
 * (async-first init, [SchedulerAdapter] Paper/Folia abstraction, per-player
 * container loot, gzip snapshots) by port, not by dependency, plus Mantle's
 * MC26 dialog config approach.
 */
class BetterEnd : JavaPlugin() {

    /** Flips true once async init completes; gates command/listener execution. */
    @Volatile
    var isReady: Boolean = false
        private set

    /** Paper/Folia scheduler abstraction. */
    lateinit var scheduler: SchedulerAdapter
        private set

    lateinit var databaseManager: DatabaseManager
        private set

    lateinit var cityManager: CityManager
        private set

    lateinit var discoveryManager: CityDiscoveryManager
        private set

    lateinit var containerLootManager: ContainerLootManager
        private set

    lateinit var elytraClaimManager: ElytraClaimManager
        private set

    lateinit var snapshotManager: SnapshotManager
        private set

    private var lootListener: ContainerLootListener? = null

    /** Directory holding per-city snapshot files. */
    val snapshotsDir: File by lazy { File(dataFolder, "snapshots").apply { mkdirs() } }

    // Plugin-wide coroutine scope (SupervisorJob so one failed job doesn't tear
    // down the rest). Cancelled in onDisable.
    private val pluginJob = SupervisorJob()

    // Catches anything escaping a launchAsync block: logged, and reported to
    // FastStats when error reporting is on. An uncaught background failure is
    // exactly the kind of bug nobody files a ticket for.
    private val coroutineErrorHandler = CoroutineExceptionHandler { _, t ->
        if (t !is CancellationException) {
            logger.log(java.util.logging.Level.SEVERE, "Uncaught error in a background task", t)
            MetricsService.reportHandled(t, "coroutine")
        }
    }
    val pluginScope = CoroutineScope(Dispatchers.Default + pluginJob + coroutineErrorHandler)

    /** Launch an async coroutine on the plugin scope. */
    fun launchAsync(block: suspend CoroutineScope.() -> Unit): Job =
        pluginScope.launch(block = block)

    override fun onEnable() {
        saveDefaultConfig()
        scheduler = SchedulerAdapter.create(this)

        logger.info("Better End Cities starting on ${if (scheduler.isFolia) "Folia" else "Paper"}...")

        // Async-first init: heavy setup (DB, caches, discovery sweep) runs off
        // the main thread; listeners register on the main thread once ready.
        databaseManager = DatabaseManager(this)
        cityManager = CityManager(this)
        discoveryManager = CityDiscoveryManager(this)
        containerLootManager = ContainerLootManager(this)
        elytraClaimManager = ElytraClaimManager(this)
        snapshotManager = SnapshotManager(this)

        // Commands MUST be registered synchronously inside onEnable: Paper backs
        // registerCommand with a lifecycle event handler, and the lifecycle
        // manager stops accepting handlers the moment enable returns. Doing this
        // from a scheduled task throws "Cannot register lifecycle event handlers"
        // and, because it aborts the rest of that task, leaves isReady false -
        // which silently disables every listener too. Registered after the
        // managers above so tab-completion always has them; BeCommand guards on
        // isReady for anything that needs the database.
        @Suppress("UnstableApiUsage")
        registerCommand("betterend", "Better End Cities admin command & config menu", BeCommand(this))

        launchAsync {
            try {
                databaseManager.initialize()
                cityManager.preload()
                elytraClaimManager.preload()
                scheduler.runTask(Runnable {
                    server.pluginManager.registerEvents(CityDiscoveryListener(this@BetterEnd), this@BetterEnd)
                    val loot = ContainerLootListener(this@BetterEnd)
                    lootListener = loot
                    server.pluginManager.registerEvents(loot, this@BetterEnd)
                    server.pluginManager.registerEvents(ProtectionListener(this@BetterEnd), this@BetterEnd)
                    server.pluginManager.registerEvents(ElytraFrameListener(this@BetterEnd), this@BetterEnd)
                    server.pluginManager.registerEvents(SetupReminderListener(this@BetterEnd), this@BetterEnd)
                    // Central GUI dispatcher - routes only BaseHolder inventories.
                    server.pluginManager.registerEvents(VcGuiListener(), this@BetterEnd)

                    // Core setup is done - flip the flag BEFORE the optional
                    // integrations below. Every listener guards on isReady, so a
                    // throw from an add-on must never leave the plugin inert.
                    isReady = true
                    logger.info("Better End Cities ready.")

                    // Update checking (PluginPulse). Config in pluginpulse.yml;
                    // server owners can override mode/interval via an `update:`
                    // block in config.yml.
                    runCatching {
                        io.github.darkstarworks.pluginpulse.PluginPulse.bootstrap(this@BetterEnd)
                    }.onFailure { logger.warning("Update checking unavailable: ${it.message}") }

                    // Anonymous usage metrics (FastStats). Opt-out via metrics.enabled
                    // in config.yml or the global plugins/FastStats/config.yml.
                    logger.info("FastStats Metrics: ${MetricsService.init(this@BetterEnd)}")
                    // Catch cities in chunks already resident at enable (the live
                    // ChunkLoadEvent covers everything loaded afterward).
                    discoveryManager.startupSweep()
                })
            } catch (e: Exception) {
                logger.severe("Better End Cities failed to initialize: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    override fun onDisable() {
        isReady = false
        // Before the pool closes: loot inventories still open, or closed but not yet written.
        runCatching { lootListener?.saveOpenOnShutdown() }
            .onFailure { logger.warning("Could not save open loot inventories: ${it.message}") }
        io.github.darkstarworks.pluginpulse.PluginPulse.shutdown(this)
        MetricsService.shutdown()
        scheduler.cancelAllTasks()
        pluginScope.cancel()
        if (::databaseManager.isInitialized) databaseManager.close()
        logger.info("Better End Cities disabled.")
    }
}

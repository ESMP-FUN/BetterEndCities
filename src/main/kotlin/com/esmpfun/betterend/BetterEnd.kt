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
import com.esmpfun.betterend.messages.Messages
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

/** Renewable End Cities: per-player elytras and loot, protection and resets. */
class BetterEnd : JavaPlugin() {

    /** True once async init completes; commands and listeners wait for it. */
    @Volatile
    var isReady: Boolean = false
        private set

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

    lateinit var messages: Messages
        private set

    private var lootListener: ContainerLootListener? = null

    val snapshotsDir: File by lazy { File(dataFolder, "snapshots").apply { mkdirs() } }

    private val pluginJob = SupervisorJob()

    // Uncaught background failures are logged and reported; nobody files a ticket for them.
    private val coroutineErrorHandler = CoroutineExceptionHandler { _, t ->
        if (t !is CancellationException) {
            logger.log(java.util.logging.Level.SEVERE, "Uncaught error in a background task", t)
            MetricsService.reportHandled(t, "coroutine")
        }
    }
    val pluginScope = CoroutineScope(Dispatchers.Default + pluginJob + coroutineErrorHandler)

    fun launchAsync(block: suspend CoroutineScope.() -> Unit): Job =
        pluginScope.launch(block = block)

    override fun onEnable() {
        saveDefaultConfig()
        messages = Messages(this).apply { load() }
        scheduler = SchedulerAdapter.create(this)

        logger.info("Better End Cities starting on ${if (scheduler.isFolia) "Folia" else "Paper"}...")

        databaseManager = DatabaseManager(this)
        cityManager = CityManager(this)
        discoveryManager = CityDiscoveryManager(this)
        containerLootManager = ContainerLootManager(this)
        elytraClaimManager = ElytraClaimManager(this)
        snapshotManager = SnapshotManager(this)

        // Must happen inside onEnable: Paper stops accepting lifecycle handlers
        // once enable returns, and a throw from a later task would leave isReady false.
        @Suppress("UnstableApiUsage")
        registerCommand("betterend", messages.raw("command.description"), BeCommand(this))

        launchAsync {
            try {
                databaseManager.initialize()
                cityManager.preload()
                elytraClaimManager.preload()
                containerLootManager.purgeEmptyChestTemplatesOnce()
                scheduler.runTask(Runnable {
                    server.pluginManager.registerEvents(CityDiscoveryListener(this@BetterEnd), this@BetterEnd)
                    val loot = ContainerLootListener(this@BetterEnd)
                    lootListener = loot
                    server.pluginManager.registerEvents(loot, this@BetterEnd)
                    server.pluginManager.registerEvents(ProtectionListener(this@BetterEnd), this@BetterEnd)
                    server.pluginManager.registerEvents(ElytraFrameListener(this@BetterEnd), this@BetterEnd)
                    server.pluginManager.registerEvents(SetupReminderListener(this@BetterEnd), this@BetterEnd)
                    server.pluginManager.registerEvents(VcGuiListener(this@BetterEnd), this@BetterEnd)

                    // Set before the optional integrations, so a throw from one can't leave the plugin inert.
                    isReady = true
                    logger.info("Better End Cities ready.")

                    runCatching {
                        io.github.darkstarworks.pluginpulse.PluginPulse.bootstrap(this@BetterEnd)
                    }.onFailure { logger.warning("Update checking unavailable: ${it.message}") }

                    logger.info("FastStats Metrics: ${MetricsService.init(this@BetterEnd)}")
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

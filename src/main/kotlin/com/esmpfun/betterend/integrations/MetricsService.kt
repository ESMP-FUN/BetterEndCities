package com.esmpfun.betterend.integrations

import com.esmpfun.betterend.BetterEnd
import dev.faststats.Attributes
import dev.faststats.ErrorTracker
import dev.faststats.Metrics
import dev.faststats.bukkit.BukkitContext
import dev.faststats.data.Metric

/**
 * FastStats usage metrics and error reports. Either `metrics.enabled` or the
 * global FastStats switch turns both off; suppliers read in-memory state only.
 */
object MetricsService {

    // Blank disables metrics entirely.
    private const val PROJECT_TOKEN: String = "0f6a0acd2f476c81fd3241d567a8c546"

    @Volatile
    private var context: BukkitContext? = null

    @Volatile
    private var errorTracker: ErrorTracker? = null

    fun init(plugin: BetterEnd): String {
        if (PROJECT_TOKEN.isBlank()) return "Disabled (no project token)"
        if (!plugin.config.getBoolean("metrics.enabled", true)) return "Disabled (config)"
        // A second context would mean a second submission scheduler.
        if (context != null) return "Enabled"

        val tracker = if (plugin.config.getBoolean("metrics.error-reporting", true)) {
            buildErrorTracker(plugin)
        } else null

        return try {
            val ctx = BukkitContext.Factory(plugin, PROJECT_TOKEN)
                .also { f -> tracker?.let { f.errorTrackerService(it) } }
                .metrics { factory -> registerMetrics(plugin, factory) }
                .create()

            // Must run on the main thread during enable; the caller is inside scheduler.runTask.
            ctx.ready()
            context = ctx
            errorTracker = tracker
            "Enabled"
        } catch (e: Exception) {
            plugin.logger.warning("FastStats init failed: ${e.message}")
            "Failed"
        }
    }

    private fun registerMetrics(plugin: BetterEnd, factory: Metrics.Factory): Metrics =
        factory
            .addMetric(Metric.string("database_type") {
                plugin.databaseManager.databaseType.toString().lowercase()
            })
            .addMetric(Metric.string("elytra_claim_mode") {
                plugin.config.getString("elytra.claim-mode", "per-ship")
            })
            .addMetric(Metric.bool("per_player_loot") {
                plugin.config.getBoolean("loot.enabled", true)
            })
            .addMetric(Metric.string("city_count") {
                cityCountBucket(plugin.cityManager.all().size)
            })
            .create()

    /** Reports a caught error. [context] is sent as-is: counts and fixed labels only, never names or coordinates. */
    fun reportHandled(t: Throwable, operation: String, vararg context: Pair<String, Any?>) {
        val tracker = errorTracker ?: return
        if (t is java.util.concurrent.CancellationException) return
        runCatching {
            val attrs = Attributes.empty().put("operation", operation)
            for ((k, v) in context) {
                when (v) {
                    null -> {}
                    is Number -> attrs.put(k, v)
                    is Boolean -> attrs.put(k, v)
                    else -> attrs.put(k, v.toString())
                }
            }
            tracker.trackError(t).attributes(attrs).handled(true)
        }
    }

    // Uncaught errors are only captured when thrown from our class loader. The SDK
    // matches ignored types exactly, so kotlinx's JobCancellationException needs the
    // message pattern. On top of the SDK's own redaction: query-string credentials
    // and player UUIDs.
    private fun buildErrorTracker(plugin: BetterEnd): ErrorTracker {
        val tracker = ErrorTracker.contextAware(MetricsService::class.java.classLoader)
            .ignoreError(java.util.concurrent.CancellationException::class.java)
            .ignoreError("(?i).*\\b(?:job|coroutine)\\b.*\\bcancell?ed\\b.*")
            .anonymize("(?i)([?&;](?:user|username|password|pass|pwd)=)[^&;\\s\"']*", "$1[hidden]")
            .anonymize(
                "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b",
                "[uuid hidden]"
            )

        runCatching {
            tracker.attributes
                .put("be_version", plugin.pluginMeta.version)
                .put("mc_version", plugin.server.minecraftVersion)
                .put("database", plugin.databaseManager.databaseType.toString().lowercase())
                .put("folia", plugin.scheduler.isFolia)
                .put("cities", cityCountBucket(plugin.cityManager.all().size))
        }
        return tracker
    }

    /** Coarse fleet band. Shared by the metric and the error context. */
    private fun cityCountBucket(n: Int): String = when (n) {
        0 -> "0"
        in 1..5 -> "1-5"
        in 6..20 -> "6-20"
        in 21..50 -> "21-50"
        in 51..100 -> "51-100"
        else -> "100+"
    }

    /** Flushes and stops the submission scheduler. Safe to call when init never ran. */
    fun shutdown() {
        val ctx = context ?: return
        context = null
        errorTracker = null
        runCatching { ctx.shutdown() }
    }
}

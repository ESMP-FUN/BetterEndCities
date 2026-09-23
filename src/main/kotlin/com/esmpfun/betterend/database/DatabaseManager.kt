package com.esmpfun.betterend.database

import com.esmpfun.betterend.BetterEnd
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.configuration.file.FileConfiguration
import java.io.File
import java.sql.Connection

/**
 * HikariCP-pooled database access. SQLite (default) or MySQL.
 *
 * Same pool tuning as BetterAncientCities (WAL + concurrent readers for
 * SQLite), same dual-dialect approach. Schema is BetterEnd's own:
 * `cities` + `city_pieces` + per-player loot + elytra claims.
 */
class DatabaseManager(private val plugin: BetterEnd) {

    private lateinit var dataSource: HikariDataSource
    private var _databaseType: DatabaseType = DatabaseType.SQLITE

    val databaseType: DatabaseType get() = _databaseType

    enum class DatabaseType { SQLITE, MYSQL }

    /** A pooled connection. Always use inside `.use { }`. */
    val connection: Connection get() = dataSource.connection

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val config = plugin.config
        _databaseType = try {
            DatabaseType.valueOf(config.getString("database.type", "SQLITE")!!.uppercase())
        } catch (_: IllegalArgumentException) {
            plugin.logger.warning("Invalid database.type, defaulting to SQLITE")
            DatabaseType.SQLITE
        }

        dataSource = when (_databaseType) {
            DatabaseType.SQLITE -> createSQLiteDataSource()
            DatabaseType.MYSQL -> createMySQLDataSource(config)
        }
        plugin.logger.info("Database pool initialized (${_databaseType.name})")
        createTables()
    }

    private fun createSQLiteDataSource(): HikariDataSource {
        val dbFile = File(plugin.dataFolder, "database.db")
        plugin.dataFolder.mkdirs()
        return HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}?foreign_keys=on"
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 5
            minimumIdle = 1
            connectionTimeout = 30000
            idleTimeout = 300000
            maxLifetime = 600000
            connectionTestQuery = "SELECT 1"
            poolName = "BetterEndCities-SQLite"
            leakDetectionThreshold = 10000
            addDataSourceProperty("journal_mode", "WAL")
            addDataSourceProperty("synchronous", "NORMAL")
            addDataSourceProperty("busy_timeout", "5000")
        })
    }

    private fun createMySQLDataSource(config: FileConfiguration): HikariDataSource {
        val host = config.getString("database.mysql.host", "localhost")!!
        val port = config.getInt("database.mysql.port", 3306)
        val database = config.getString("database.mysql.database", "betterend")!!
        val username = config.getString("database.mysql.username", "root")!!
        val password = config.getString("database.mysql.password", "")!!
        return HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC"
            driverClassName = "com.mysql.cj.jdbc.Driver"
            this.username = username
            this.password = password
            maximumPoolSize = config.getInt("database.mysql.pool-size", 10)
            connectionTestQuery = "SELECT 1"
            poolName = "BetterEndCities-MySQL"
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("useServerPrepStmts", "true")
        })
    }

    private suspend fun createTables() = withContext(Dispatchers.IO) {
        val autoId = if (_databaseType == DatabaseType.SQLITE)
            "INTEGER PRIMARY KEY AUTOINCREMENT" else "INT AUTO_INCREMENT PRIMARY KEY"
        connection.use { conn ->
            conn.createStatement().use { stmt ->
                // Registered End Cities. (origin_x/y/z) = the structure
                // bounding-box min corner — the stable dedup identity across
                // every chunk of the city; the min/max_* region bounds are the
                // union of the pieces.
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS cities (
                        id $autoId,
                        world VARCHAR(64) NOT NULL,
                        min_x INT NOT NULL, min_y INT NOT NULL, min_z INT NOT NULL,
                        max_x INT NOT NULL, max_y INT NOT NULL, max_z INT NOT NULL,
                        origin_x INT NOT NULL, origin_y INT NOT NULL, origin_z INT NOT NULL,
                        created_at BIGINT NOT NULL,
                        last_reset BIGINT,
                        snapshot_file VARCHAR(255),
                        loot_cycle_start BIGINT,
                        has_ship INT NOT NULL DEFAULT 0,
                        ship_x INT, ship_y INT, ship_z INT,
                        head_taken INT NOT NULL DEFAULT 0,
                        UNIQUE (world, origin_x, origin_y, origin_z)
                    )
                    """.trimIndent()
                )
                // Per-piece bounding boxes — exact provenance bounds (towers,
                // bridges, and the ship when one generated).
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS city_pieces (
                        id $autoId,
                        city_id INT NOT NULL,
                        min_x INT NOT NULL, min_y INT NOT NULL, min_z INT NOT NULL,
                        max_x INT NOT NULL, max_y INT NOT NULL, max_z INT NOT NULL,
                        FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_city_pieces_city ON city_pieces(city_id)")

                // Per-player private container copies (Lootr-style). One row per
                // (city, container position, player). Cleared per city on reset.
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS player_container_loot (
                        city_id INT NOT NULL,
                        x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        contents TEXT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (city_id, x, y, z, player_uuid),
                        FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                // Shared per-container template: the canonical contents every
                // first-open copy is cloned from (materialized by rolling the
                // vanilla loot table). PERSISTS across resets so op edits stick.
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS container_template (
                        city_id INT NOT NULL,
                        x INT NOT NULL, y INT NOT NULL, z INT NOT NULL,
                        contents TEXT NOT NULL,
                        material VARCHAR(64) NOT NULL,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (city_id, x, y, z),
                        FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                // Elytra claims — one row per (city, player). claimed_at lets
                // the per-refresh claim mode compare against the city's loot
                // cycle start instead of needing explicit deletes.
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS elytra_claims (
                        city_id INT NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        claimed_at BIGINT NOT NULL,
                        PRIMARY KEY (city_id, player_uuid),
                        FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_elytra_claims_player ON elytra_claims(player_uuid)")
            }
            // Columns added after 0.3.0. CREATE TABLE above covers new databases.
            val existing = HashSet<String>()
            conn.metaData.getColumns(conn.catalog, null, "cities", null).use { rs ->
                while (rs.next()) existing.add(rs.getString("COLUMN_NAME").lowercase())
            }
            conn.createStatement().use { stmt ->
                for ((column, type) in listOf(
                    "ship_x" to "INT", "ship_y" to "INT", "ship_z" to "INT",
                    "head_taken" to "INT NOT NULL DEFAULT 0",
                )) {
                    if (column !in existing) stmt.execute("ALTER TABLE cities ADD COLUMN $column $type")
                }
            }
        }
    }

    fun close() {
        if (::dataSource.isInitialized && !dataSource.isClosed) {
            dataSource.close()
            plugin.logger.info("Database pool closed")
        }
    }
}

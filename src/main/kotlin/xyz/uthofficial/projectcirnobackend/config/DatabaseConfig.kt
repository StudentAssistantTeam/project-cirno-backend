package xyz.uthofficial.projectcirnobackend.config

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import xyz.uthofficial.projectcirnobackend.entity.Users

/**
 * Auto-creates missing Exposed tables on startup.
 * For production, replace with Flyway or Liquibase migrations.
 */
@Configuration
class DatabaseConfig {

    /**
     * Runs SchemaUtils.createMissingTablesAndColumns for the Users table
     * after the application context is fully initialized.
     */
    @Bean
    fun initDatabase(): ApplicationRunner = ApplicationRunner {
        transaction {
            arrayOf<Table>(Users)
        }
    }
}

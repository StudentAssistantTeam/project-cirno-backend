package xyz.uthofficial.projectcirnobackend.config

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import xyz.uthofficial.projectcirnobackend.entity.Users

/**
 * Auto-creates missing Exposed tables on startup.
 * For production, replace with Flyway or Liquibase migrations.
 */
@Configuration
class AppDatabaseConfig {

    /**
     * Runs SchemaUtils.createMissingTablesAndColumns for the Users table
     * after the application context is fully initialized.
     */
    @Bean
    fun initDatabase(): ApplicationRunner = ApplicationRunner {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Users)
        }
    }
}

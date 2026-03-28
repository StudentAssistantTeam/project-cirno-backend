package xyz.uthofficial.projectcirnobackend.config

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import xyz.uthofficial.projectcirnobackend.entity.Events
import xyz.uthofficial.projectcirnobackend.entity.EventTags
import xyz.uthofficial.projectcirnobackend.entity.Tags
import xyz.uthofficial.projectcirnobackend.entity.Users

/**
 * Auto-creates missing Exposed tables on startup.
 * For production, replace with Flyway or Liquibase migrations.
 */
@Configuration
class AppDatabaseConfig {

    /**
     * Runs SchemaUtils.createMissingTablesAndColumns for all entity tables
     * after the application context is fully initialized.
     */
    @Bean
    fun initDatabase(): ApplicationRunner = ApplicationRunner {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Users, Events, Tags, EventTags)
        }
    }
}

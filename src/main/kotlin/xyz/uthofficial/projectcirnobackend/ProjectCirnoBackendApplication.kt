package xyz.uthofficial.projectcirnobackend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring Boot entry point. Scans xyz.uthofficial.projectcirnobackend and sub-packages.
 */
@SpringBootApplication
class ProjectCirnoBackendApplication

fun main(args: Array<String>) {
    runApplication<ProjectCirnoBackendApplication>(*args)
}

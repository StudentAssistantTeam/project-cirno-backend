package xyz.uthofficial.projectcirnobackend

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<ProjectCirnoBackendApplication>().with(TestcontainersConfiguration::class).run(*args)
}

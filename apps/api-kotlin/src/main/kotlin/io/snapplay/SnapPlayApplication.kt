package io.snapplay

import io.snapplay.config.SnapPlayProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(SnapPlayProperties::class)
@EnableScheduling
class SnapPlayApplication

fun main(args: Array<String>) {
    runApplication<SnapPlayApplication>(*args)
}

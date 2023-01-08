package io.johnsonlee.springboot.starter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["io.johnsonlee.*"])
class StarterApplication

fun main(args: Array<String>) {
	runApplication<StarterApplication>(*args)
}

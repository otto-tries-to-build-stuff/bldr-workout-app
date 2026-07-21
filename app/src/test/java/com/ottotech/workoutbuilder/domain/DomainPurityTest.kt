package com.ottotech.workoutbuilder.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Enforces the architectural boundary: the domain package must stay free of Android
 * dependencies so it runs on the JVM in milliseconds without a device or emulator.
 *
 * Gradle runs unit tests with the module directory as the working directory.
 */
class DomainPurityTest {

    @Test
    fun `domain package contains no android imports`() {
        val dir = File("src/main/java/com/ottotech/workoutbuilder/domain")
        assertTrue(dir.isDirectory, "domain source directory not found at ${dir.absolutePath}")

        val offenders = dir.walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .filter { it.trimStart().startsWith("import android") }
                    .map { "${file.name}: ${it.trim()}" }
            }
            .toList()

        assertTrue(
            offenders.isEmpty(),
            "domain/ must stay Android-free, found:\n${offenders.joinToString("\n")}",
        )
    }
}

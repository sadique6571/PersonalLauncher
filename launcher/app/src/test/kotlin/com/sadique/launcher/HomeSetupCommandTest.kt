package com.sadique.launcher

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Test

class HomeSetupCommandTest {
    companion object {
        private fun write(path: Path, text: String) {
            Files.write(path, text.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private class Rig : java.io.Closeable {
        val dir: Path = Files.createTempDirectory("home-setup")
        val bin: Path = Files.createDirectory(dir.resolve("bin"))

        init {
            HomeSetupCommandTest.write(dir.resolve("home"), "com.google.android.apps.tv.launcherx/.Home\n")
            HomeSetupCommandTest.write(dir.resolve("services"), "org.example/.OtherService\n")
            script("pm", "printf '%s\\n' \"$*\" >> \"\$RIG/pm.log\"\n[ \"\$FAIL_GRANT\" != 1 ]\n")
            script("settings", "printf '%s\\n' \"$*\" >> \"\$RIG/settings.log\"\n[ \"$1\" != --user ] || shift 2\ncase \"$1 $2 $3\" in\n'get secure enabled_accessibility_services') [ \"\$FAIL_READ\" != 1 ] || exit 1; cat \"\$RIG/services\" ;;\n'put secure enabled_accessibility_services') printf '%s' \"$4\" > \"\$RIG/services\" ;;\n'put secure accessibility_enabled') printf '%s' \"$4\" > \"\$RIG/accessibility\" ;;\n*) exit 1 ;; esac\n")
            script("cmd", "printf '%s\\n' \"$*\" >> \"\$RIG/cmd.log\"\ncase \"$1 $2\" in\n'package resolve-activity') cat \"\$RIG/home\" ;;\n'package set-home-activity') if [ \"\$KEEP_OTHER_HOME\" != 1 ]; then for value in \"$@\"; do target=\$value; done; printf '%s\\n' \"\$target\" > \"\$RIG/home\"; fi; printf 'Success\\n' ;;\n'appops set') exit 0 ;;\n*) exit 1 ;; esac\n")
        }

        fun script(name: String, body: String) {
            val file = bin.resolve(name)
            HomeSetupCommandTest.write(file, "#!/bin/sh\n" + body)
            assertTrue(file.toFile().setExecutable(true))
        }

        fun run(sdk: Int, user: Int, vararg env: String): String {
            val builder = ProcessBuilder("/bin/sh", "-c", HomeSetupCommand.build("com.sadique.launcher", user, sdk))
            builder.environment()["PATH"] = bin.toString() + File.pathSeparator + System.getenv("PATH")
            builder.environment()["RIG"] = dir.toString()
            for (i in env.indices step 2) builder.environment()[env[i]] = env[i + 1]
            val child = builder.redirectErrorStream(true).start()
            child.outputStream.close()
            val output = String(child.inputStream.readAllBytes(), StandardCharsets.UTF_8)
            assertTrue(child.waitFor(5, TimeUnit.SECONDS))
            return output
        }

        fun read(name: String): String {
            val file = dir.resolve(name)
            return if (Files.exists(file)) String(Files.readAllBytes(file), StandardCharsets.UTF_8) else ""
        }

        override fun close() {
            val paths = Files.walk(dir)
            try {
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            } finally {
                paths.close()
            }
        }
    }

    @Test fun setsAndVerifiesHomeAndPreservesOtherAccessibilityServices() {
        Assume.assumeTrue(File("/bin/sh").canExecute())
        Rig().use { rig ->
            assertTrue(rig.run(34, 0).contains(HomeSetupCommand.MARKER))
            val services = rig.read("services")
            assertTrue(services.startsWith("org.example/.OtherService:"))
            assertTrue(services.contains("com.sadique.launcher/com.sadique.launcher.HomeKeyService"))
            assertTrue(services.contains("com.sadique.launcher/com.sadique.launcher.InputKeyService"))
            assertTrue(rig.read("cmd.log").contains("package set-home-activity --user 0"))
            assertEquals("1", rig.read("accessibility"))
        }
    }

    @Test fun alreadyConfiguredHomeDoesNotDuplicateOrRewriteHelpers() {
        Assume.assumeTrue(File("/bin/sh").canExecute())
        Rig().use { rig ->
            val services = "org.example/.OtherService:com.sadique.launcher/.HomeKeyService:com.sadique.launcher/com.sadique.launcher.InputKeyService"
            write(rig.dir.resolve("services"), services)
            write(rig.dir.resolve("home"), "com.sadique.launcher/.MainActivity\n")
            assertTrue(rig.run(34, 0).contains(HomeSetupCommand.MARKER))
            assertEquals(services, rig.read("services"))
            assertFalse(rig.read("settings.log").contains("put secure enabled_accessibility_services"))
            assertFalse(rig.read("cmd.log").contains("set-home-activity"))
        }
    }

    @Test fun successTextWithoutResolverConfirmationRemainsFailure() {
        Assume.assumeTrue(File("/bin/sh").canExecute())
        Rig().use { rig ->
            val output = rig.run(34, 0, "KEEP_OTHER_HOME", "1")
            assertTrue(output.contains(HomeSetupCommand.FAILED))
            assertFalse(output.contains(HomeSetupCommand.MARKER))
        }
    }

    @Test fun grantRefusalDoesNotProceedToSettingsOrHomeChanges() {
        Assume.assumeTrue(File("/bin/sh").canExecute())
        Rig().use { rig ->
            assertTrue(rig.run(34, 0, "FAIL_GRANT", "1").contains(HomeSetupCommand.FAILED))
            assertEquals("", rig.read("cmd.log"))
            assertEquals("", rig.read("settings.log"))
        }
    }

    @Test fun nullServiceListBootstrapsOnlyOurHelpers() {
        Assume.assumeTrue(File("/bin/sh").canExecute())
        Rig().use { rig ->
            write(rig.dir.resolve("services"), "null\n")
            assertTrue(rig.run(34, 0).contains(HomeSetupCommand.MARKER))
            assertFalse(rig.read("services").contains("null"))
            assertEquals(2, rig.read("services").split(":").size)
        }
    }

    @Test fun unreadableServiceListDoesNotOverwriteOtherHelpers() {
        Assume.assumeTrue(File("/bin/sh").canExecute())
        Rig().use { rig ->
            val original = rig.read("services")
            assertTrue(rig.run(34, 0, "FAIL_READ", "1").contains(HomeSetupCommand.FAILED))
            assertEquals(original, rig.read("services"))
            assertFalse(rig.read("cmd.log").contains("set-home-activity"))
        }
    }

    @Test fun android11SkipsNewerAppOpAndUsesRequestedUser() {
        Assume.assumeTrue(File("/bin/sh").canExecute())
        Rig().use { rig ->
            assertTrue(rig.run(30, 10).contains(HomeSetupCommand.MARKER))
            assertFalse(rig.read("cmd.log").contains("ACCESS_RESTRICTED_SETTINGS"))
            assertTrue(rig.read("cmd.log").contains("set-home-activity --user 10"))
            assertTrue(rig.read("settings.log").contains("--user 10"))
        }
    }

    @Test fun injectedTargetsAndInvalidUsersAreRejected() {
        for (pkg in arrayOf("com.bad;id", "com.bad$(id)", "com.bad/Other", "")) {
            try {
                HomeSetupCommand.build(pkg, 0, 34)
                fail(pkg)
            } catch (expected: IllegalArgumentException) {
            }
        }
        try {
            HomeSetupCommand.build("com.sadique.launcher", -1, 34)
            fail()
        } catch (expected: IllegalArgumentException) {
        }
    }
}

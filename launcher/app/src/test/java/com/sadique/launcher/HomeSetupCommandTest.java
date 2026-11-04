package com.sadique.launcher;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class HomeSetupCommandTest {
    private static void write(Path path,String text) throws IOException { Files.write(path,text.getBytes(StandardCharsets.UTF_8)); }
    private static final class Rig implements AutoCloseable {
        final Path dir=Files.createTempDirectory("home-setup"),bin=Files.createDirectory(dir.resolve("bin"));
        Rig() throws Exception {
            write(dir.resolve("home"),"com.google.android.apps.tv.launcherx/.Home\n");
            write(dir.resolve("services"),"org.example/.OtherService\n");
            script("pm","printf '%s\\n' \"$*\" >> \"$RIG/pm.log\"\n[ \"$FAIL_GRANT\" != 1 ]\n");
            script("settings","printf '%s\\n' \"$*\" >> \"$RIG/settings.log\"\n[ \"$1\" != --user ] || shift 2\ncase \"$1 $2 $3\" in\n'get secure enabled_accessibility_services') [ \"$FAIL_READ\" != 1 ] || exit 1; cat \"$RIG/services\" ;;\n'put secure enabled_accessibility_services') printf '%s' \"$4\" > \"$RIG/services\" ;;\n'put secure accessibility_enabled') printf '%s' \"$4\" > \"$RIG/accessibility\" ;;\n*) exit 1 ;; esac\n");
            script("cmd","printf '%s\\n' \"$*\" >> \"$RIG/cmd.log\"\ncase \"$1 $2\" in\n'package resolve-activity') cat \"$RIG/home\" ;;\n'package set-home-activity') if [ \"$KEEP_OTHER_HOME\" != 1 ]; then for value in \"$@\"; do target=$value; done; printf '%s\\n' \"$target\" > \"$RIG/home\"; fi; printf 'Success\\n' ;;\n'appops set') exit 0 ;;\n*) exit 1 ;; esac\n");
        }
        void script(String name,String body) throws Exception {
            Path file=bin.resolve(name); write(file,"#!/bin/sh\n"+body); assertTrue(file.toFile().setExecutable(true));
        }
        String run(int sdk,int user,String... env) throws Exception {
            ProcessBuilder builder=new ProcessBuilder("/bin/sh","-c",HomeSetupCommand.build("com.sadique.launcher",user,sdk));
            builder.environment().put("PATH",bin+File.pathSeparator+System.getenv("PATH")); builder.environment().put("RIG",dir.toString());
            for(int i=0;i<env.length;i+=2) builder.environment().put(env[i],env[i+1]);
            Process child=builder.redirectErrorStream(true).start(); child.getOutputStream().close();
            String output=new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            assertTrue(child.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)); return output;
        }
        String read(String name) throws Exception { Path file=dir.resolve(name); return Files.exists(file)?new String(Files.readAllBytes(file),StandardCharsets.UTF_8):""; }
        @Override public void close() throws Exception {
            try(java.util.stream.Stream<Path> paths=Files.walk(dir)) {
                for(Path file:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(file);
            }
        }
    }
    @Test public void setsAndVerifiesHomeAndPreservesOtherAccessibilityServices() throws Exception {
        org.junit.Assume.assumeTrue(new File("/bin/sh").canExecute());
        try(Rig rig=new Rig()) {
            assertTrue(rig.run(34,0).contains(HomeSetupCommand.MARKER));
            String services=rig.read("services");
            assertTrue(services.startsWith("org.example/.OtherService:"));
            assertTrue(services.contains("com.sadique.launcher/com.sadique.launcher.HomeKeyService"));
            assertTrue(services.contains("com.sadique.launcher/com.sadique.launcher.InputKeyService"));
            assertTrue(rig.read("cmd.log").contains("package set-home-activity --user 0"));
            assertEquals("1",rig.read("accessibility"));
        }
    }
    @Test public void alreadyConfiguredHomeDoesNotDuplicateOrRewriteHelpers() throws Exception {
        org.junit.Assume.assumeTrue(new File("/bin/sh").canExecute());
        try(Rig rig=new Rig()) {
            String services="org.example/.OtherService:com.sadique.launcher/.HomeKeyService:com.sadique.launcher/com.sadique.launcher.InputKeyService";
            write(rig.dir.resolve("services"),services);
            write(rig.dir.resolve("home"),"com.sadique.launcher/.MainActivity\n");
            assertTrue(rig.run(34,0).contains(HomeSetupCommand.MARKER));
            assertEquals(services,rig.read("services"));
            assertFalse(rig.read("settings.log").contains("put secure enabled_accessibility_services"));
            assertFalse(rig.read("cmd.log").contains("set-home-activity"));
        }
    }
    @Test public void successTextWithoutResolverConfirmationRemainsFailure() throws Exception {
        org.junit.Assume.assumeTrue(new File("/bin/sh").canExecute());
        try(Rig rig=new Rig()) {
            String output=rig.run(34,0,"KEEP_OTHER_HOME","1");
            assertTrue(output.contains(HomeSetupCommand.FAILED)); assertFalse(output.contains(HomeSetupCommand.MARKER));
        }
    }
    @Test public void grantRefusalDoesNotProceedToSettingsOrHomeChanges() throws Exception {
        org.junit.Assume.assumeTrue(new File("/bin/sh").canExecute());
        try(Rig rig=new Rig()) {
            assertTrue(rig.run(34,0,"FAIL_GRANT","1").contains(HomeSetupCommand.FAILED));
            assertEquals("",rig.read("cmd.log")); assertEquals("",rig.read("settings.log"));
        }
    }
    @Test public void nullServiceListBootstrapsOnlyOurHelpers() throws Exception {
        org.junit.Assume.assumeTrue(new File("/bin/sh").canExecute());
        try(Rig rig=new Rig()) {
            write(rig.dir.resolve("services"),"null\n");
            assertTrue(rig.run(34,0).contains(HomeSetupCommand.MARKER));
            assertFalse(rig.read("services").contains("null"));
            assertEquals(2,rig.read("services").split(":").length);
        }
    }
    @Test public void unreadableServiceListDoesNotOverwriteOtherHelpers() throws Exception {
        org.junit.Assume.assumeTrue(new File("/bin/sh").canExecute());
        try(Rig rig=new Rig()) {
            String original=rig.read("services");
            assertTrue(rig.run(34,0,"FAIL_READ","1").contains(HomeSetupCommand.FAILED));
            assertEquals(original,rig.read("services"));
            assertFalse(rig.read("cmd.log").contains("set-home-activity"));
        }
    }
    @Test public void android11SkipsNewerAppOpAndUsesRequestedUser() throws Exception {
        org.junit.Assume.assumeTrue(new File("/bin/sh").canExecute());
        try(Rig rig=new Rig()) {
            assertTrue(rig.run(30,10).contains(HomeSetupCommand.MARKER));
            assertFalse(rig.read("cmd.log").contains("ACCESS_RESTRICTED_SETTINGS"));
            assertTrue(rig.read("cmd.log").contains("set-home-activity --user 10"));
            assertTrue(rig.read("settings.log").contains("--user 10"));
        }
    }
    @Test public void injectedTargetsAndInvalidUsersAreRejected() {
        for(String pkg:new String[]{"com.bad;id","com.bad$(id)","com.bad/Other",""}) {
            try { HomeSetupCommand.build(pkg,0,34); fail(pkg); } catch(IllegalArgumentException expected) { }
        }
        try { HomeSetupCommand.build("com.sadique.launcher",-1,34); fail(); } catch(IllegalArgumentException expected) { }
    }
}

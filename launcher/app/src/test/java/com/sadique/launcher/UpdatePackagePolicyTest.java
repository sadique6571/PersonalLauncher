package com.sadique.launcher;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class UpdatePackagePolicyTest {
    private final Set<String> signed=new HashSet<>(Arrays.asList("publisher-certificate"));
    @Test public void onlyStrictlyNewerSamePackageSameSignerIsAccepted() {
        assertTrue(UpdatePackagePolicy.accepts("com.sadique.launcher","com.sadique.launcher",49,50,signed,signed));
        assertFalse(UpdatePackagePolicy.accepts("com.sadique.launcher","com.other.app",49,50,signed,signed));
        assertFalse(UpdatePackagePolicy.accepts("com.sadique.launcher","com.sadique.launcher",49,49,signed,signed));
        assertFalse(UpdatePackagePolicy.accepts("com.sadique.launcher","com.sadique.launcher",49,48,signed,signed));
    }
    @Test public void unsignedForeignAndAdditionalSignersAreRejected() {
        Set<String> other=new HashSet<>(Arrays.asList("another-certificate"));
        Set<String> extra=new HashSet<>(signed); extra.add("extra");
        assertFalse(UpdatePackagePolicy.accepts("pkg","pkg",1,2,signed,other));
        assertFalse(UpdatePackagePolicy.accepts("pkg","pkg",1,2,signed,extra));
        assertFalse(UpdatePackagePolicy.accepts("pkg","pkg",1,2,Collections.emptySet(),Collections.emptySet()));
    }
    @Test public void shellTargetsRejectInjectedPackagesHashesAndUsers() {
        String hash=new String(new char[64]).replace((char)0,'a');
        for(String pkg:new String[]{"com.bad;id","com.bad$(id)","com.bad/path"}) {
            try { UpdateInstallCommand.build(pkg,0,hash); fail(); } catch(IllegalArgumentException expected) { }
        }
        try { UpdateInstallCommand.build("com.sadique.launcher",-1,hash); fail(); } catch(IllegalArgumentException expected) { }
        try { UpdateInstallCommand.build("com.sadique.launcher",0,"abc;id"); fail(); } catch(IllegalArgumentException expected) { }
    }
}

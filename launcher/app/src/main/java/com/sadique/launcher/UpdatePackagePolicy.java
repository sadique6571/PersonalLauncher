package com.sadique.launcher;

import java.util.Set;

/** The release label is not a version: signed APK metadata decides whether installation is allowed. */
final class UpdatePackagePolicy {
    private UpdatePackagePolicy() { }
    static boolean accepts(String installedPackage,String archivePackage,long installedCode,long archiveCode,
            Set<String> installedSigners,Set<String> archiveSigners) {
        return installedPackage!=null && installedPackage.equals(archivePackage)
                && archiveCode>installedCode && installedCode>=0
                && installedSigners!=null && !installedSigners.isEmpty() && installedSigners.equals(archiveSigners);
    }
}

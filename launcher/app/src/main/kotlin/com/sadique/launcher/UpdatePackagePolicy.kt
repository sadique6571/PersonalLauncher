package com.sadique.launcher

/** The release label is not a version: signed APK metadata decides whether installation is allowed. */
object UpdatePackagePolicy {

    @JvmStatic
    fun accepts(
        installedPackage: String?, archivePackage: String?, installedCode: Long, archiveCode: Long,
        installedSigners: Set<String>?, archiveSigners: Set<String>?
    ): Boolean {
        return installedPackage != null && installedPackage == archivePackage &&
                archiveCode > installedCode && installedCode >= 0 &&
                installedSigners != null && installedSigners.isNotEmpty() && installedSigners == archiveSigners
    }
}

package com.sadique.launcher

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.Collator
import java.util.Collections
import java.util.HashSet
import java.util.TreeSet
import java.util.function.IntPredicate

/** Home shortcut choices made from the library, separate from theme defaults and tile ordering. */
internal class LibraryApps private constructor() {
    companion object {
        const val MAX_ADDED = 128
        private const val RECENT_MS = 7L * 24 * 60 * 60 * 1000
        private const val MAX_BYTES = MAX_ADDED * 256

        private fun store(context: Context, removed: Boolean): AtomicFile {
            return AtomicFile(File(context.filesDir, if (removed) "library_home_removed.txt" else "library_apps.txt"))
        }

        @JvmStatic
        fun load(context: Context): Set<String> {
            return read(store(context, false))
        }

        @JvmStatic
        fun loadRemoved(context: Context): Set<String> = read(store(context, true))

        private fun read(file: AtomicFile): Set<String> {
            return try {
                readStrict(file)
            } catch (e: IOException) {
                Log.w("TvHome.Library", "Could not read shortcuts", e)
                Collections.emptySet()
            } catch (e: RuntimeException) {
                Log.w("TvHome.Library", "Could not read shortcuts", e)
                Collections.emptySet()
            }
        }

        private fun readStrict(file: AtomicFile): Set<String> {
            if (!file.baseFile.exists() && !File(file.baseFile.toString() + ".bak").exists())
                return Collections.emptySet()
            return file.openRead().use { input ->
                val bytes = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    if (bytes.size() + count > MAX_BYTES) throw IOException("Shortcut file too large")
                    bytes.write(buffer, 0, count)
                }
                decode(String(bytes.toByteArray(), StandardCharsets.UTF_8))
            }
        }

        @JvmStatic
        fun save(context: Context, pkg: String, onHome: Boolean): Boolean {
            val additions: Set<String>
            val removed: Set<String>
            try {
                additions = readStrict(store(context, false))
                removed = readStrict(store(context, true))
            } catch (e: IOException) {
                // Never replace unreadable owner choices with an empty list on the next edit.
                Log.w("TvHome.Library", "Keeping unreadable shortcut choices unchanged", e)
                return false
            } catch (e: RuntimeException) {
                // Never replace unreadable owner choices with an empty list on the next edit.
                Log.w("TvHome.Library", "Keeping unreadable shortcut choices unchanged", e)
                return false
            }
            if (onHome) {
                if (!write(store(context, false), additions, pkg, true)) return false
                return !removed.contains(pkg) || write(store(context, true), removed, pkg, false)
            }
            // The removal takes precedence over theme defaults and any previous explicit addition.
            if (!write(store(context, true), removed, pkg, true)) return false
            if (additions.contains(pkg)) write(store(context, false), additions, pkg, false)
            return true
        }

        private fun write(file: AtomicFile, saved: Set<String>, pkg: String, added: Boolean): Boolean {
            var output: FileOutputStream? = null
            try {
                val next = change(saved, pkg, added)
                val bytes = encode(next).toByteArray(StandardCharsets.UTF_8)
                output = file.startWrite()
                output.write(bytes)
                file.finishWrite(output)
                return true
            } catch (e: IOException) {
                if (output != null) file.failWrite(output)
                Log.w("TvHome.Library", "Could not save shortcut choice", e)
                return false
            } catch (e: RuntimeException) {
                if (output != null) file.failWrite(output)
                Log.w("TvHome.Library", "Could not save shortcut choice", e)
                return false
            }
        }

        @JvmStatic
        fun visible(tvApp: Boolean, themeHidden: Boolean, explicitlyAdded: Boolean): Boolean {
            return visible(tvApp, themeHidden, explicitlyAdded, false)
        }

        @JvmStatic
        fun visible(tvApp: Boolean, themeHidden: Boolean, explicitlyAdded: Boolean, removedFromHome: Boolean): Boolean {
            return !removedFromHome && (explicitlyAdded || (tvApp && !themeHidden))
        }

        /** The pill after the last Home row: show_library_pill false hides it only while the top-bar button is a way in. */
        @JvmStatic
        fun showPill(library: Boolean, button: Boolean, pill: Boolean): Boolean {
            return library && (pill || !button)
        }

        /** A package on library_excluded_apps (config.xml): never offered in the catalogue, unlike hidden_apps,
         *  which only keeps a package off Home while the catalogue still lists it. */
        @JvmStatic
        fun excludedFromCatalog(excludedApps: Set<String>, pkg: String): Boolean {
            return excludedApps.contains(pkg)
        }

        /** OK on a library card opens the app (true) or changes its Home shortcut. app_library_selection's OK always
         *  changes it; otherwise an app on Home opens, and with library_ok_opens every app does. */
        @JvmStatic
        fun okOpens(selection: Boolean, openAll: Boolean, onHome: Boolean): Boolean {
            return !selection && (openAll || onHome)
        }

        @JvmStatic
        fun recent(installedAt: Long, now: Long): Boolean {
            return installedAt > 0 && installedAt <= now && now - installedAt <= RECENT_MS
        }

        /** Missing shortcuts first; recent installs lead that group. The caller sorts ties by label. */
        @JvmStatic
        fun comparePriority(homeA: Boolean, installedA: Long, homeB: Boolean, installedB: Long, now: Long): Int {
            if (homeA != homeB) return if (homeA) 1 else -1
            if (homeA) return 0
            val recentA = recent(installedA, now)
            val recentB = recent(installedB, now)
            if (recentA != recentB) return if (recentA) -1 else 1
            return if (recentA) installedB.compareTo(installedA) else 0
        }

        /** After adding a shortcut, prefer another missing app; otherwise stay by a neighbour. */
        @JvmStatic
        fun nextFocus(current: Int, count: Int, missing: IntPredicate): Int {
            if (count <= 0) return -1
            val at = Math.max(0, Math.min(current, count - 1))
            for (step in 1 until count) {
                val candidate = (at + step) % count
                if (missing.test(candidate)) return candidate
            }
            return if (at + 1 < count) at + 1 else Math.max(0, at - 1)
        }

        /** Checked Home apps first, then apps hidden from Home; the caller sorts each group by label. */
        @JvmStatic
        fun compareHomeGroup(homeA: Boolean, homeB: Boolean): Int {
            return homeB.compareTo(homeA)
        }

        /** Keep the next neighbour when a shortcut moves away, or the previous one at the end. */
        @JvmStatic
        fun nextAfterRemoval(current: Int, count: Int): Int {
            if (count <= 1) return -1
            val at = Math.max(0, Math.min(current, count - 1))
            return if (at + 1 < count) at + 1 else at - 1
        }

        @JvmStatic
        fun compareCatalog(
            homeA: Boolean, labelA: String, packageA: String,
            homeB: Boolean, labelB: String, packageB: String, collator: Collator
        ): Int {
            val group = compareHomeGroup(homeA, homeB)
            if (group != 0) return group
            val label = collator.compare(labelA, labelB)
            return if (label != 0) label else packageA.compareTo(packageB)
        }

        @JvmStatic
        fun change(saved: Set<String>, pkg: String, added: Boolean): Set<String> {
            if (!validPackage(pkg)) throw IllegalArgumentException("Invalid package")
            val next: MutableSet<String> = HashSet(saved)
            if (added) next.add(pkg) else next.remove(pkg)
            if (next.size > MAX_ADDED) throw IllegalArgumentException("Too many shortcuts")
            return next
        }

        @JvmStatic
        fun encode(added: Set<String>): String {
            if (added.size > MAX_ADDED) throw IllegalArgumentException("Too many shortcuts")
            val text = StringBuilder()
            for (pkg in TreeSet(added)) {
                if (!validPackage(pkg)) throw IllegalArgumentException("Invalid package")
                text.append(pkg).append('\n')
            }
            return text.toString()
        }

        @JvmStatic
        fun decode(text: String): Set<String> {
            if (text.length > MAX_BYTES) throw IllegalArgumentException("Shortcut file too large")
            val result: MutableSet<String> = HashSet()
            var start = 0
            for (i in 0..text.length) {
                if (i < text.length && text[i] != '\n') continue
                if (i == start && i == text.length) break
                val pkg = text.substring(start, i)
                if (!validPackage(pkg) || !result.add(pkg) || result.size > MAX_ADDED)
                    throw IllegalArgumentException("Invalid shortcut file")
                start = i + 1
            }
            return result
        }

        @JvmStatic
        fun validPackage(pkg: String?): Boolean {
            if (pkg == null || pkg.isEmpty() || pkg.length > 255) return false
            var first = true
            for (i in 0 until pkg.length) {
                val c = pkg[i]
                if (c == '.') {
                    if (first) return false
                    first = true
                } else {
                    val letter = c in 'a'..'z' || c in 'A'..'Z' || c == '_'
                    if (!letter && (first || c < '0' || c > '9')) return false
                    first = false
                }
            }
            return !first
        }
    }
}

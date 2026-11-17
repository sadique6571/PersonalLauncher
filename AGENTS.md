# Working on Personal Launcher

This repository builds the Android TV launcher `com.sadique.launcher`. The Android project is in `launcher/`. Kotlin application code, Android resources and JVM tests live under `launcher/app/src/`. Start with the current implementation and the [maintainer's review notes](.github/maintainer/MAINTAINER.md). Device notes in `docs/devices/` describe particular firmware and must be read in that context.

## Make focused changes

- Preserve the Home entry points, resource IDs used by the launcher, saved preferences, and remote and volume behavior. For settings, keep the meaning of persisted option indices and use stable keys for logic driven by menu labels.
- Route new user-facing text through Android resources. Keep English defaults so other locales can fall back when a translation is missing.
- Keep the signed updater's asset digest, package name, certificate and newer-version checks intact. Increment `versionCode` for app updates and keep `versionName` at 7.0 unless the maintainer requests a change.
- Keep keystores, credentials, saved ADB identities, device addresses and diagnostic secrets out of commits. Handle security issues through [SECURITY.md](SECURITY.md).
- Keep release workflow changes small and verify which commit a signed release points to.

## Verify the change

Use JDK 17, Android SDK Platform 34 and Build Tools 34.0.0.

```sh
cd launcher
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

For a change to the shell helpers, run `python3 -m unittest discover -s tools/tests -v` from the repository root. For changes to focus, remote or startup behavior, record physical-TV testing separately from CI. A real remote verifies hardware key routing. If a device check is pending, say which behavior remains unverified.

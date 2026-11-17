# Build and install Personal Launcher on the selected ADB device.
# Use -TvHost or TV_HOST for a network target. Use SKIP_BUILD=1 for an existing APK.
param(
    [string]$TvHost = $env:TV_HOST,
    [switch]$InputKeyService,
    [switch]$HomeKeyService,
    [switch]$RestoreKeyServices
)

$ErrorActionPreference = 'Stop'
$package = 'com.sadique.launcher'
$apk = 'app/build/outputs/apk/release/app-release.apk'
$adbArgs = @()

function Invoke-Adb {
    & adb @adbArgs @args
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($args -join ' ') failed with exit code $LASTEXITCODE."
    }
}

function Get-EnabledServices {
    $value = (Invoke-Adb shell settings get secure enabled_accessibility_services | Out-String).Trim()
    if ($value -and $value -ne 'null') { $value -split ':' }
}

function Test-KeyService([string[]]$Services, [string]$Name) {
    return $Services -ccontains "$package/.$Name" -or $Services -ccontains "$package/$package.$Name"
}

Push-Location $PSScriptRoot
try {
    if ($TvHost) {
        if ($TvHost -notmatch ':\d+$' -or $TvHost.EndsWith(']')) {
            $TvHost = "$($TvHost):5555"
        }
        Invoke-Adb connect $TvHost
        $adbArgs = @('-s', $TvHost)
    }
    Invoke-Adb get-state | Out-Null

    if ($env:SKIP_BUILD -ne '1') {
        .\gradlew.bat assembleRelease
        if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed.' }
    }
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) {
        throw "APK missing: $apk. Build it with gradlew.bat assembleRelease first."
    }

    $inputKey = $InputKeyService -or $env:INPUT_KEY_SERVICE -eq '1'
    $homeKey = $HomeKeyService -or $env:HOME_KEY_SERVICE -eq '1'
    $restoreKeys = $RestoreKeyServices -or $env:RESTORE_KEY_SERVICES -eq '1'
    if ($restoreKeys) {
        $previous = @(Get-EnabledServices)
        $inputKey = $inputKey -or (Test-KeyService $previous InputKeyService)
        $homeKey = $homeKey -or (Test-KeyService $previous HomeKeyService)
    }

    Invoke-Adb install -r $apk
    if ($restoreKeys) {
        Invoke-Adb shell pm grant $package android.permission.WRITE_SECURE_SETTINGS
    }
    if ($inputKey -or $homeKey) {
        $existing = @(Get-EnabledServices)
        $services = $existing
        if ($inputKey -and -not (Test-KeyService $services InputKeyService)) {
            $services += "$package/.InputKeyService"
        }
        if ($homeKey -and -not (Test-KeyService $services HomeKeyService)) {
            $services += "$package/.HomeKeyService"
        }
        if ($services.Count -gt $existing.Count) {
            Invoke-Adb shell settings put secure enabled_accessibility_services ($services -join ':')
        }
        Invoke-Adb shell settings put secure accessibility_enabled 1
    }
    Invoke-Adb shell am start -W -n "$package/.MainActivity"
} finally {
    Pop-Location
}

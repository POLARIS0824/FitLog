---
name: agp-9-upgrade
description: Upgrades, or migrates, an Android project to use Android Gradle Plugin
  (AGP) version 9. Do not use this skill for migrating Kotlin Multiplatform (KMP)
  projects.
license: Complete terms in LICENSE.txt
metadata:
  author: Google LLC
  last-updated: '2026-04-17'
  keywords:
  - Android Gradle Plugin 9
  - AGP 9
  - AGP Upgrade
  - AGP Migration
  - New AGP DSL
  - Migrate to built-in Kotlin
---

## Migration guide

See the [AGP 9 migration guide](references/android/build/releases/agp-9-0-0-release-notes.md) for the major changes, many
breaking, in AGP 9 compared to AGP 8.

## Requirements

When the user requests an AGP 9 migration, inspect the current AGP, Gradle, JDK, Kotlin, KSP, and Hilt versions. The migration request authorizes compatible project-file changes and validation. Upgrade Assistant is an optional path, not a prerequisite requiring the user to perform the migration first. Preserve an explicitly requested target version; otherwise select a compatible stable AGP 9 release after checking its release notes. Ask only for a material unresolved tradeoff or an operation outside existing authorization.

Each version of AGP has its own set of compatibilities with other tools, such as
Gradle, JDK, and Kotlin. The release notes for each of these versions will
include a **Compatibility** table indicating the minimum versions for these
tools.

Do not use this skill for KMP projects, as they are unsupported.

## Steps

For the requested AGP 9 target, perform only applicable steps; do not downgrade an already newer project:

### Step 1: Update dependencies

If KSP (`com.google.devtools.ksp`) is used in the project, ensure it is on
version 2.3.6 or higher.

If Hilt is used in the project, ensure it is on version 2.59.2 or higher.

### Step 2: Migrate to built-in Kotlin

See [the guide](references/android/build/migrate-to-built-in-kotlin.md) for detailed information.

### Step 3. Migrate to the new AGP DSL

See [the guide](references/android/build/releases/agp-9-0-0-release-notes.md) for detailed information.

See also [gradle-recipes](references/recipes.md) for examples on how to migrate old code to code
that is compatible with AGP 9 and the new DSL.

### Step 4. Migrate kapt to KSP or legacy-kapt

If KSP (`com.google.devtools.ksp`) or kapt (`org.jetbrains.kotlin.kapt`) are
used in the project, see [KSP, kapt, and legacy-kapt](references/ksp-kapt.md) for detailed migration
steps.

### Step 5. BuildConfig

If any Android module contains custom BuildConfig fields, see [BuildConfig](references/buildconfig.md)
for detailed information.

### Step 6. Update gradle.properties

After the migration, check gradle.properties. Remove the following flags:

1. android.builtInKotlin
2. android.newDsl
3. android.uniquePackageNames
4. android.enableAppCompileTimeRClass

Remove only task-created temporary files whose paths and ownership are verified; preserve user files and useful requested reports.

## Guidelines

- Never write or run python scripts.
- Only search the Gradle dependency cache when inspecting external dependencies, and only as a last resort.
- Never add `android.disallowKotlinSourceSets=false` to `gradle.properties`.
- When verifying changes, don't run the `clean` task. This is a waste of time.

## Verification

After migration, verify the following:

1. Run wrapper `help` and the relevant build task graph (`build --dry-run`) from the supported host shell; on Windows use `.\gradlew.bat`, never WSL.
2. Compile affected variants and run relevant existing tests: a dry run alone does not validate compilation or behavior.
3. Verify IDE sync if available; otherwise report it as unverified without asking the user to perform routine checks before continuing.

## Troubleshooting

Paparazzi v2.0.0-alpha04 and lower versions have issues with AGP 9. See
[references/paparazzi-gradle-9.md](references/paparazzi-gradle-9.md) for details.

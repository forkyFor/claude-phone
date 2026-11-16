plugins {
    // AGP 9 compiles Kotlin itself ("built-in Kotlin"); no Kotlin plugin is applied to :app.
    id("com.android.application") version "9.5.1" apply false
    // Only pins the Kotlin compiler version AGP uses. It must be at least the Kotlin version
    // GeckoView was built with (see geckoVersion in app/build.gradle.kts).
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
}

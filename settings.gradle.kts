pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Only used by the "gecko" flavor (GeckoView, MPL-2.0).
        maven("https://maven.mozilla.org/maven2") {
            content { includeGroup("org.mozilla.geckoview") }
        }
    }
}
rootProject.name = "OldPhoneClaude"
include(":app")

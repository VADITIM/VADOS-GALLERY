pluginManagement {
    includeBuild("build-logic")
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
    }
}

rootProject.name = "VADOS-GALLERY"

// The app joins the features, features stand on the core, and the core never reaches up (docs/ARCHITECTURE.md).
include(":app")
include(":core:settings")
include(":core:data")
include(":core:design")
include(":core:ui")
include(":feature:viewer")
include(":feature:review")
include(":feature:duplicates")
include(":feature:picker")
include(":feature:albums")
include(":feature:settings")

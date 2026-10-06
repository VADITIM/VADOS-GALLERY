dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    // The build logic reads the same versions as the app, so a plugin is bumped in one place.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")

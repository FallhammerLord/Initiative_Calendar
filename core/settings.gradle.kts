rootProject.name = "core"

// Share the app's version catalog, so both builds use the same Kotlin.
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

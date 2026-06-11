rootProject.name = "gbkt-gradle-plugin"

// This is an included build (composite) — it does not inherit the parent's version
// catalog, so import the shared one explicitly to keep versions (e.g. ktfmt) in sync.
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

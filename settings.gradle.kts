rootProject.name = "gbkt"

// Skip plugin and samples when building core only (used in CI before publishing)
// Usage: ./gradlew -PcoreOnly=true :gbkt-core:build
val coreOnly = providers.gradleProperty("coreOnly").orNull?.toBoolean() ?: false

pluginManagement {
    repositories {
        mavenLocal()  // For local development
        mavenCentral()  // For kotlinx.atomicfu and other plugins
        gradlePluginPortal()
    }
    // Only include plugin when not in coreOnly mode
    val skipPlugin = providers.gradleProperty("coreOnly").orNull?.toBoolean() ?: false
    if (!skipPlugin) {
        includeBuild("gbkt-gradle-plugin")
    }
}

include("gbkt-core")
include("gbkt-cli")

if (!coreOnly) {
    include("sample-game")
    include("sample-minimal")
    include("sample-dialog")
    include("sample-save")
    include("sample-adventure")
    include("sample-modular")
}

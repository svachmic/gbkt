rootProject.name = "gbkt"

pluginManagement {
    repositories {
        mavenLocal()  // For local development
        mavenCentral()  // For kotlinx.atomicfu and other plugins
        gradlePluginPortal()
    }
    includeBuild("gbkt-gradle-plugin")
}

include("gbkt-core")
include("gbkt-cli")
include("sample-game")
include("sample-minimal")
include("sample-dialog")
include("sample-save")
include("sample-adventure")
include("sample-modular")

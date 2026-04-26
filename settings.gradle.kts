pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "jamo"

include(
    ":identity-service",
    ":diary-service",
    ":chat-service",
    ":learning-service",
    ":platform-service",
)

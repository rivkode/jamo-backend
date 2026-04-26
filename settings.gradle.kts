pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "jamo"

include(
    ":contracts",
    ":common-auth-jwt",
    ":common-infrastructure",
    ":identity-service",
    ":diary-service",
    ":chat-service",
    ":learning-service",
    ":platform-service",
)

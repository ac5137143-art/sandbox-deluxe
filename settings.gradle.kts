pluginManagement {
    // Local builds in restricted networks can pass -Pmirror=https://host/repository/central
    val mirrors = providers.gradleProperty("mirror").orNull
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

    repositories {
        mirrors.forEach { url -> maven { setUrl(url) } }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val mirrors = providers.gradleProperty("mirror").orNull
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mirrors.forEach { url -> maven { setUrl(url) } }
        google()
        mavenCentral()
    }
}

rootProject.name = "SandboxDeluxe"

include(":core")
include(":tools")
include(":app")

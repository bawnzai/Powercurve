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
        // Token-free SDK: clone hammerheadnav/karoo-ext at tag 1.1.9 and run
        // .\gradlew.bat :lib:publishToMavenLocal with JAVA_HOME and ANDROID_HOME set.
        mavenLocal {
            content { includeModule("io.hammerhead", "karoo-ext") }
        }
        google()
        mavenCentral()
        // karoo-ext is public but GitHub Packages always requires a token (read:packages scope)
        maven {
            url = uri("https://maven.pkg.github.com/hammerheadnav/karoo-ext")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("USERNAME")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("TOKEN")
            }
        }
    }
}

rootProject.name = "karoo-power-curve"
include(":app")

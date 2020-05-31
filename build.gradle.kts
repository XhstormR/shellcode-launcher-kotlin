version = "1.0-SNAPSHOT"

plugins {
    idea
    kotlin("multiplatform") version "1.3.72"
    id("org.jlleitschuh.gradle.ktlint") version "9.2.1"
}

kotlin {
    val hostOs = org.gradle.internal.os.OperatingSystem.current()

    val hostTarget = when {
        hostOs.isLinux -> linuxX64("launcher")
        hostOs.isMacOsX -> macosX64("launcher")
        hostOs.isWindows -> mingwX64("launcher")
        else -> throw Error("Unknown host")
    }

    hostTarget.apply {
        binaries {
            executable {
                entryPoint = "com.xhstormr.app.main"
            }
        }
    }
}

repositories {
    maven("https://maven.aliyun.com/repository/jcenter")
}

tasks {
    withType<Wrapper> {
        gradleVersion = "6.3"
        distributionType = Wrapper.DistributionType.ALL
    }
}

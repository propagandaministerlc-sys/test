import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

sourceSets {
    main {
        // Layout model, crop maths and filters shared with the Android app.
        kotlin.srcDir("../app/src/shared/kotlin")
        // Inter font files, shared with the Android app.
        resources.srcDir("../app/src/main/res/font")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

compose.desktop {
    application {
        mainClass = "app.ultragrid.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "UltraGrid"
            packageVersion = "1.2.0"
            description = "Custom photo grids for Instagram"
            vendor = "UltraGrid"
            copyright = "UltraGrid"
            includeAllModules = true
            windows {
                iconFile.set(project.file("packaging/ultragrid.ico"))
                menuGroup = "UltraGrid"
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6f4c2a8e-3b1d-4f6a-9c2e-7d5b8a1e0f43"
            }
        }
    }
}

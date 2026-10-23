import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.IncrementalSyncTask

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("com.guardsquare:proguard-gradle:7.7.0") }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop")

    iosArm64()
    iosSimulatorArm64()

    listOf(
        iosArm64(), iosSimulatorArm64()
    ).forEach { target ->
        target.binaries.framework {
            baseName = "StockScreenerKit"
            isStatic = true
        }
    }

    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(libs.compose.material.icons.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // okio-Dateiablage (FileBarStore) für alle Nicht-Wasm-Plattformen,
        // siehe src/diskMain (okio hat keinen wasmJs-Variant)
        val androidMain by getting {
            kotlin.srcDir("src/diskMain/kotlin")
            dependencies {
                implementation(libs.okio)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.activity.compose)
                implementation(libs.lifecycle.viewmodel.compose)
            }
        }
        val desktopMain by getting {
            kotlin.srcDir("src/diskMain/kotlin")
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.okio)
                implementation(libs.ktor.client.okhttp)
                // Windows-Natives zusätzlich einbinden (currentOs = nur macOS)
                runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.144.6")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.compose.ui.test)
            }
        }
        iosMain {
            kotlin.srcDir("src/diskMain/kotlin")
            dependencies {
                implementation(libs.okio)
                implementation(libs.ktor.client.darwin)
            }
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.components.resources)
                implementation(libs.ktor.client.js)
                implementation(libs.kotlinx.browser)
            }
        }
    }
}

android {
    namespace = "de.luigialice.stockscreener"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        targetSdk = 36
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "de.luigialice.stockscreener.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "StockScreener"
            packageVersion = "1.0.0"
        }
    }
}

tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Ausführbares Jar mit allen Desktop-Abhängigkeiten"
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes("Main-Class" to "de.luigialice.stockscreener.MainKt") }
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("META-INF/versions/*/module-info.class", "module-info.class")
    from({ configurations.getByName("desktopRuntimeClasspath").map { if (it.isDirectory) it else zipTree(it) } })
    with(tasks.getByName("desktopJar") as Jar)

    // Nur Skiko-Bibliotheken für die eigene macOS-Architektur und Windows x64 einpacken
    val currentArch = if (System.getProperty("os.arch").contains("aarch64")) "arm64" else "x64"
    val keepSkiko = listOf("libskiko-macos-$currentArch", "skiko-macos-$currentArch", "libskiko-windows-x64", "skiko-windows-x64")
    exclude {
        val n = it.name
        val isSkikoNative = n.startsWith("libskiko-") || n.startsWith("skiko-")
        isSkikoNative && keepSkiko.none { p -> n.startsWith(p) }
    }
}

tasks.register("minifiedJar", proguard.gradle.ProGuardTask::class) {
    group = "build"
    description = "ProGuard-verkleinerte fatJar (nur Shrinking, keine Obfuskierung)"
    val fatJarTask = tasks.getByName<Jar>("fatJar")
    dependsOn(fatJarTask)
    injars(fatJarTask.archiveFile.get().asFile)
    outjars(layout.buildDirectory.file("libs/composeApp-min.jar").get().asFile)
    val jmods = listOf(
        "java.base", "java.desktop", "java.logging", "java.xml", "java.sql",
        "java.naming", "java.management", "java.security.jgss", "jdk.unsupported"
    )
    jmods.forEach { libraryjars("${System.getProperty("java.home")}/jmods/$it.jmod") }
    configuration(project.file("compose-desktop.pro"))
}

// Dev- und Prod-Build füllen denselben Webpack-Ordner build/wasm/packages/<name>/kotlin.
// Der Sync des Kotlin-Plugins (DefaultIncrementalSyncTask, kein org.gradle.api.tasks.Sync!)
// kopiert nur Dateien, die sich seit dem letzten Lauf *dieses Tasks* geändert haben. Läuft
// also der Prod-Sync und danach der Dev-Sync, bleibt das Prod-Import-Objekt (ohne
// kotlin.time.toFixed) neben dem Dev-Wasm (braucht es) liegen – der Browser startet mit
// LinkError "function import requires a callable". Nach jedem Lauf wird deshalb geprüft und
// repariert: das Ziel muss eine exakte Kopie der Quellen sein. (Nicht einfach löschen – der
// inkrementelle Sync stellt gelöschte, unveränderte Dateien nicht wieder her; damit fehlt
// danach der Webpack-Entry und der Task wird still übersprungen.)
tasks.withType<IncrementalSyncTask>().configureEach {
    doLast {
        val dest = destinationDirectory.get()
        from.files.filter { it.isDirectory }.forEach { src ->
            src.listFiles()?.forEach { f ->
                val target = dest.resolve(f.name)
                val inSync = if (f.isFile) {
                    target.isFile && target.length() == f.length() && target.readBytes().contentEquals(f.readBytes())
                } else {
                    target.isDirectory
                }
                if (!inSync) {
                    if (f.isDirectory) f.copyRecursively(target, overwrite = true) else f.copyTo(target, overwrite = true)
                }
            }
        }
    }
}

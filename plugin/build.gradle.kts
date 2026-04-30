plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.floatplay"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

intellij {
    version.set("2023.3")
    type.set("IC")
    plugins.set(listOf("com.intellij.java"))
}

tasks {
    buildSearchableOptions {
        enabled = false
    }

    // Disable instrumentation to avoid JDK compatibility issues
    instrumentCode {
        enabled = false
    }

    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("261.*")
    }

    runIde {
        // Point to the native library for development
        jvmArgs("-Djava.library.path=${rootProject.projectDir}/../native/target/release")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

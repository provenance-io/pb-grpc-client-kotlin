plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":client"))
    implementation(project(":client-coroutines"))
    implementation(project(":client-common"))
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.KotlinxCore)
}
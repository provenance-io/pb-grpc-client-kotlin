plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":client"))
    implementation(project(":client-common"))
    implementation(project(":client-coroutines"))
    implementation("io.provenance","proto-kotlin", Versions.ProvenanceProtos)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.KotlinxCore)
}
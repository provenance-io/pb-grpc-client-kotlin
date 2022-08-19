plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(projects.clientCommon)
    implementation(projects.client)
    implementation(projects.clientCoroutines)
    implementation(libs.provenance.protos)
    implementation(libs.kotlinx.coroutines)
}
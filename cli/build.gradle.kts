plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    listOf(
        projects.client,
        projects.clientCommon,
        projects.clientCoroutines,
        libs.kotlinx.coroutines,
        libs.provenance.protos,
    ).forEach(::implementation)
}

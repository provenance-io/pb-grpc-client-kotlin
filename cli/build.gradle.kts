plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
    // TODO: Remove staging repository reference when v1.13.0 is officially merged
    maven {
        url = uri("https://s01.oss.sonatype.org/content/groups/staging/")
    }
}

dependencies {
    implementation(projects.clientCommon)
    implementation(projects.client)
    implementation(projects.clientCoroutines)
    implementation(libs.provenance.protos)
    implementation(libs.kotlinx.coroutines)
}

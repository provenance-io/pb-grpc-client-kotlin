plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
    // TODO: Remove staging repository reference when v1.13.0 is officially merged
    maven {
        url = uri("https://s01.oss.sonatype.org/content/groups/staging/")
    }
}

dependencies {
    api(projects.clientCommon)
    implementation(libs.grpc.alts)
    implementation(libs.grpc.netty)
    implementation(libs.provenance.protos)
    implementation(libs.kotlinx.coroutines)

    testImplementation(projects.client)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

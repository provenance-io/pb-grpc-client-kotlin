plugins {
    kotlin("jvm")
    `java-library`
}

repositories {
    mavenCentral()
    // TODO: Remove staging repository reference when v1.13.0 is officially merged
    maven {
        url = uri("https://s01.oss.sonatype.org/content/groups/staging/")
    }
}

dependencies {
    // Provenance / Cosmos-SDK protos
    implementation(libs.provenance.protos)
    implementation(libs.grpc.alts)
    implementation(libs.grpc.netty)


    // Test
    testImplementation(libs.kotlin.test.junit)
}

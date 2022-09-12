plugins {
    kotlin("jvm")
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    // Provenance / Cosmos-SDK protos
    implementation(libs.provenance.protos)
    implementation(libs.grpc.alts)
    implementation(libs.grpc.netty)


    // Test
    testImplementation(libs.kotlin.test.junit)
}
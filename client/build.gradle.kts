plugins {
    kotlin("jvm")
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(projects.clientCommon)

    implementation(libs.grpc.netty)
    implementation(libs.apache.http)

    implementation(libs.provenance.protos)
    implementation(libs.provenance.hdwallet)

    testImplementation(libs.kotlin.test.junit)
}
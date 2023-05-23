plugins {
    kotlin("jvm")
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    listOf(
        projects.clientCommon,
        libs.grpc.alts,
        libs.grpc.netty,
        libs.provenance.hdwallet,
        libs.provenance.protos,
    ).forEach(::api)

    testImplementation(libs.kotlin.test.junit)
}

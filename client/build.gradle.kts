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
        libs.figuretech.hdwallet,
        libs.grpc.alts,
        libs.grpc.netty,
        libs.provenance.protos,
    ).forEach(::api)

    testImplementation(libs.kotlin.test.junit)
}

plugins {
    kotlin("jvm")
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    listOf(
        libs.grpc.alts,
        libs.grpc.netty,
        libs.provenance.protos,
        libs.figuretech.hdwallet,
    ).forEach(::api)

    testImplementation(libs.kotlin.test.junit)
}

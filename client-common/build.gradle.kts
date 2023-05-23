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
    ).forEach(::api)

    testImplementation(libs.kotlin.test.junit)
}

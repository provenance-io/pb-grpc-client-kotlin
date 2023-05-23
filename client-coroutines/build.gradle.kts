plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    listOf(
        projects.clientCommon,
        libs.grpc.alts,
        libs.grpc.netty,
        libs.kotlinx.coroutines,
        libs.provenance.protos,
    ).forEach(::api)

    listOf(
        projects.client,
        libs.kotlin.test.junit,
        libs.kotlinx.coroutines.test,
    ).forEach(::testImplementation)
}

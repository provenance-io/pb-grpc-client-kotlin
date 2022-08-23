plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
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

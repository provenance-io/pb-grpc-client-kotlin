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
    api(projects.clientCommon)

    implementation(libs.grpc.netty)
    implementation(libs.apache.http)

    implementation(libs.provenance.protos)
    implementation(libs.provenance.hdwallet)

    testImplementation(libs.kotlin.test.junit)
}

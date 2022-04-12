plugins {
    kotlin("jvm")
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":client-common"))
    implementation("io.grpc", "grpc-netty", Versions.Grpc)
    implementation("org.apache.httpcomponents", "httpclient", Versions.ApachHttp)

    implementation("io.provenance", "proto-kotlin", Versions.ProvenanceProtos)
    implementation("io.provenance.hdwallet", "hdwallet", Versions.ProvenanceHDWallet)

    testImplementation("org.jetbrains.kotlin", "kotlin-test-junit")
}
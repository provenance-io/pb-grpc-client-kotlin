plugins {
    kotlin("jvm")
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    // Provenance / Cosmos-SDK protos
    implementation("io.provenance:proto-kotlin:${Versions.ProvenanceProtos}")

    // Test
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
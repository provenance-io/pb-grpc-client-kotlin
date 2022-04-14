plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":client-common"))
    implementation("io.grpc", "grpc-alts", Versions.Grpc)
    implementation("io.provenance","proto-kotlin", Versions.ProvenanceProtos)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.KotlinxCore)
}

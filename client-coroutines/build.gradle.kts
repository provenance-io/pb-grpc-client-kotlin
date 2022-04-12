plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(rootProject)
    implementation("io.provenance","proto-kotlin", Versions.ProvenanceProtos)
    implementation("io.grpc", "grpc-alts", Versions.Grpc)
    implementation("io.grpc", "grpc-netty", Versions.Grpc)
    implementation("io.grpc", "grpc-protobuf", Versions.Grpc)
    implementation("io.grpc", "grpc-stub", Versions.Grpc)

    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.KotlinxCore)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8", Versions.KotlinxCore)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-reactive", Versions.KotlinxCore)

    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin", "kotlin-reflect")
    implementation("org.jetbrains.kotlin", "kotlin-stdlib")
    implementation("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
}
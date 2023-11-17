import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import java.net.URI

buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.10")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.10"
    signing
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
    id("org.jlleitschuh.gradle.ktlint") version "10.2.1"
}

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    // Pin kotlin packages together on a common version:
    listOf(
        libs.kotlin.allopen,
        libs.kotlin.bom,
        libs.kotlin.reflect,
        libs.kotlin.jdk8,
    ).forEach(::implementation)
}

val projectVersion = project.property("version")?.takeIf { it != "unspecified" } ?: "1.0-SNAPSHOT"

object Repos {
    private object sonatype {
        const val snapshots = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
        const val releases = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
    }

    fun RepositoryHandler.sonatypeOss(projectVersion: String): MavenArtifactRepository {
        val murl =
            if (projectVersion == "1.0-SNAPSHOT") sonatype.snapshots
            else sonatype.releases

        return maven {
            name = "Sonatype"
            url = URI.create(murl)
            credentials {
                username = System.getenv("OSSRH_USERNAME")
                password = System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

subprojects {
    apply {
        plugin("java")
        plugin("kotlin")
        plugin("signing")
        plugin("maven-publish")
    }

    configurations.forEach { it.exclude("org.slf4j", "slf4j-api") }
    val artifactName = "pb-grpc-$name-kotlin"

    tasks.withType<PublishToMavenLocal> {
        signing.isRequired = false
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                groupId = "io.provenance.client"
                artifactId = artifactName
                version = "$projectVersion"

                from(components["java"])

                pom {
                    name.set("Provenance Blockchain GRPC Kotlin Client")
                    description.set("A GRPC client for communicating with the Provenance Blockchain")
                    url.set("https://provenance.io")

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            id.set("pstory")
                            name.set("Phil Story")
                            email.set("tech@figure.com")
                        }
                        developer {
                            id.set("vwagner")
                            name.set("Valerie Wagner")
                            email.set("tech@figure.com")
                        }
                    }

                    scm {
                        connection.set("git@github.com:provenance-io/pb-grpc-client-kotlin.git")
                        developerConnection.set("git@github.com/provenance-io/pb-grpc-client-kotlin.git")
                        url.set("https://github.com/provenance-io/pb-grpc-client-kotlin")
                    }
                }
            }
        }

        configure<SigningExtension> {
            sign(publications["maven"])
        }
    }

    configure<KotlinJvmProjectExtension> {
        jvmToolchain(17)
        compilerOptions {
            freeCompilerArgs.set(listOf("-opt-in=kotlin.time.ExperimentalTime"))
        }
    }

    java {
        withJavadocJar()
        withSourcesJar()
    }
}

configure<io.github.gradlenexus.publishplugin.NexusPublishExtension> {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(findProject("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME"))
            password.set(findProject("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD"))
            stagingProfileId.set("3180ca260b82a7") // prevents querying for the staging profile id, performance optimization
        }
    }
}

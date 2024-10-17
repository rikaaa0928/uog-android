import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

plugins {
    id("com.google.protobuf")
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "moe.rikaaa0928.uot"
    compileSdk = 34

    defaultConfig {
        applicationId = "moe.rikaaa0928.uot"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".rika"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
//    implementation(libs.grpc.okhttp)
//    implementation(libs.grpc.protobuf.lite)
//    implementation(libs.protobuf.kotlin.lite)
//    implementation(libs.grpc.stub)
//    implementation(libs.grpc.kotlin.stub)
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("rustls:rustls-platform-verifier:latest.release")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

protobuf {
    protoc {
        artifact = libs.protoc.asProvider().get().toString()
    }
    plugins {
        create("java") {
            artifact = libs.protoc.gen.grpc.java.get().toString()
        }
        create("grpc") {
            artifact = libs.protoc.gen.grpc.java.get().toString()
        }
        create("grpckt") {
            artifact = libs.protoc.gen.grpc.kotlin.get().toString() + ":jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("java") {
                    option("lite")
                }
                create("grpc") {
                    option("lite")
                }
                create("grpckt") {
                    option("lite")
                }
            }
            it.builtins {
                create("kotlin") {
                    option("lite")
                }
            }
        }
    }
}


repositories {
    rustlsPlatformVerifier()
    google()
    mavenCentral()
}

fun RepositoryHandler.rustlsPlatformVerifier(): MavenArtifactRepository {
    @Suppress("UnstableApiUsage")
    val manifestPath = let {
        val dependencyJson = providers.exec {
            workingDir = File(project.rootDir, "")
            commandLine(
                "cargo",
                "metadata",
                "--format-version",
                "1",
                "--filter-platform",
                "aarch64-linux-android",
                "--manifest-path",
                "lib-src/uog/Cargo.toml"
            )
        }.standardOutput.asText
        val path = Json.decodeFromString<JsonObject>(dependencyJson.get())
            .getValue("packages")
            .jsonArray
            .first { element ->
                element.jsonObject.getValue("name").jsonPrimitive.content == "rustls-platform-verifier-android"
            }.jsonObject.getValue("manifest_path").jsonPrimitive.content
        File(path)
    }
    println(manifestPath)
    println( uri(File(manifestPath.parentFile, "maven").path))
    return maven {
        url = uri(File(manifestPath.parentFile, "maven").path)
        metadataSources.artifact()
    }
}
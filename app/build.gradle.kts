import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// G-30 (NFG-81, SS-13): data controller name/contact for the privacy notice. Single source:
// app/veri-sorumlusu.properties. A value that is blank or still "[...]" counts as missing.
val dataController = Properties().apply {
    file("veri-sorumlusu.properties").reader(Charsets.UTF_8).use { load(it) }
}
fun dataControllerValue(key: String): String = dataController.getProperty(key, "").trim()
fun isPlaceholder(value: String) = value.isEmpty() || (value.startsWith("[") && value.endsWith("]"))
fun javaStringLiteral(value: String) =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val missingDataController = listOf("VERI_SORUMLUSU_AD", "ILETISIM")
    .filter { isPlaceholder(dataControllerValue(it)) }

android {
    namespace = "tr.yasliasistan.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "tr.yasliasistan.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "DATA_CONTROLLER_NAME", javaStringLiteral(dataControllerValue("VERI_SORUMLUSU_AD")))
        buildConfigField("String", "DATA_CONTROLLER_CONTACT", javaStringLiteral(dataControllerValue("ILETISIM")))
        // G-43: on-device embedding benchmark (tools/gomme-olcum.ps1).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8 shrinking/obfuscation; also strips SafeLog.content calls (proguard-rules.pro).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// Hand each variant's merged manifest to its unit tests so ManifestPermissionTest
// checks the final manifest (including permissions added by libraries).
androidComponents {
    onVariants { variant ->
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val testTaskName = "test${variant.name.replaceFirstChar { it.uppercase() }}UnitTest"
        tasks.withType<Test>().matching { it.name == testTaskName }.configureEach {
            inputs.file(manifest).withPropertyName("mergedManifest")
            jvmArgumentProviders.add(
                CommandLineArgumentProvider {
                    listOf("-DmergedManifest=${manifest.get().asFile.absolutePath}")
                }
            )
        }
    }
}

// G-30: a release build with an incomplete privacy notice gets a loud warning (docs/KURULUM-SON.md).
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    val missing = missingDataController
    doFirst {
        if (missing.isNotEmpty()) {
            logger.warn(
                "UYARI (G-30, KVKK): Aydınlatma metnindeki veri sorumlusu bilgisi eksik: $missing. " +
                    "app/veri-sorumlusu.properties dosyasını doldurun (docs/KURULUM-SON.md)."
            )
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":speech"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    // G-18: on-device metric log (app-private database, excluded from backup).
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // G-43: task-memory embedding model runtime (V-03, allow-listed).
    implementation(libs.onnxruntime.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

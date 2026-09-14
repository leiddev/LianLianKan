import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * release 签名信息从 local.properties 读取（该文件已被 .gitignore 排除），
 * 因此密钥与密码不会进入版本库。
 *
 * 缺失时 release 构建会退化为未签名，`assembleDebug` 与单测不受影响 ——
 * 这让「没有密钥的人也能跑测试」与「有密钥的人能出正式包」两件事互不干扰。
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val releaseStorePath: String? = keystoreProperties.getProperty("RELEASE_STORE_FILE")
val hasReleaseSigning: Boolean =
    !releaseStorePath.isNullOrBlank() && rootProject.file(releaseStorePath).exists()

android {
    namespace = "com.ldxy.lianliankan"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ldxy.lianliankan"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStorePath!!)
                storePassword = keystoreProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = keystoreProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = keystoreProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                // 保持关闭：R8 需要真机回归才能确认 keep 规则完整，
                // 而本项目当前没有可用的设备/模拟器验证通道（见开发计划第 7 节）。
                enable = false
            }
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Compose：版本由 BOM 统一管理，各 Compose 构件不再单独写版本号
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Lifecycle / Navigation
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    // 本地持久化（FR-14）
    implementation(libs.androidx.datastore.preferences)

    // 单元测试（领域层主力，SRS NFR-3.1）
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // 仪器测试
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}

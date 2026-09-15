import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * release 绛惧悕淇℃伅浠?local.properties 璇诲彇锛堣鏂囦欢宸茶 .gitignore 鎺掗櫎锛夛紝
 * 鍥犳瀵嗛挜涓庡瘑鐮佷笉浼氳繘鍏ョ増鏈簱銆?
 *
 * 缂哄け鏃?release 鏋勫缓浼氶€€鍖栦负鏈鍚嶏紝`assembleDebug` 涓庡崟娴嬩笉鍙楀奖鍝?鈥斺€?
 * 杩欒銆屾病鏈夊瘑閽ョ殑浜轰篃鑳借窇娴嬭瘯銆嶄笌銆屾湁瀵嗛挜鐨勪汉鑳藉嚭姝ｅ紡鍖呫€嶄袱浠朵簨浜掍笉骞叉壈銆?
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
        versionCode = 9
        versionName = "1.5"

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
                // 淇濇寔鍏抽棴锛歊8 闇€瑕佺湡鏈哄洖褰掓墠鑳界‘璁?keep 瑙勫垯瀹屾暣锛?
                // 鑰屾湰椤圭洰褰撳墠娌℃湁鍙敤鐨勮澶?妯℃嫙鍣ㄩ獙璇侀€氶亾锛堣寮€鍙戣鍒掔 7 鑺傦級銆?
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

    // 鍚姩鐢婚潰锛圴1.4锛夛細鍐峰惎鍔ㄦ椂鐢ㄥ搧鐗屽簳鑹?+ 鍝佺墝鍥炬爣椤舵帀骞冲彴榛樿鐧藉睆锛?
    // 骞舵敮鎸併€岃缃瀹屼箣鍓嶄笉鏀捐銆嶏紙瑙?MainActivity 涓?values/themes.xml锛?
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.coroutines.android)

    // 鏍囧噯 M3 鑹茶皟鏉跨畻娉曪紝鐢ㄤ簬浠庣瀛愯壊鐢熸垚 4 濂椾富棰橀厤鑹诧紙V1.3锛?
    implementation(libs.material.color.utilities)

    // Compose锛氱増鏈敱 BOM 缁熶竴绠＄悊锛屽悇 Compose 鏋勪欢涓嶅啀鍗曠嫭鍐欑増鏈彿
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

    // 鏈湴鎸佷箙鍖栵紙FR-14锛?
    implementation(libs.androidx.datastore.preferences)

    // 鍗曞厓娴嬭瘯锛堥鍩熷眰涓诲姏锛孲RS NFR-3.1锛?
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // 浠櫒娴嬭瘯
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}

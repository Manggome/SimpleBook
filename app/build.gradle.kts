plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// keystore/release.jks 가 있으면 릴리스 서명, 없으면 고정 debug 키로 서명한다.
val releaseKeystore = rootProject.file("keystore/release.jks")

// 저장소에 고정해 둔 debug 서명 키.
// 빌드마다 키가 바뀌면 기존 앱을 지워야만 설치되므로, 키를 고정해 덮어쓰기 업데이트가 되게 한다.
val debugKeystore = rootProject.file("keystore/debug.jks")

android {
    namespace = "kr.neptune.simplebook"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.neptune.simplebook"
        minSdk = 24
        targetSdk = 35

        // CI 실행번호로 버전을 매긴다. 앱이 릴리스의 latest.json 과 비교해
        // 새 버전이 있으면 스스로 받아 설치할 수 있게 하기 위함.
        versionCode = 1 + ((System.getenv("GITHUB_RUN_NUMBER") ?: "").toIntOrNull() ?: 0)
        versionName = "1.0." + ((System.getenv("GITHUB_RUN_NUMBER") ?: "0"))
    }

    signingConfigs {
        if (debugKeystore.exists()) {
            create("fixedDebug") {
                storeFile = debugKeystore
                storePassword = "simplebook"
                keyAlias = "simplebook"
                keyPassword = "simplebook"
            }
        }
        if (releaseKeystore.exists()) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "simplebook"
                keyAlias = System.getenv("KEY_ALIAS") ?: "simplebook"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "simplebook"
            }
        }
    }

    buildTypes {
        release {
            // junrar 가 리플렉션/SLF4J 를 쓰므로 난독화는 끈다. APK 가 작아 얻는 것이 적다.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseKeystore.exists()) {
                signingConfigs.getByName("release")
            } else if (debugKeystore.exists()) {
                signingConfigs.getByName("fixedDebug")
            } else {
                null
            }
        }
        debug {
            isMinifyEnabled = false
            if (debugKeystore.exists()) {
                signingConfig = signingConfigs.getByName("fixedDebug")
            }
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCY",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.documentfile:documentfile:1.0.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.11.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // RAR/CBR 압축 해제 (RAR4 전용 — RAR5 는 지원하지 않아 앱이 안내 메시지를 띄운다)
    implementation("com.github.junrar:junrar:7.5.5")
    // junrar 가 SLF4J 를 부르는데 바인딩이 없으면 로그 경고가 뜬다. 무동작 바인딩을 넣는다.
    implementation("org.slf4j:slf4j-nop:2.0.16")
}

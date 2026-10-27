plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.keepdev.routeemock"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.keepdev.routeemock"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        // 팀 배포용: 코드·리소스를 줄여 디스코드 10MB 한도 안으로. 스토어에 안 올리니 디버그 키로 서명
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.core:core-location-altitude:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("com.google.android.gms:play-services-location:21.4.0")
    // play-services가 끌고 오는 fragment 1.1.0은 권한 요청 결과를 못 받음
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}

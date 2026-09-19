import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 签名信息放在仓库根目录的 keystore.properties (已被 .gitignore 忽略)。
// storeFile 指向项目文件夹之外的绝对路径, 这样整个项目可以安全地打包/分享。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

// release 必须签得上名, 缺钥匙就直接报错
// —— 免得安静地生出一个 app-release-unsigned.apk 然后被当成正式包发出去
if (!keystorePropsFile.exists() && gradle.startParameter.taskNames.any { it.contains("release", true) }) {
    throw GradleException(
        "缺少 keystore.properties，无法给 release 包签名。\n" +
            "签名密钥见 密码本「课表App签名密钥」，或 README「发布构建（签名）」。"
    )
}

android {
    namespace = "com.example.courseschedule"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.CourseSchedule.courseschedule"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 纯 Compose、无反射, R8 安全; 顺带压掉未使用的资源
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 始终挂上签名配置: 缺钥匙时让 AGP 在 validateSigning 阶段直接报错,
            // 而不是静默产出一个装不上的 app-release-unsigned.apk
            signingConfig = signingConfigs.getByName("release")
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

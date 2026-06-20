plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// قراءة إعدادات التوقيع من ملف منفصل (keystore.properties) للحفاظ على الأمان
val keystorePropsFile = rootProject.file("keystore.properties")
fun loadKeystoreProps(): Map<String, String> {
    return if (!keystorePropsFile.exists()) emptyMap()
    else keystorePropsFile.readLines()
        .filter { it.contains("=") && !it.startsWith("#") }
        .associate {
            val parts = it.split("=", limit = 2)
            parts[0].trim() to parts[1].trim()
        }
}
val keystoreProps = loadKeystoreProps()

android {
    namespace = "com.platinum.vip.hasiba"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.platinum.vip.hasiba"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "2.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = keystorePropsFile.takeIf { it.exists() }?.let {
                rootProject.file(keystoreProps["storeFile"] ?: "My_keystore.jks")
            }
            storePassword = keystoreProps["storePassword"] ?: ""
            keyAlias = keystoreProps["keyAlias"] ?: ""
            keyPassword = keystoreProps["keyPassword"] ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    
    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":lib-calculator"))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // الأساسيات
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // --- مكتبات الكوروتينات ودورة الحياة (تمت إضافتها للأكواد الجديدة) ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    // --- مكتبات المشروع ---
    
    // 1. النوافذ المنبثقة
    implementation("com.afollestad.material-dialogs:core:3.3.0")
    implementation("com.afollestad.material-dialogs:input:3.3.0")

    // 3. الأيقونات
    implementation("com.mikepenz:iconics-core:5.4.0")
    implementation("com.mikepenz:iconics-views:5.4.0")
    implementation("com.mikepenz:fontawesome-typeface:5.9.0.2-kotlin")

    // 4. الشرح
    implementation("com.getkeepsafe.taptargetview:taptargetview:1.13.3")

    // 5. السجل
    implementation("com.google.code.gson:gson:2.10.1")
    
    // 6. مكتبات الأبعاد
    implementation("com.intuit.sdp:sdp-android:1.1.0")
    implementation("com.intuit.ssp:ssp-android:1.1.0")

    // الاختبارات
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

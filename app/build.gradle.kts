plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.unibattery"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.unibattery"
    minSdk = 26
    targetSdk = 36
    versionCode = 6
    versionName = "1.0.5"
  }

  // Release signing credentials live in ~/.gradle/gradle.properties, never in the repo. Without them
  // (e.g. a fresh clone) release builds are simply unsigned.
  val storeFile = providers.gradleProperty("UNIBATTERY_STORE_FILE").orNull
  signingConfigs {
    if (storeFile != null) {
      create("release") {
        this.storeFile = file(storeFile)
        storePassword = providers.gradleProperty("UNIBATTERY_STORE_PASSWORD").get()
        keyAlias = providers.gradleProperty("UNIBATTERY_KEY_ALIAS").get()
        keyPassword = providers.gradleProperty("UNIBATTERY_KEY_PASSWORD").get()
      }
    }
  }

  buildTypes {
    release {
      signingConfig = signingConfigs.findByName("release")
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures { compose = true }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.glance.appwidget)
  testImplementation(libs.junit)
}

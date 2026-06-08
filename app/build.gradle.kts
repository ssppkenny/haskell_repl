plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlin.compose)
}

android {
	namespace = "com.example.haskellrepl"
	compileSdk = 35

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	defaultConfig {
		applicationId = "com.example.haskellrepl"
		minSdk = 26
		targetSdk = 35
		versionCode = 1
		versionName = "1.0.0"
		ndk {
			abiFilters += "arm64-v8a"
		}
		externalNativeBuild {
			cmake {
				cppFlags("")
			}
		}
	}

	buildFeatures {
		compose = true
	}

	externalNativeBuild {
		cmake {
			path("src/main/jni/CMakeLists.txt")
			version = "3.22.1"
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			signingConfig = signingConfigs.getByName("release")
		}
	}

	signingConfigs {
		create("release") {
			storeFile = rootProject.file("release.jks")
			storePassword = "android"
			keyAlias = "release"
			keyPassword = "android"
		}
	}
}

kotlin {
	jvmToolchain(17)
}

apply(from = "fetchGhci.gradle.kts")

dependencies {
	implementation(platform(libs.compose.bom))
	implementation(libs.compose.ui)
	implementation(libs.compose.ui.graphics)
	implementation(libs.compose.ui.tooling.preview)
	implementation(libs.compose.material3)
	implementation(libs.compose.foundation)
	implementation(libs.activity.compose)
	implementation(libs.lifecycle.runtime.compose)
	implementation(libs.lifecycle.service)
	implementation(libs.coroutines.android)
	debugImplementation(libs.compose.ui.tooling)
	testImplementation(libs.junit)
	testImplementation(libs.coroutines.test)
	androidTestImplementation(libs.test.ext)
	androidTestImplementation(libs.espresso)
}

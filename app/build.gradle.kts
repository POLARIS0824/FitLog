plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.devtools.ksp)
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

android {
    namespace = "com.example.fitlog"

    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.fitlog"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "2.0"

    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    // Only the isolated benchmark source set has host-side tests.
    testOptions { unitTests.isIncludeAndroidResources = true }


    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true // 开启 R8 代码缩减与优化（模拟线上真实环境）
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 核心技巧：本地实测时直接复用 debug 签名，无需新建私钥
            signingConfig = signingConfigs.getByName("debug")
        }
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
            matchingFallbacks += "release"
            // The production file has a legacy blanket ADK keep. This variant uses a scoped rule set.
            setProguardFiles(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "benchmark-rules.pro"))
        }
    }
}

// Explicit harness invocation only; regular unit tests never read credentials or call a model.
tasks.withType<Test>().configureEach {
    if (name == "testBenchmarkUnitTest") {
        System.getenv("FITLOG_BENCH_SPEC")?.let { systemProperty("fitlog.benchmark.spec", it) }
        if (System.getenv("FITLOG_BENCH_SPEC") != null) outputs.upToDateWhen { false }
        System.getenv("FITLOG_BENCH_PROXY_HOST")?.let {
            systemProperty("https.proxyHost", it)
            systemProperty("http.proxyHost", it)
            val port = requireNotNull(System.getenv("FITLOG_BENCH_PROXY_PORT"))
            systemProperty("https.proxyPort", port)
            systemProperty("http.proxyPort", port)
        }
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("benchmark")) {
        it.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
    onVariants(selector().withBuildType("benchmark")) {
        it.packaging.resources.excludes.add("META-INF/INDEX.LIST")
        it.packaging.resources.merges.addAll("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES")
    }
}

dependencies {
    "benchmarkImplementation"("com.google.adk:google-adk-kotlin-core-android:1.3.1") {
        // Android already provides XmlPullParser; the transitive desktop parser conflicts with R8.
        exclude(group = "net.sf.kxml", module = "kxml2")
        exclude(group = "xmlpull", module = "xmlpull")
    }
    "benchmarkImplementation"("io.ktor:ktor-client-mock")
    "benchmarkImplementation"("androidx.compose.ui:ui-test-manifest")
    "testBenchmarkImplementation"("junit:junit:4.13.2")
    "testBenchmarkImplementation"("org.robolectric:robolectric:4.12.2")
    "testBenchmarkImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    "testBenchmarkImplementation"("androidx.compose.ui:ui-test-junit4")
    constraints {
        "benchmarkImplementation"("androidx.concurrent:concurrent-futures:1.2.0")
    }
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Coroutines are used directly; align dependencies with their BOM.
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.android)

    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigationevent.compose)

    // AI transport: Ktor handles HTTP; the existing JSON library handles the wire format.
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    // Serialization
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.kotlinx.serialization.json)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

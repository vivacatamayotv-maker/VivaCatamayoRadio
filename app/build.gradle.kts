plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vivacatamayo.radio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vivacatamayo.radio"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "1.4.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

val patchV14Source by tasks.registering {
    doLast {
        val sourceFile = file("src/main/java/com/vivacatamayo/radio/MainActivity.kt")
        var source = sourceFile.readText()

        source = source.replace("VivaCatamayo Radio v1.3.0", "VivaCatamayo Radio v1.4.0")

        source = source.replace(
            "it.addListener(listener)\n                renderState()",
            "it.addListener(listener)\n                if (it.playbackState == Player.STATE_IDLE) it.prepare()\n                it.play()\n                renderState()"
        )

        source = source.replace(
            "hero.addView(text(\"RADIO WEB\", 12, true, VCT_CYAN).apply {",
            "hero.addView(android.widget.ImageView(this).apply {\n            setImageResource(R.drawable.vct_logo_official)\n            adjustViewBounds = true\n            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER\n            contentDescription = \"VivaCatamayo TV\"\n        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(110)))\n        hero.addView(space(8))\n        hero.addView(text(\"RADIO WEB\", 12, true, VCT_CYAN).apply {"
        )

        source = source.replace(
            "scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))",
            "scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))\n        ObjectAnimator.ofInt(root.background, \"alpha\", 215, 255).apply {\n            duration = 2800L\n            repeatCount = ValueAnimator.INFINITE\n            repeatMode = ValueAnimator.REVERSE\n            start()\n        }"
        )

        source = source.replace(
            "equalizerAnimator = ObjectAnimator.ofFloat(equalizer, \"alpha\", 0.32f, 1f).apply {",
            "equalizer.alpha = 1f\n            equalizerAnimator = ObjectAnimator.ofFloat(equalizer, \"scaleY\", 0.65f, 1.35f).apply {"
        )
        source = source.replace(
            "equalizer.alpha = 0.50f",
            "equalizer.alpha = 0.50f\n            equalizer.scaleY = 1f"
        )

        sourceFile.writeText(source)
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(patchV14Source)
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.media3:media3-exoplayer:1.7.1")
    implementation("androidx.media3:media3-session:1.7.1")
    implementation("androidx.media3:media3-extractor:1.7.1")
}

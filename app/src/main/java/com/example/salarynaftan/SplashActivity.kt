package com.example.salarynaftan

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Устанавливаем SplashScreen API (Android 12+ получит нативную анимацию)
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // 2. Показываем кастомный layout для всех API-уровней
        setContentView(R.layout.activity_splash)

        // 3. Анимации: логотип + текст + прогресс
        val logo = findViewById<ImageView>(R.id.ivLogo)
        val titleText = findViewById<TextView>(R.id.tvSplashTitle)
        val subtitleText = findViewById<TextView>(R.id.tvSplashSubtitle)
        val progressBar = findViewById<View>(R.id.splashProgress)

        // Начальные состояния
        logo.scaleX = 0f
        logo.scaleY = 0f
        logo.alpha = 0f
        titleText.alpha = 0f
        titleText.translationY = 40f
        subtitleText.alpha = 0f
        subtitleText.translationY = 30f
        progressBar.alpha = 0f

        // Анимация логотипа — пружинный bounce
        val logoScaleX = ObjectAnimator.ofFloat(logo, "scaleX", 0f, 1f).apply {
            duration = 800
            interpolator = OvershootInterpolator(1.4f)
        }
        val logoScaleY = ObjectAnimator.ofFloat(logo, "scaleY", 0f, 1f).apply {
            duration = 800
            interpolator = OvershootInterpolator(1.4f)
        }
        val logoAlpha = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f).apply {
            duration = 400
        }

        // Анимация заголовка — выезжает снизу
        val titleAlpha = ObjectAnimator.ofFloat(titleText, "alpha", 0f, 1f).apply {
            duration = 500
            startDelay = 400
        }
        val titleSlide = ObjectAnimator.ofFloat(titleText, "translationY", 40f, 0f).apply {
            duration = 500
            startDelay = 400
            interpolator = OvershootInterpolator(1.2f)
        }

        // Анимация подзаголовка
        val subAlpha = ObjectAnimator.ofFloat(subtitleText, "alpha", 0f, 1f).apply {
            duration = 500
            startDelay = 600
        }
        val subSlide = ObjectAnimator.ofFloat(subtitleText, "translationY", 30f, 0f).apply {
            duration = 500
            startDelay = 600
            interpolator = OvershootInterpolator(1.1f)
        }

        // Анимация прогресс-бара — fade in
        val progressAlpha = ObjectAnimator.ofFloat(progressBar, "alpha", 0f, 1f).apply {
            duration = 300
            startDelay = 800
        }

        // Запускаем все вместе
        val set = AnimatorSet()
        set.playTogether(
            logoScaleX, logoScaleY, logoAlpha,
            titleAlpha, titleSlide,
            subAlpha, subSlide,
            progressAlpha
        )
        set.start()

        // Переход в MainActivity после анимации
        set.doOnEnd {
            startActivity(Intent(this, MainActivity::class.java))
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
            finish()
        }
    }
}

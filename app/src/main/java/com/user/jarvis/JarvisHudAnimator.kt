package com.user.jarvis

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

object JarvisHudAnimator {

    fun pulseView(view: View, durationMs: Long = 1000) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.05f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.05f, 1f)

        scaleX.repeatCount = ValueAnimator.INFINITE
        scaleY.repeatCount = ValueAnimator.INFINITE

        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = durationMs
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    fun animateTextChange(view: android.widget.TextView, newText: String) {
        val fadeOut = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply { duration = 150 }
        val fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply { duration = 150 }

        fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                view.text = newText
                fadeIn.start()
            }
        })
        fadeOut.start()
    }

    fun shakeError(view: View) {
        val shake = ObjectAnimator.ofFloat(view, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f)
        shake.duration = 400
        shake.start()
    }
}

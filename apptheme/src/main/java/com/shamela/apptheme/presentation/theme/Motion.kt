package com.shamela.apptheme.presentation.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * The app's motion, from Material's published motion tokens (material-components-android,
 * docs/theming/Motion.md) and Android's predictive back guidance. Material's springs have a damping
 * of 0.9 (a barely visible overshoot); here they are critically damped (1.0), so nothing bounces.
 */
object Motion {
    private fun <T> spring(stiffness: Float, visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(Spring.DampingRatioNoBouncy, stiffness, visibilityThreshold)

    /** motionSpringFastSpatial: small components. */
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(1400f)
    /** motionSpringDefaultSpatial: partial-screen surfaces (sheets, bars). */
    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = spring(700f)
    /** motionSpringSlowSpatial: "full screen animations or transitions should use the slow spring". */
    fun <T> slowSpatial(): FiniteAnimationSpec<T> = spring(300f)
    fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(3800f)
    fun <T> defaultEffects(): FiniteAnimationSpec<T> = spring(1600f)
    fun <T> slowEffects(): FiniteAnimationSpec<T> = spring(800f)

    /** Pages: opening a screen and switching tabs (and the navigation bar moving with them). */
    fun <T> page(): FiniteAnimationSpec<T> = slowSpatial()

    /**
     * Going back. The back gesture scrubs it, which needs a fixed length (a spring has none):
     * Material's Long2 (500 ms) with the interpolator Android's predictive back guidance gives
     * "to match the interpolator used for the SystemUI animations". It traces close to [page]'s
     * spring, so going back feels like opening in reverse.
     */
    const val BackMillis = 500
    val BackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
    fun <T> back(): FiniteAnimationSpec<T> = tween(BackMillis, easing = BackEasing)
}

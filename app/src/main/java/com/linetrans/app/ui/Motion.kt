package com.linetrans.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 全应用统一动效参数。
 *
 * 进入用减速曲线（先快后慢，落地柔和），退出用标准曲线且时长更短，
 * 位置与展开类动画统一走柔和弹簧，避免生硬的一次性跳变。
 */
internal object Motion {
    const val FAST = 180
    const val MEDIUM = 300
    const val SLOW = 420

    /** 减速曲线：进入动画。 */
    val Decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 标准曲线：退出与位移。 */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> enter(duration: Int = MEDIUM): FiniteAnimationSpec<T> =
        tween(duration, easing = Decelerate)

    fun <T> exit(duration: Int = FAST + 40): FiniteAnimationSpec<T> =
        tween(duration, easing = Standard)

    fun <T> move(duration: Int = MEDIUM): FiniteAnimationSpec<T> =
        tween(duration, easing = Standard)

    /** 柔和弹簧：展开/收起、列表位移。 */
    fun <T> gentle(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

    /** 数值滚动（进度、费用等）。 */
    fun <T> value(duration: Int = 520): FiniteAnimationSpec<T> =
        tween(duration, easing = Decelerate)
}

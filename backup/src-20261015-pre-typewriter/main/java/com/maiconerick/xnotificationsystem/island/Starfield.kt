package com.maiconerick.xnotificationsystem.island

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private class Star(
    val angle: Float,
    val ring: Float,
    val spin: Float,
    val twinkle: Float,
    val size: Float,
    val color: Color,
)

/**
 * "Warp in" particle field: stars rush in from outside and settle into a slowly rotating
 * elliptical halo around the center, then dim down. Restarts whenever [key] changes.
 */
@Composable
fun Starfield(modifier: Modifier = Modifier, key: Any? = Unit) {
    val stars = remember {
        val rnd = Random(7)
        List(170) {
            val accent = rnd.nextFloat()
            Star(
                angle = rnd.nextFloat() * 2f * PI.toFloat(),
                ring = 0.22f + sqrt(rnd.nextFloat()) * 0.85f,
                spin = (0.02f + rnd.nextFloat() * 0.05f) * if (rnd.nextBoolean()) 1f else -1f,
                twinkle = rnd.nextFloat() * 6.28f,
                size = 0.5f + rnd.nextFloat() * 1.1f,
                color = when {
                    accent < 0.05f -> Color(0xFF7FE7FF)
                    accent < 0.09f -> Color(0xFFFF8FB8)
                    accent < 0.12f -> Color(0xFFB9A4FF)
                    else -> Color.White
                },
            )
        }
    }
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(key) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { time.floatValue = (it - start) / 1_000_000_000f }
        }
    }

    Canvas(modifier) {
        val t = time.floatValue
        val intro = (t / 1.15f).coerceIn(0f, 1f)
        val ease = 1f - (1f - intro).pow(3)
        val reach = 1f + 2.6f * (1f - ease)         // radius multiplier: far → home
        val streak = (1f - ease) * 0.55f             // trail length while rushing in
        val fade = when {
            t < 0.15f -> t / 0.15f
            t < 2.6f -> 1f
            else -> (1f - (t - 2.6f) / 1.2f).coerceAtLeast(0.28f)
        }
        val c = Offset(size.width / 2f, size.height / 2f)
        val rx = size.width * 0.48f
        val ry = size.height * 0.62f
        val px = density

        for (s in stars) {
            val a = s.angle + s.spin * t
            val ca = cos(a); val sa = sin(a)
            val r = s.ring * reach
            val p = Offset(c.x + ca * rx * r, c.y + sa * ry * r)
            val tw = 0.55f + 0.45f * sin(t * 3.2f + s.twinkle)
            val alpha = (fade * tw).coerceIn(0f, 1f)
            if (streak > 0.02f) {
                val r2 = s.ring * (reach + streak)
                val tail = Offset(c.x + ca * rx * r2, c.y + sa * ry * r2)
                drawLine(s.color.copy(alpha = alpha * 0.7f), p, tail, s.size * px * 0.9f, StrokeCap.Round)
            } else {
                drawCircle(s.color.copy(alpha = alpha), radius = s.size * px * 0.75f, center = p)
            }
        }
    }
}

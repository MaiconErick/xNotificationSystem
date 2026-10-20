package com.maiconerick.xnotificationsystem.island

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class Face { Normal, Happy, Dizzy, Squint, Sleep }

/** Mood tint: bottom of the classic body gradient + the halo color. */
enum class Tint(val bottom: Color, val glow: Color) {
    Neutral(Color(0xFFD5D7E6), Color(0xFFFFFFFF)),
    Work(Color(0xFFD3DBF0), Color(0xFFDDE6FF)),
    Amber(Color(0xFFF2C88E), Color(0xFFFFB648)),
    Mint(Color(0xFFBFEBD6), Color(0xFF5EE3A8)),
    Lilac(Color(0xFFD9CFF4), Color(0xFFB9A4FF)),
    Pink(Color(0xFFF2D2DF), Color(0xFFFF8FB8)),
}

enum class Badge(val color: Color) {
    None(Color.Transparent),
    Working(Color(0xFF3B82F6)),
    Alert(Color(0xFFF5A524)),
    Done(Color(0xFF22C55E)),
    Thinking(Color(0xFF8B5CF6)),
}

/** Character designs. Every skin supports every face, badge, hand wave and the eating mouth. */
enum class Skin(
    val label: String,
    val tag: String,
    val eyes: Color,
    val glowEyes: Boolean,
    val halo: Color,
    val hand: Color,
) {
    Classic("Classic", "Original", Color(0xFF16161B), false, Color(0xFFFFFFFF), Color.White),
    Ghost("Ghost", "Halloween", Color(0xFF16161B), false, Color(0xFFBFD4FF), Color(0xFFF4F6FF)),
    Pumpkin("Pumpkin", "Halloween", Color(0xFFFFD25A), true, Color(0xFFFF9A3D), Color(0xFFE5761E)),
    Bat("Bat", "Halloween", Color(0xFFFF5C7F), true, Color(0xFF9B5CFF), Color(0xFF2E2442)),
    Cat("Black cat", "Halloween", Color(0xFFD8F34A), true, Color(0xFF8A7CFF), Color(0xFF221F2A)),
    Umbra("Umbra", "Umbra", Color(0xFFDCCBFF), true, Color(0xFF8B6CFF), Color(0xFF2A1F4D)),
    Moon("Moon", "Umbra", Color(0xFF3A2F1A), false, Color(0xFFFFE08A), Color(0xFFFFE9A8)),
    Slime("Slime", "Fun", Color(0xFF0B3D22), false, Color(0xFF5EF0A0), Color(0xFF7DE9A6)),
}

private val Ink = Color(0xFF16161B)

/**
 * The mascot. Canvas size should be ~1.18:1 (width:height); the body uses 74% of the width so
 * hands, ears, wings, badge and halo have room around it. [skin] defaults to the user's choice.
 */
@Composable
fun Mascot(
    modifier: Modifier = Modifier,
    face: Face = Face.Normal,
    tint: Tint = Tint.Neutral,
    badge: Badge = Badge.None,
    hands: Boolean = false,
    mouth: Float = 0f,
    sparkles: Boolean = false,
    blush: Boolean = false,
    glow: Boolean = true,
    tilt: Float = 0f,
    lookX: Float = 0f,
    lookY: Float = 0f,
    skin: Skin = IslandSettings.skin,
    onTap: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val blink = remember { Animatable(1f) }
    val squish = remember { Animatable(1f) }
    var ouch by remember { mutableStateOf(false) }
    val f = if (ouch) Face.Squint else face

    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2200, 4800))
            blink.animateTo(0.08f, tween(70))
            blink.animateTo(1f, tween(120))
            if (Random.nextFloat() < 0.25f) {
                delay(120)
                blink.animateTo(0.08f, tween(60))
                blink.animateTo(1f, tween(110))
            }
        }
    }
    // A face (or costume) change gets a small "boing" so swaps feel physical.
    LaunchedEffect(f, skin) {
        squish.snapTo(0.88f)
        squish.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 420f))
    }

    val inf = rememberInfiniteTransition(label = "mascot")
    val bob by inf.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob"
    )
    val wave by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(260, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wave"
    )
    val spin by inf.animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spin"
    )
    val beat by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "beat"
    )
    val phase by inf.animateFloat(
        0f, 2f * PI.toFloat(), infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "phase"
    )

    val bottom by animateColorAsState(tint.bottom, tween(450), label = "tint")
    val glowColor by animateColorAsState(
        if (tint == Tint.Neutral) skin.halo else tint.glow, tween(450), label = "glow"
    )
    val happyAmt by animateFloatAsState(if (f == Face.Happy) 1f else 0f, tween(220), label = "happy")
    val dizzyAmt by animateFloatAsState(if (f == Face.Dizzy) 1f else 0f, tween(220), label = "dizzy")
    val squintAmt by animateFloatAsState(if (f == Face.Squint) 1f else 0f, tween(140), label = "squint")
    val sleepAmt by animateFloatAsState(if (f == Face.Sleep) 1f else 0f, tween(400), label = "sleep")
    val handsAmt by animateFloatAsState(if (hands) 1f else 0f, spring(0.5f, 300f), label = "hands")
    val blushAmt by animateFloatAsState(if (blush || f == Face.Dizzy) 1f else 0f, tween(300), label = "blush")
    val sparkAmt by animateFloatAsState(if (sparkles) 1f else 0f, tween(400), label = "spark")
    val badgeScale by animateFloatAsState(
        if (badge == Badge.None) 0f else 1f, spring(0.45f, 380f), label = "badge"
    )
    val badgeColor by animateColorAsState(
        if (badge == Badge.None) Badge.Working.color else badge.color, tween(250), label = "badgeColor"
    )
    val mouthAmt by animateFloatAsState(mouth, spring(0.6f, 420f), label = "mouth")
    val tiltAnim by animateFloatAsState(tilt, spring(0.4f, 200f), label = "tilt")
    val lx by animateFloatAsState(lookX, spring(0.7f, 260f), label = "lx")
    val ly by animateFloatAsState(lookY, spring(0.7f, 260f), label = "ly")

    val tap = if (onTap != null) Modifier.pointerInput(Unit) {
        detectTapGestures {
            onTap()
            scope.launch {
                ouch = true
                delay(380)
                ouch = false
            }
            scope.launch {
                squish.snapTo(0.78f)
                squish.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 520f))
            }
        }
    } else Modifier

    Canvas(modifier.then(tap)) {
        val bw = size.width * 0.74f
        val bh = bw * 0.78f
        val cx = size.width / 2f
        val cy = size.height / 2f + bob * bw * 0.018f * (1f - sleepAmt * 0.6f)
        val b = Body(cx - bw / 2f, cy - bh / 2f, bw, bh)
        val sq = squish.value
        val sx = 1f + (1f - sq) * 0.7f

        if (glow && IslandSettings.mascotGlow) {
            val a = if (skin.glowEyes) 0.42f else 0.34f
            drawCircle(
                Brush.radialGradient(
                    listOf(glowColor.copy(alpha = a), glowColor.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(cx, cy), radius = bw * 0.95f,
                ),
                radius = bw * 0.95f, center = Offset(cx, cy),
            )
        }

        rotate(tiltAnim, pivot = Offset(cx, cy)) {
            scale(sx, sq, pivot = Offset(cx, b.bottom)) {
                drawBehindBody(skin, b, phase, handsAmt, wave)
                drawBody(skin, b, bottom, phase, beat)

                val mo = mouthAmt.coerceIn(0f, 1.2f)
                if (mo > 0.02f) {
                    val mw = bw * 0.78f
                    val mh = bh * 0.34f * mo
                    drawRoundRect(
                        if (skin.glowEyes) Color.Black else Ink,
                        topLeft = Offset(cx - mw / 2f, b.top + bh * 0.1f),
                        size = Size(mw, mh), cornerRadius = CornerRadius(mh * 0.45f),
                    )
                }

                val eyeY = b.top + bh * (0.55f + 0.12f * mo.coerceAtMost(1f)) + ly * bh * 0.06f
                val faceAmt = FaceAmounts(happyAmt, dizzyAmt, squintAmt, sleepAmt)
                drawFace(skin, b, eyeY, lx * bw * 0.06f, blink.value, faceAmt, spin, blushAmt, mo)
            }
        }

        if (badgeScale > 0.01f) drawBadge(b, badge, badgeColor, badgeScale, beat)

        if (sparkAmt > 0.01f) {
            val spots = listOf(
                Offset(-0.55f, -0.42f) to 0f, Offset(0.58f, -0.38f) to 0.33f,
                Offset(0.62f, 0.3f) to 0.66f, Offset(-0.05f, -0.62f) to 0.5f,
            )
            for ((o, ph) in spots) {
                val t = ((beat + ph) % 1f)
                val s = sin(t * PI.toFloat()) * sparkAmt
                drawSparkle(Offset(cx + o.x * bw, cy + o.y * bh), bw * 0.07f * s, Color(0xFF7CF0C0).copy(alpha = s))
            }
        }
    }
}

private class Body(val left: Float, val top: Float, val w: Float, val h: Float) {
    val right get() = left + w
    val bottom get() = top + h
    val cx get() = left + w / 2f
    val cy get() = top + h / 2f
}

private class FaceAmounts(val happy: Float, val dizzy: Float, val squint: Float, val sleep: Float) {
    val normal get() = (1f - happy - dizzy - squint - sleep).coerceIn(0f, 1f)
}

// ---- bodies ---------------------------------------------------------------------------------------

/** Things that sit behind the body: hands, bat wings, cat tail. */
private fun DrawScope.drawBehindBody(skin: Skin, b: Body, phase: Float, handsAmt: Float, wave: Float) {
    when (skin) {
        Skin.Bat -> {
            val flap = sin(phase * 2f) * (10f + 14f * handsAmt)
            for (side in listOf(-1f, 1f)) {
                val shoulder = Offset(b.cx + side * b.w * 0.36f, b.cy - b.h * 0.05f)
                rotate(side * flap, shoulder) {
                    val tipX = shoulder.x + side * b.w * 0.62f
                    val p = Path().apply {
                        moveTo(shoulder.x, shoulder.y - b.h * 0.18f)
                        quadraticBezierTo(shoulder.x + side * b.w * 0.3f, shoulder.y - b.h * 0.55f, tipX, shoulder.y - b.h * 0.42f)
                        // scalloped lower edge back to the body
                        quadraticBezierTo(tipX - side * b.w * 0.06f, shoulder.y - b.h * 0.05f, shoulder.x + side * b.w * 0.42f, shoulder.y + b.h * 0.02f)
                        quadraticBezierTo(shoulder.x + side * b.w * 0.36f, shoulder.y - b.h * 0.12f, shoulder.x + side * b.w * 0.24f, shoulder.y + b.h * 0.14f)
                        quadraticBezierTo(shoulder.x + side * b.w * 0.16f, shoulder.y - b.h * 0.02f, shoulder.x, shoulder.y + b.h * 0.26f)
                        close()
                    }
                    drawPath(p, Color(0xFF2B2140))
                    drawPath(p, Color(0xFF7A5CC8).copy(alpha = 0.35f), style = Stroke(b.w * 0.012f))
                }
            }
        }
        Skin.Cat -> {
            val sway = sin(phase) * b.h * 0.12f
            val p = Path().apply {
                moveTo(b.right - b.w * 0.12f, b.bottom - b.h * 0.12f)
                cubicTo(
                    b.right + b.w * 0.22f, b.bottom - b.h * 0.05f,
                    b.right + b.w * 0.12f, b.top + b.h * 0.25f + sway,
                    b.right + b.w * 0.26f, b.top + b.h * 0.02f + sway,
                )
            }
            drawPath(p, Color(0xFF1B1922), style = Stroke(b.w * 0.09f, cap = StrokeCap.Round))
        }
        else -> Unit
    }
    if (handsAmt > 0.01f && skin != Skin.Bat) drawHands(b, handsAmt, wave, skin.hand)
}

private fun DrawScope.drawBody(skin: Skin, b: Body, moodBottom: Color, phase: Float, beat: Float) {
    when (skin) {
        Skin.Classic -> {
            drawRoundRect(
                Brush.verticalGradient(0f to Color.White, 0.45f to Color(0xFFFBFBFD), 1f to moodBottom, startY = b.top, endY = b.bottom),
                topLeft = Offset(b.left, b.top), size = Size(b.w, b.h), cornerRadius = CornerRadius(b.w * 0.3f),
            )
            drawHighlight(b)
        }

        Skin.Ghost -> {
            val p = ghostPath(b, b.top + b.h * 1.08f, b.h * 0.1f, phase)
            drawPath(p, Brush.verticalGradient(listOf(Color.White, Color(0xFFE3E8F8)), startY = b.top, endY = b.bottom))
            drawPath(p, Color(0xFFBFD4FF).copy(alpha = 0.35f), style = Stroke(b.w * 0.012f))
            drawOval(Color.White.copy(alpha = 0.9f), Offset(b.left + b.w * 0.2f, b.top + b.h * 0.08f), Size(b.w * 0.22f, b.h * 0.12f))
        }

        Skin.Pumpkin -> {
            val top = b.top + b.h * 0.04f
            val h = b.h * 0.98f
            val sideBrush = Brush.verticalGradient(listOf(Color(0xFFE47A22), Color(0xFFB4480E)), startY = top, endY = top + h)
            drawOval(sideBrush, Offset(b.left - b.w * 0.02f, top + h * 0.04f), Size(b.w * 0.6f, h * 0.94f))
            drawOval(sideBrush, Offset(b.right - b.w * 0.58f, top + h * 0.04f), Size(b.w * 0.6f, h * 0.94f))
            drawOval(
                Brush.verticalGradient(listOf(Color(0xFFFFA43A), Color(0xFFD9661A)), startY = top, endY = top + h),
                Offset(b.cx - b.w * 0.3f, top), Size(b.w * 0.6f, h),
            )
            for (side in listOf(-1f, 1f)) {
                val rib = Path().apply {
                    moveTo(b.cx + side * b.w * 0.12f, top + h * 0.08f)
                    quadraticBezierTo(b.cx + side * b.w * 0.2f, top + h * 0.5f, b.cx + side * b.w * 0.12f, top + h * 0.92f)
                }
                drawPath(rib, Color(0xFF9A3C0C).copy(alpha = 0.45f), style = Stroke(b.w * 0.014f, cap = StrokeCap.Round))
            }
            // stem + curly vine
            rotate(8f, Offset(b.cx, top)) {
                drawRoundRect(
                    Color(0xFF55702A), Offset(b.cx - b.w * 0.05f, top - b.h * 0.16f), Size(b.w * 0.1f, b.h * 0.22f),
                    CornerRadius(b.w * 0.03f),
                )
            }
            drawArc(
                Color(0xFF6E9A34), 180f, 220f, false, Offset(b.cx + b.w * 0.02f, top - b.h * 0.14f),
                Size(b.w * 0.14f, b.h * 0.14f), style = Stroke(b.w * 0.018f, cap = StrokeCap.Round),
            )
        }

        Skin.Bat -> {
            for (side in listOf(-1f, 1f)) {
                val ear = Path().apply {
                    moveTo(b.cx + side * b.w * 0.36f, b.top + b.h * 0.18f)
                    lineTo(b.cx + side * b.w * 0.3f, b.top - b.h * 0.3f)
                    lineTo(b.cx + side * b.w * 0.1f, b.top + b.h * 0.06f)
                    close()
                }
                drawPath(ear, Color(0xFF3A2D52))
            }
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFF433460), Color(0xFF1F1730)), startY = b.top, endY = b.bottom),
                topLeft = Offset(b.left, b.top), size = Size(b.w, b.h), cornerRadius = CornerRadius(b.w * 0.32f),
            )
            drawRoundRect(
                Color(0xFF9B7CFF).copy(alpha = 0.25f), Offset(b.left, b.top), Size(b.w, b.h),
                CornerRadius(b.w * 0.32f), style = Stroke(b.w * 0.012f),
            )
        }

        Skin.Cat -> {
            for (side in listOf(-1f, 1f)) {
                val outer = Path().apply {
                    moveTo(b.cx + side * b.w * 0.42f, b.top + b.h * 0.22f)
                    lineTo(b.cx + side * b.w * 0.36f, b.top - b.h * 0.26f)
                    lineTo(b.cx + side * b.w * 0.08f, b.top + b.h * 0.04f)
                    close()
                }
                drawPath(outer, Color(0xFF26232E))
                val inner = Path().apply {
                    moveTo(b.cx + side * b.w * 0.36f, b.top + b.h * 0.12f)
                    lineTo(b.cx + side * b.w * 0.33f, b.top - b.h * 0.13f)
                    lineTo(b.cx + side * b.w * 0.17f, b.top + b.h * 0.04f)
                    close()
                }
                drawPath(inner, Color(0xFFFF8FB0).copy(alpha = 0.55f))
            }
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFF2E2A38), Color(0xFF15131B)), startY = b.top, endY = b.bottom),
                topLeft = Offset(b.left, b.top), size = Size(b.w, b.h), cornerRadius = CornerRadius(b.w * 0.34f),
            )
        }

        Skin.Umbra -> {
            val p = ghostPath(b, b.top + b.h * 1.02f, b.h * 0.08f, phase)
            drawPath(
                p,
                Brush.radialGradient(
                    listOf(Color(0xFF5A3FA8), Color(0xFF231944), Color(0xFF110B22)),
                    center = Offset(b.cx, b.top + b.h * 0.3f), radius = b.w * 0.75f,
                ),
            )
            drawPath(p, Color(0xFFA48BFF).copy(alpha = 0.55f), style = Stroke(b.w * 0.016f))
            // little stars inside the shadow
            listOf(Offset(0.22f, 0.25f) to 0f, Offset(0.78f, 0.3f) to 0.4f, Offset(0.68f, 0.82f) to 0.75f).forEach { (o, ph) ->
                val a = (sin(((beat + ph) % 1f) * PI.toFloat()))
                drawCircle(Color.White.copy(alpha = 0.25f + 0.6f * a), b.w * 0.012f, Offset(b.left + b.w * o.x, b.top + b.h * o.y))
            }
            // crescent moon hovering by the head
            val mc = Offset(b.right - b.w * 0.12f, b.top - b.h * 0.12f + sin(phase) * b.h * 0.03f)
            val r = b.w * 0.11f
            val moon = Path.combine(
                PathOperation.Difference,
                Path().apply { addOval(Rect(mc, r)) },
                Path().apply { addOval(Rect(Offset(mc.x + r * 0.45f, mc.y - r * 0.3f), r * 0.85f)) },
            )
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFFE08A).copy(alpha = 0.35f), Color.Transparent), center = mc, radius = r * 2f),
                r * 2f, mc,
            )
            drawPath(moon, Color(0xFFFFE08A))
        }

        Skin.Moon -> {
            val top = b.top - b.h * 0.04f
            val h = b.h * 1.08f
            drawOval(
                Brush.verticalGradient(listOf(Color(0xFFFFF4C9), Color(0xFFF0C062)), startY = top, endY = top + h),
                Offset(b.left, top), Size(b.w, h),
            )
            listOf(Offset(0.22f, 0.3f) to 0.09f, Offset(0.75f, 0.22f) to 0.06f, Offset(0.8f, 0.75f) to 0.08f, Offset(0.3f, 0.82f) to 0.05f)
                .forEach { (o, r) ->
                    drawCircle(Color(0xFFDDA64A).copy(alpha = 0.35f), b.w * r, Offset(b.left + b.w * o.x, top + h * o.y))
                }
            // two little stars around it
            listOf(Offset(-0.62f, -0.3f) to 0f, Offset(0.66f, -0.5f) to 0.5f).forEach { (o, ph) ->
                val s = sin(((beat + ph) % 1f) * PI.toFloat())
                drawSparkle(Offset(b.cx + o.x * b.w, b.cy + o.y * b.h), b.w * 0.06f * (0.4f + 0.6f * s), Color(0xFFFFE08A).copy(alpha = 0.5f + 0.5f * s))
            }
        }

        Skin.Slime -> {
            val drop = Path().apply {
                moveTo(b.cx, b.top - b.h * 0.16f)
                cubicTo(b.cx + b.w * 0.14f, b.top + b.h * 0.05f, b.right + b.w * 0.06f, b.top + b.h * 0.35f, b.right + b.w * 0.05f, b.bottom - b.h * 0.12f)
                quadraticBezierTo(b.right + b.w * 0.05f, b.bottom, b.right - b.w * 0.12f, b.bottom)
                lineTo(b.left + b.w * 0.12f, b.bottom)
                quadraticBezierTo(b.left - b.w * 0.05f, b.bottom, b.left - b.w * 0.05f, b.bottom - b.h * 0.12f)
                cubicTo(b.left - b.w * 0.06f, b.top + b.h * 0.35f, b.cx - b.w * 0.14f, b.top + b.h * 0.05f, b.cx, b.top - b.h * 0.16f)
                close()
            }
            drawPath(drop, Brush.verticalGradient(listOf(Color(0xFFA8F9C4), Color(0xFF2FBF71)), startY = b.top, endY = b.bottom), alpha = 0.95f)
            drawOval(Color.White.copy(alpha = 0.5f), Offset(b.left + b.w * 0.12f, b.top + b.h * 0.34f), Size(b.w * 0.13f, b.h * 0.2f))
            drawCircle(Color.White.copy(alpha = 0.45f), b.w * 0.022f, Offset(b.left + b.w * 0.2f, b.top + b.h * 0.62f))
        }
    }
}

private fun ghostPath(b: Body, yb: Float, amp: Float, phase: Float): Path {
    val r = b.w / 2f
    val seg = b.w / 3f
    return Path().apply {
        moveTo(b.left, b.top + r)
        arcTo(Rect(b.left, b.top, b.right, b.top + b.w), 180f, 180f, false)
        lineTo(b.right, yb - amp)
        for (i in 0..2) {
            val x0 = b.right - seg * i
            val x1 = x0 - seg
            val dip = amp * (1.4f + 0.5f * sin(phase + i * 1.7f))
            quadraticBezierTo((x0 + x1) / 2f, yb + dip, x1, yb - amp)
        }
        close()
    }
}

private fun DrawScope.drawHighlight(b: Body) {
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), Color.Transparent), startY = b.top, endY = b.top + b.h * 0.35f),
        topLeft = Offset(b.left + b.w * 0.08f, b.top + b.h * 0.03f), size = Size(b.w * 0.84f, b.h * 0.3f),
        cornerRadius = CornerRadius(b.w * 0.2f),
    )
}

private fun DrawScope.drawHands(b: Body, amt: Float, wave: Float, color: Color) {
    val r = b.w * 0.085f * amt
    drawCircle(color, radius = r, center = Offset(b.left - r * 0.55f, b.cy + b.h * 0.22f))
    val angle = -35f + wave * 50f
    val pivot = Offset(b.right - r * 0.2f, b.cy + b.h * 0.05f)
    rotate(angle, pivot) {
        drawRoundRect(
            color, topLeft = Offset(pivot.x, pivot.y - r * 0.8f),
            size = Size(r * 2.6f, r * 1.6f), cornerRadius = CornerRadius(r * 0.8f),
        )
    }
}

// ---- faces ----------------------------------------------------------------------------------------

private fun DrawScope.drawFace(
    skin: Skin, b: Body, eyeY: Float, shift: Float, blink: Float, fa: FaceAmounts, spin: Float, blush: Float, mouth: Float,
) {
    val ex = b.w * 0.15f
    val eyeW = b.w * 0.085f
    val col = skin.eyes
    val stroke = b.w * 0.035f

    for (side in listOf(-1f, 1f)) {
        val ecx = b.cx + side * ex + shift
        if (skin.glowEyes && fa.sleep < 0.9f) {
            drawCircle(
                Brush.radialGradient(listOf(col.copy(alpha = 0.45f), Color.Transparent), center = Offset(ecx, eyeY), radius = b.w * 0.13f),
                radius = b.w * 0.13f, center = Offset(ecx, eyeY),
            )
        }
        if (fa.normal > 0.01f) {
            val a = fa.normal
            val eh = b.w * 0.13f * blink * a.coerceAtLeast(0.15f)
            when (skin) {
                Skin.Pumpkin -> {
                    val w = b.w * 0.14f
                    val tri = Path().apply {
                        moveTo(ecx, eyeY - eh * 0.75f)
                        lineTo(ecx + w / 2f, eyeY + eh * 0.45f)
                        lineTo(ecx - w / 2f, eyeY + eh * 0.45f)
                        close()
                    }
                    drawPath(tri, col.copy(alpha = a))
                }
                Skin.Cat -> {
                    val w = b.w * 0.12f
                    drawOval(col.copy(alpha = a), Offset(ecx - w / 2f, eyeY - eh * 0.6f), Size(w, eh * 1.2f))
                    drawRoundRect(
                        Ink.copy(alpha = a), Offset(ecx - w * 0.1f, eyeY - eh * 0.48f), Size(w * 0.2f, eh * 0.96f),
                        CornerRadius(w * 0.1f),
                    )
                }
                else -> drawRoundRect(
                    col.copy(alpha = a), topLeft = Offset(ecx - eyeW / 2f, eyeY - eh / 2f),
                    size = Size(eyeW, eh), cornerRadius = CornerRadius(eyeW / 2f),
                )
            }
        }
        if (fa.happy > 0.01f) {
            val r = b.w * 0.065f
            drawArc(
                col.copy(alpha = fa.happy), startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(ecx - r, eyeY - r * 0.55f), size = Size(r * 2f, r * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        if (fa.sleep > 0.01f) {
            val r = b.w * 0.065f
            drawArc(
                col.copy(alpha = fa.sleep), startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(ecx - r, eyeY - r * 1.5f), size = Size(r * 2f, r * 2f),
                style = Stroke(width = stroke * 0.9f, cap = StrokeCap.Round),
            )
        }
        if (fa.squint > 0.01f) {
            val r = b.w * 0.055f
            val p = Path().apply {
                moveTo(ecx - side * r, eyeY - r)
                lineTo(ecx + side * r * 0.6f, eyeY)
                lineTo(ecx - side * r, eyeY + r)
            }
            drawPath(p, col.copy(alpha = fa.squint), style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        if (fa.dizzy > 0.01f) {
            drawSpiral(Offset(ecx, eyeY), b.w * 0.07f, spin * side, col.copy(alpha = fa.dizzy), b.w * 0.022f)
        }
        if (blush > 0.01f) {
            drawOval(
                Color(0xFFFF8FB0).copy(alpha = 0.55f * blush),
                topLeft = Offset(ecx + side * b.w * 0.07f - b.w * 0.05f, eyeY + b.h * 0.1f),
                size = Size(b.w * 0.1f, b.h * 0.07f),
            )
        }
    }

    // skin-specific mouths (hidden while eating or asleep)
    val ma = (1f - mouth).coerceIn(0f, 1f) * (1f - fa.sleep)
    if (ma < 0.02f) return
    val my = eyeY + b.h * 0.2f
    when (skin) {
        Skin.Ghost -> drawOval(Ink.copy(alpha = ma), Offset(b.cx + shift - b.w * 0.035f, my - b.h * 0.03f), Size(b.w * 0.07f, b.h * 0.1f))
        Skin.Pumpkin -> {
            val w = b.w * 0.42f
            val p = Path().apply {
                moveTo(b.cx - w / 2f, my - b.h * 0.03f)
                val teeth = 5
                for (i in 1..teeth) {
                    val x = b.cx - w / 2f + w * i / teeth
                    lineTo(x - w / teeth / 2f, my + if (i % 2 == 0) -b.h * 0.07f else b.h * 0.02f)
                    lineTo(x, my - b.h * 0.03f)
                }
                quadraticBezierTo(b.cx, my + b.h * 0.2f, b.cx - w / 2f, my - b.h * 0.03f)
                close()
            }
            drawPath(p, skin.eyes.copy(alpha = ma))
        }
        Skin.Bat -> for (side in listOf(-1f, 1f)) {
            val fx = b.cx + side * b.w * 0.05f
            val p = Path().apply {
                moveTo(fx - b.w * 0.03f, my - b.h * 0.02f)
                lineTo(fx + b.w * 0.03f, my - b.h * 0.02f)
                lineTo(fx, my + b.h * 0.08f)
                close()
            }
            drawPath(p, Color.White.copy(alpha = ma))
        }
        Skin.Cat -> {
            val nose = Path().apply {
                moveTo(b.cx - b.w * 0.03f, my - b.h * 0.06f)
                lineTo(b.cx + b.w * 0.03f, my - b.h * 0.06f)
                lineTo(b.cx, my - b.h * 0.01f)
                close()
            }
            drawPath(nose, Color(0xFFFF8FB0).copy(alpha = ma))
            for (side in listOf(-1f, 1f)) {
                for (k in -1..1) {
                    drawLine(
                        Color(0xFFB8B4C8).copy(alpha = 0.45f * ma),
                        Offset(b.cx + side * b.w * 0.22f, my - b.h * 0.02f + k * b.h * 0.05f),
                        Offset(b.cx + side * b.w * 0.5f, my - b.h * 0.05f + k * b.h * 0.09f),
                        b.w * 0.01f, StrokeCap.Round,
                    )
                }
            }
        }
        else -> Unit
    }
}

private fun DrawScope.drawBadge(b: Body, badge: Badge, color: Color, scale: Float, beat: Float) {
    val r = b.w * 0.12f
    val c = Offset(b.left + b.w * 0.03f, b.top + b.w * 0.03f)
    scale(scale, pivot = c) {
        drawCircle(Color(0xFF0B0B0E), radius = r * 1.28f, center = c)
        drawCircle(color, radius = r, center = c)
        when (badge) {
            Badge.Working, Badge.Thinking -> for (i in 0..2) {
                val ph = ((beat * 3f - i) % 3f).let { if (it < 0) it + 3f else it }
                val lift = if (ph < 1f) sin(ph * PI.toFloat()) * r * 0.18f else 0f
                drawCircle(Color.White, radius = r * 0.13f, center = Offset(c.x + (i - 1) * r * 0.38f, c.y - lift))
            }
            Badge.Alert -> {
                drawLine(Ink, Offset(c.x, c.y - r * 0.5f), Offset(c.x, c.y + r * 0.12f), r * 0.24f, StrokeCap.Round)
                drawCircle(Ink, radius = r * 0.13f, center = Offset(c.x, c.y + r * 0.48f))
            }
            else -> Unit
        }
    }
}

private fun DrawScope.drawSpiral(c: Offset, radius: Float, rotDeg: Float, color: Color, stroke: Float) {
    val p = Path()
    val turns = 2.2f
    val steps = 40
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val a = t * turns * 2f * PI.toFloat() + rotDeg * PI.toFloat() / 180f
        val rr = radius * t
        val x = c.x + cos(a) * rr
        val y = c.y + sin(a) * rr
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    drawPath(p, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
}

private fun DrawScope.drawSparkle(c: Offset, r: Float, color: Color) {
    if (r <= 0.1f) return
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticBezierTo(c.x, c.y, c.x + r, c.y)
        quadraticBezierTo(c.x, c.y, c.x, c.y + r)
        quadraticBezierTo(c.x, c.y, c.x - r, c.y)
        quadraticBezierTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(p, color)
}

/** Flat mini face used for the integration chips. */
fun DrawScope.drawMiniFace(topLeft: Offset, w: Float, h: Float, body: Color, eyes: Color = Ink) {
    drawRoundRect(body, topLeft, Size(w, h), CornerRadius(h * 0.42f))
    val ew = w * 0.12f
    val eh = h * 0.32f
    for (side in listOf(-1f, 1f)) {
        val ex = topLeft.x + w / 2f + side * w * 0.17f
        drawRoundRect(
            eyes, Offset(ex - ew / 2f, topLeft.y + h * 0.5f - eh / 2f), Size(ew, eh), CornerRadius(ew / 2f)
        )
    }
}

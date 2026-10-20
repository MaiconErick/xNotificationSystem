package com.maiconerick.xnotificationsystem.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin

private val CardShape = RoundedCornerShape(22.dp)
private val GlowAmber = Color(0xFFB8741C)
private val GlowGreen = Color(0xFF15835C)
private val GlowPink = Color(0xFFA62E66)
private val GlowIndigo = Color(0xFF3A3DB0)

private fun Modifier.cardPad() = fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp)

/** Mascot size scaled by the user's preference. */
private fun Dp.ms() = this * IslandSettings.mascotScale

/**
 * Gap between the mascot and the text next to it. The bat's wings are drawn well outside the
 * mascot's box, so the text needs extra room on that skin or the wing tip overlaps it.
 */
@Composable
internal fun mascotGap(base: Dp = 10.dp, mascotWidth: Dp = 70.dp, skin: Skin = IslandSettings.skin): Dp =
    base + if (skin == Skin.Bat) mascotWidth * 0.24f * IslandSettings.mascotScale else 0.dp

/** Card height scaled by the "island height" preference. */
private fun Dp.hs() = this * IslandSettings.islandHeight

/**
 * Rounded inner card. It paints its own background (no clip) so the mascot can pop out of it,
 * with an optional breathing radial glow and dashed border.
 */
@Composable
private fun Card(
    modifier: Modifier = Modifier,
    glow: Color? = null,
    glowAt: Offset = Offset(0.5f, 1.15f),
    glowRadius: Float = 0.7f,
    dashed: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val inf = rememberInfiniteTransition(label = "card")
    val breathe by inf.animateFloat(
        0.75f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe"
    )
    val glowIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) { glowIn.animateTo(1f, tween(700)) }
    Box(
        modifier.drawBehind {
            val r = 22.dp.toPx()
            val path = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
            }
            drawPath(path, CardBg)
            if (glow != null) {
                val c = Offset(size.width * glowAt.x, size.height * glowAt.y)
                val radius = size.width * glowRadius
                clipPath(path) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(glow.copy(alpha = (0.55f * breathe * glowIn.value * IslandSettings.glow).coerceIn(0f, 1f)), Color.Transparent),
                            center = c, radius = radius,
                        ),
                        radius = radius, center = c,
                    )
                }
            }
            if (dashed != null) {
                val inset = 1.dp.toPx()
                drawRoundRect(
                    dashed, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2),
                    CornerRadius(r - inset),
                    style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            } else {
                drawPath(path, Color.White.copy(alpha = 0.05f), style = Stroke(1.dp.toPx()))
            }
        },
        content = content,
    )
}

// ---- small building blocks ---------------------------------------------------------------------

@Composable
private fun Dot(color: Color = TextPrimary) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
}

@Composable
private fun Header(name: String, detail: String, dot: Color = TextPrimary, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(dot)
        Spacer(Modifier.width(6.dp))
        Text(name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text(
            detail, color = TextSecondary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun PillButton(
    text: String,
    light: Boolean = false,
    keyHint: String? = null,
    dropdown: Boolean = false,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(0.5f, 600f), label = "press")
    val fg = if (light) Color(0xFF111114) else TextPrimary
    Row(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (light) Color(0xFFF3F3F6) else Color(0xFF232328))
            .clickable(src, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (keyHint != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .border(1.dp, fg.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp),
            ) {
                Text(keyHint, color = fg.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (dropdown) {
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = fg.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun SmallChip(text: String) {
    Box(
        Modifier.clip(CircleShape).background(Color(0xFF1E1E23)).padding(horizontal = 9.dp, vertical = 3.dp)
    ) { Text(text, color = TextSecondary, fontSize = 10.5.sp) }
}

/** A white document with a folded corner and a red label — the "quote.pdf" icon. */
@Composable
private fun FileIcon(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val fold = w * 0.3f
        val body = Path().apply {
            moveTo(0f, w * 0.12f)
            quadraticBezierTo(0f, 0f, w * 0.12f, 0f)
            lineTo(w - fold, 0f)
            lineTo(w, fold)
            lineTo(w, h - w * 0.12f)
            quadraticBezierTo(w, h, w - w * 0.12f, h)
            lineTo(w * 0.12f, h)
            quadraticBezierTo(0f, h, 0f, h - w * 0.12f)
            close()
        }
        drawPath(body, Color(0xFFF4F4F7))
        drawPath(Path().apply {
            moveTo(w - fold, 0f); lineTo(w - fold, fold); lineTo(w, fold); close()
        }, Color(0xFFCFCFD6))
        drawRoundRect(
            Color(0xFFE5484D), Offset(w * 0.16f, h * 0.48f), Size(w * 0.68f, h * 0.16f), CornerRadius(h * 0.04f)
        )
    }
}

@Composable
private fun FileChip(name: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(Color(0xFF222228)).padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIcon(Modifier.size(10.dp, 13.dp))
        Spacer(Modifier.width(6.dp))
        Text(name, color = TextPrimary, fontSize = 11.5.sp)
    }
}

/** Text with a light sweep across it — used for the step that is currently running. */
@Composable
private fun ShimmerText(text: String, size: TextUnit, color: Color, active: Boolean) {
    val inf = rememberInfiniteTransition(label = "shimmer")
    val x by inf.animateFloat(
        -200f, 900f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "x"
    )
    val style = if (active) TextStyle(
        brush = Brush.linearGradient(
            listOf(color.copy(alpha = 0.55f), Color.White, color.copy(alpha = 0.55f)),
            start = Offset(x, 0f), end = Offset(x + 220f, 0f),
        ),
        fontSize = size, fontWeight = FontWeight.Medium,
    ) else TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Medium)
    Text(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private fun stepLabel(i: Int) = SESSION_STEPS[i].let { "${it.first} · ${it.second}" }

/** Rotates the mascot through a quick "look at me" wiggle when a card appears. */
@Composable
private fun rememberWiggle(vararg seq: Float, stepMs: Long = 110): Float {
    var tilt by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        delay(250)
        for (t in seq) { tilt = t; delay(stepMs) }
        tilt = 0f
    }
    return tilt
}

// ---- Home --------------------------------------------------------------------------------------

@Composable
fun HomeCard(s: IslandState) {
    var hands by remember { mutableStateOf(false) }
    var face by remember { mutableStateOf(Face.Normal) }
    val hop = remember { Animatable(0f) }
    LaunchedEffect(s.nonce) {
        delay(350)
        hands = true; face = Face.Happy
        delay(1500)
        face = Face.Normal
        delay(350)
        hands = false
        delay(400)
        hop.snapTo(0f)
        hop.animateTo(1f, tween(820, easing = LinearEasing))
        hop.snapTo(0f)
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val jump = with(density) { 22.dp.toPx() }
    val p = hop.value
    fun hopY(t: Float) = -sin(t * Math.PI.toFloat()) * jump
    fun hopRot(t: Float) = t * 360f

    val greeting = s.greeting
    LaunchedEffect(greeting) { if (greeting.isNotEmpty()) hands = true }
    Card(Modifier.cardPad().height(104.dp.hs())) {
        if (IslandSettings.starfield) Starfield(Modifier.matchParentSize().clip(CardShape), key = s.nonce)
        // after-images trailing the flip
        if (p > 0f && greeting.isEmpty()) {
            Canvas(Modifier.align(Alignment.Center).size(84.dp.ms(), 71.dp.ms())) {
                for (k in 1..6) {
                    val t = p - k * 0.045f
                    if (t <= 0f) continue
                    val a = 0.22f * (1f - k / 7f)
                    val bw = size.width * 0.74f
                    val bh = bw * 0.78f
                    val c = Offset(size.width / 2f, size.height / 2f + hopY(t))
                    rotate(hopRot(t), c) {
                        drawRoundRect(
                            Color.White.copy(alpha = a), Offset(c.x - bw / 2f, c.y - bh / 2f), Size(bw, bh), CornerRadius(bw * 0.3f)
                        )
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            val homeSkin = if (greeting.isNotEmpty()) IslandSettings.skinFor(Category.Greeting) else IslandSettings.skin
            Mascot(
                Modifier
                    .offset { IntOffset(0, if (greeting.isEmpty()) hopY(p).roundToInt() else 0) }
                    .graphicsLayer { rotationZ = if (greeting.isEmpty()) hopRot(p) else 0f }
                    .mascotShared()
                    .size(84.dp.ms(), 71.dp.ms()),
                skin = homeSkin,
                face = face, hands = hands || greeting.isNotEmpty(), onTap = Island::poke,
            )
            if (greeting.isNotEmpty()) {
                Spacer(Modifier.width(mascotGap(4.dp, 84.dp, homeSkin)))
                SpeechBubble(greeting)
            }
        }
    }
}

/** Pops in next to the mascot, tail pointing at it, text typed out. */
@Composable
private fun SpeechBubble(text: String) {
    val pop = remember { Animatable(0f) }
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        delay(380)
        launch { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 420f)) }
        delay(120)
        while (shown < text.length) { shown++; delay(28) }
    }
    val bubble = Color(0xFFF4F4F8)
    Row(
        Modifier.graphicsLayer {
            scaleX = pop.value; scaleY = pop.value
            alpha = pop.value.coerceIn(0f, 1f)
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(8.dp, 12.dp)) {
            drawPath(Path().apply {
                moveTo(size.width, 0f); lineTo(0f, size.height / 2f); lineTo(size.width, size.height); close()
            }, bubble)
        }
        Box(
            Modifier.clip(RoundedCornerShape(16.dp)).background(bubble)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // full text keeps the bubble from resizing while typing
            Text(text, color = Color.Transparent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(text.take(shown), color = Color(0xFF111114), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

// ---- Agent session -----------------------------------------------------------------------------

private val Integrations = listOf(
    "Stripe" to Color(0xFF3B82F6),
    "GitHub" to Color(0xFFE5534B),
    "n8n" to Color(0xFFF5A524),
    "Vercel" to Color(0xFF7C5CFF),
)

@Composable
fun SessionCard(s: IslandState) {
    BoxWithConstraints(Modifier.cardPad()) {
        val wide = maxWidth >= 560.dp
        Row(Modifier.fillMaxWidth().height(98.dp.hs().coerceAtLeast(70.dp.ms() + 20.dp))) {
            Card(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxHeight().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Mascot(
                        Modifier.mascotShared().size(70.dp.ms(), 59.dp.ms()),
                        tint = if (s.pushed) Tint.Mint else Tint.Work,
                        badge = if (s.pushed) Badge.Done else Badge.Working,
                        face = if (s.pushed) Face.Happy else Face.Normal,
                        onTap = Island::poke,
                    )
                    Spacer(Modifier.width(mascotGap()))
                    Column(Modifier.weight(1f)) {
                        Header("korus", "Claude Code") {
                            Text("${s.step + 1}/${SESSION_STEPS.size}", color = TextDim, fontSize = 11.sp)
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier.size(16.dp).clip(CircleShape).background(Color(0xFF1F1F24))
                                    .clickable { Island.openApp() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.NorthEast, null, tint = TextSecondary, modifier = Modifier.size(10.dp))
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        AnimatedContent(
                            targetState = s.step to s.pushed,
                            transitionSpec = {
                                (slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 2 } +
                                        fadeIn(tween(220, delayMillis = 60))) togetherWith
                                        (slideOutVertically(tween(220)) { -it / 2 } + fadeOut(tween(160))) using
                                        SizeTransform(clip = false)
                            },
                            label = "steps",
                        ) { (step, pushed) ->
                            Column {
                                if (step > 0) {
                                    Text(
                                        "✓ ${stepLabel(step - 1)}", color = TextDim, fontSize = 11.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("› ", color = TextPrimary, fontSize = 13.sp)
                                    ShimmerText(stepLabel(step), 13.sp, TextPrimary, active = !pushed)
                                }
                                if (pushed) {
                                    Text("   main → main", color = Color(0xFF5EE3A8).copy(alpha = 0.8f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Card(Modifier.width(if (wide) 260.dp else 100.dp).fillMaxHeight()) {
                IntegrationsGrid(wide, activeIndex = if (s.step == 3) 1 else if (s.step == 2) 3 else -1)
            }
        }
    }
}

@Composable
private fun BoxScope.IntegrationsGrid(wide: Boolean, activeIndex: Int) {
    val inf = rememberInfiniteTransition(label = "integ")
    val pulse by inf.animateFloat(0.25f, 0.7f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    Column(
        Modifier.align(Alignment.Center).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (col in 0..1) {
                    val i = row * 2 + col
                    val (name, color) = Integrations[i]
                    val active = i == activeIndex
                    Row(
                        Modifier
                            .then(if (wide) Modifier.width(114.dp) else Modifier)
                            .clip(CircleShape)
                            .background(Color(0xFF1C1C21))
                            .border(1.dp, if (active) color.copy(alpha = pulse) else Color.White.copy(alpha = 0.04f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Canvas(Modifier.size(22.dp, 15.dp)) {
                            drawMiniFace(Offset.Zero, size.width, size.height, color)
                        }
                        if (wide) {
                            Text(
                                name, color = TextPrimary.copy(alpha = 0.85f), fontSize = 11.5.sp,
                                modifier = Modifier.weight(1f).padding(start = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- Permission --------------------------------------------------------------------------------

@Composable
fun PermissionCard() {
    val tilt = rememberWiggle(-12f, 10f, -7f, 5f)
    Card(Modifier.cardPad(), glow = GlowAmber, glowAt = Offset(0.22f, 1.25f), glowRadius = 0.85f) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Mascot(
                Modifier.mascotShared().size(70.dp.ms(), 59.dp.ms()),
                tint = Tint.Amber, badge = Badge.Alert, tilt = tilt, onTap = Island::poke,
            )
            Spacer(Modifier.width(mascotGap()))
            Column(Modifier.weight(1f)) {
                Header("korus", "needs permission")
                Spacer(Modifier.height(6.dp))
                Text(
                    "git push origin main",
                    color = TextPrimary, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace, maxLines = 1,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.07f)).padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillButton("Deny", keyHint = "N") { Island.deny() }
                    PillButton("Allow", light = true, keyHint = "Y", dropdown = true) { Island.allow() }
                    PillButton("Always") { Island.allow() }
                }
            }
        }
    }
}

// ---- Finished ----------------------------------------------------------------------------------

@Composable
fun FinishedCard() {
    val tilt = rememberWiggle(-16f, -16f, -16f, 6f, -3f, stepMs = 140)
    Card(Modifier.cardPad(), glow = GlowGreen, glowAt = Offset(0.2f, 1.2f), glowRadius = 0.9f) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Mascot(
                Modifier.mascotShared().size(70.dp.ms(), 59.dp.ms()),
                tint = Tint.Mint, badge = Badge.Done, face = Face.Happy, sparkles = true, tilt = tilt,
                onTap = Island::poke,
            )
            Spacer(Modifier.width(mascotGap()))
            Column(Modifier.weight(1f)) {
                Header("korus", "Claude Code finished", dot = Color(0xFF22C55E))
                Spacer(Modifier.height(4.dp))
                Text(
                    stepLabel(3), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillButton("Open terminal", light = true) { Island.openApp() }
                    PillButton("OK") { Island.collapse() }
                }
            }
        }
    }
}

// ---- Files -------------------------------------------------------------------------------------

@Composable
fun DropCard() {
    val fly = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    val swallow = remember { Animatable(0f) }
    var mouth by remember { mutableFloatStateOf(0f) }
    var face by remember { mutableStateOf(Face.Normal) }
    LaunchedEffect(Unit) {
        delay(450)
        launch { fly.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
        delay(380)
        launch { rise.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 240f)) }
        delay(300)
        mouth = 1f; face = Face.Happy
        delay(420)
        swallow.animateTo(1f, tween(300, easing = FastOutLinearInEasing))
        mouth = 0f
        delay(900)
        Island.dropDone()
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    Card(
        Modifier.cardPad().height(118.dp.hs()),
        glow = GlowGreen, glowAt = Offset(0.5f, 0.6f), glowRadius = 0.45f, dashed = Color(0xFF3B7058),
    ) {
        Column(
            Modifier.align(Alignment.Center).graphicsLayer { alpha = 1f - rise.value * 0.6f },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Drop your files here", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("PDF", "Images", "Code", "Docs").forEach { SmallChip(it) }
            }
        }
        Mascot(
            Modifier
                .align(Alignment.Center)
                .offset { IntOffset(0, ((1f - rise.value) * 80.dp.toPx() + 8.dp.toPx()).roundToInt()) }
                .graphicsLayer { alpha = rise.value.coerceIn(0f, 1f) }
                .size(76.dp.ms(), 64.dp.ms()),
            mouth = mouth, face = face, lookY = -0.8f * (1f - swallow.value),
        )
        val fx = with(density) { 150.dp.toPx() } * (1f - fly.value)
        val fy = with(density) { (-74).dp.toPx() + 56.dp.toPx() * fly.value + 22.dp.toPx() * swallow.value }
        FileIcon(
            Modifier
                .align(Alignment.Center)
                .offset { IntOffset(fx.roundToInt(), fy.roundToInt()) }
                .graphicsLayer {
                    val sc = 1f - swallow.value * 0.75f
                    scaleX = sc; scaleY = sc
                    alpha = (fly.value * 3f).coerceAtMost(1f) * (1f - swallow.value)
                    rotationZ = (1f - fly.value) * 28f
                }
                .size(24.dp, 30.dp),
        )
    }
}

@Composable
fun UploadCard() {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(200)
        p.animateTo(0.54f, tween(1100, easing = FastOutSlowInEasing))
        delay(260)
        p.animateTo(0.92f, tween(900, easing = FastOutSlowInEasing))
        delay(160)
        p.animateTo(1f, tween(320))
        delay(380)
        Island.uploadDone()
    }
    Card(Modifier.cardPad(), glow = GlowGreen, glowAt = Offset(0.5f, 1.3f), glowRadius = 0.6f) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Uploading quote.pdf", color = TextPrimary.copy(alpha = 0.85f), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("${(p.value * 100).roundToInt()} %", color = TextSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().height(24.dp)) {
                val w = maxWidth
                Box(
                    Modifier.align(Alignment.CenterStart).fillMaxWidth().height(6.dp)
                        .clip(CircleShape).background(Color(0xFF26262B))
                )
                Box(
                    Modifier.align(Alignment.CenterStart).width(w * p.value).height(6.dp).clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF1E9E6A), Color(0xFF5EE3A8))))
                )
                val knobX: Dp = (w * p.value - 13.dp).coerceIn(0.dp, w - 26.dp)
                Mascot(
                    Modifier.align(Alignment.CenterStart).offset(x = knobX).mascotShared().size(26.dp, 22.dp),
                    glow = false, face = if (p.value >= 1f) Face.Happy else Face.Normal,
                )
            }
        }
    }
}

@Composable
fun ReadyCard() {
    Card(Modifier.cardPad(), glow = GlowGreen, glowAt = Offset(0.15f, 1.3f), glowRadius = 0.6f) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Mascot(Modifier.mascotShared().size(70.dp.ms(), 59.dp.ms()), face = Face.Happy, tint = Tint.Mint, onTap = Island::poke)
            Spacer(Modifier.width(mascotGap()))
            Column(Modifier.weight(1f)) {
                Text("quote.pdf is ready.", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("What do you want to do with it?", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillButton("Ask a question about it", light = true) { Island.ask() }
                    PillButton("Cancel") { Island.collapse() }
                }
            }
        }
    }
}

// ---- Chat --------------------------------------------------------------------------------------

@Composable
fun ChatCard(s: IslandState) {
    Card(
        Modifier.cardPad().animateContentSize(spring(dampingRatio = 0.8f, stiffness = 300f)),
        glow = GlowIndigo, glowAt = Offset(0.55f, 1.25f), glowRadius = 0.8f,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            if (s.msgApp.isEmpty()) {
                FileChip("quote.pdf", Modifier.padding(start = 52.dp))
                Spacer(Modifier.height(6.dp))
            }
            for (m in s.chat) {
                key(m.id) { ChatLine(m) }
            }
            if (s.typing) TypingDots(Modifier.padding(start = 56.dp, top = 8.dp, bottom = 2.dp))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(
                    Modifier.mascotShared().size(48.dp.ms(), 41.dp.ms()),
                    tint = Tint.Lilac, badge = if (s.typing) Badge.Thinking else Badge.None,
                    face = if (s.typing) Face.Normal else Face.Happy, lookX = if (s.typing) 0.6f else 0f,
                    onTap = Island::poke,
                )
                Spacer(Modifier.width(mascotGap(6.dp, 48.dp)))
                ChatInput(s.draft, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChatLine(m: ChatMsg) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 380f)) }
    val mod = Modifier.fillMaxWidth().padding(top = 6.dp).graphicsLayer {
        alpha = appear.value.coerceIn(0f, 1f)
        translationY = (1f - appear.value) * 12.dp.toPx()
    }
    if (m.fromUser) {
        Row(mod, horizontalArrangement = Arrangement.End) {
            Text(
                m.text, color = TextPrimary, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF28282F))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    } else {
        var shown by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (shown < m.text.length) {
                shown = (shown + 2).coerceAtMost(m.text.length)
                delay(22)
            }
        }
        Text(
            m.text.take(shown), color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = mod.padding(start = 44.dp, end = 12.dp),
        )
    }
}

@Composable
private fun TypingDots(modifier: Modifier) {
    val inf = rememberInfiniteTransition(label = "typing")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "t")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0..2) {
            val ph = ((t * 3f - i) % 3f).let { if (it < 0) it + 3f else it }
            val lift = if (ph < 1f) sin(ph * Math.PI.toFloat()) else 0f
            Box(
                Modifier.offset(y = (-4).dp * lift).size(5.dp).clip(CircleShape)
                    .background(TextSecondary.copy(alpha = 0.5f + 0.5f * lift))
            )
        }
    }
}

@Composable
private fun ChatInput(draft: String, modifier: Modifier) {
    val host = LocalIslandHost.current
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    Row(
        modifier.height(38.dp).clip(CircleShape).background(Color(0xFF1B1B21))
            .border(1.dp, Color.White.copy(alpha = 0.06f), CircleShape)
            .padding(start = 14.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (draft.isEmpty()) Text("Continue…", color = TextDim, fontSize = 13.sp)
            BasicTextField(
                value = draft,
                onValueChange = Island::setDraft,
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { Island.send() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (!host.focusable) {
                // The overlay is not focusable by default (so it never steals the keyboard);
                // the first tap on the field flips that and then focuses it.
                Box(
                    Modifier.matchParentSize().clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) {
                        host.setFocusable(true)
                        scope.launch {
                            delay(120)
                            runCatching { focus.requestFocus() }
                            kb?.show()
                        }
                    }
                )
            }
        }
        val canSend = draft.isNotBlank()
        val sendScale by animateFloatAsState(if (canSend) 1f else 0.85f, spring(0.5f, 500f), label = "send")
        Box(
            Modifier.size(28.dp).graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                .clip(CircleShape).background(if (canSend) Color.White else Color(0xFF3A3A42))
                .clickable { Island.send() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ArrowUpward, null, tint = Color(0xFF111114), modifier = Modifier.size(16.dp))
        }
    }
}

// ---- Rate limit --------------------------------------------------------------------------------

@Composable
fun RateLimitCard() {
    val inf = rememberInfiniteTransition(label = "dizzy")
    val sway by inf.animateFloat(-7f, 7f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    var left by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        while (left > 1) { delay(1000); left-- }
    }
    val words = when (left) { 3 -> "three seconds"; 2 -> "two seconds"; else -> "one second" }
    Card(Modifier.cardPad(), glow = GlowPink, glowAt = Offset(0.05f, 0.5f), glowRadius = 0.9f) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Mascot(
                Modifier.mascotShared().size(66.dp.ms(), 56.dp.ms()),
                face = Face.Dizzy, tint = Tint.Pink, tilt = sway,
            )
            Spacer(Modifier.width(mascotGap(12.dp, 66.dp)))
            Column(Modifier.weight(1f)) {
                Text("Too many hits at once.", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("Give me a sec — back to work in $words.", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

// ---- Generic message ---------------------------------------------------------------------------

@Composable
fun MessageCard(s: IslandState) {
    val prefs = IslandSettings.cat(Category.Messages)
    val skin = IslandSettings.skinFor(Category.Messages)
    val hasPhoto = prefs.photo && s.msgAvatar && s.msgChat.isNotEmpty()
    Card(
        Modifier.cardPad().animateContentSize(spring(dampingRatio = 0.8f, stiffness = 300f)),
        glow = Color(0xFF128C4A), glowAt = Offset(0.15f, 1.3f), glowRadius = 0.75f,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Mascot(
                Modifier.mascotShared().size(70.dp.ms(), 59.dp.ms()),
                skin = skin,
                badge = Badge.Done,
                lookX = 0.8f, lookY = -0.2f, onTap = Island::poke,
            )
            Spacer(Modifier.width(mascotGap(skin = skin)))
            Column(Modifier.weight(1f)) {
                Header(s.msgApp, "· now", dot = Color(0xFF25D366))
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Top) {
                    if (hasPhoto) {
                        // the photo is as tall as the two lines next to it (name + first line of text)
                        ChatAvatar(s.msgChat, 38.dp)
                        Spacer(Modifier.width(9.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.msgTitle, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (s.msgBody.isNotEmpty() && prefs.preview) {
                            TypewriterText(s.msgBody, key = s.nonce, animate = prefs.typewriter)
                        }
                    }
                }
                if (s.msgBody.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PillButton("Reply", light = true) { Island.reply() }
                        PillButton("Mark as read") { Island.markRead() }
                    }
                }
            }
        }
    }
}

/** Round chat photo fetched through the hub (cached). */
@Composable
private fun ChatAvatar(chat: String, size: Dp = 22.dp) {
    var bmp by remember(chat) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(chat) {
        bmp = com.maiconerick.xnotificationsystem.HubClient.avatar(chat)?.asImageBitmap()
    }
    val img = bmp
    if (img != null) {
        androidx.compose.foundation.Image(
            img, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF2A2A31)))
    }
}

// ---- Voice assistant (xAssistant, wake word "Umbra") -------------------------------------------

@Composable
fun AssistantCard(s: IslandState) {
    val skin = IslandSettings.skinFor(Category.Assistant)
    val violet = Color(0xFFB48CFF)
    val mint = Color(0xFF7CF2D0)
    val (label, accent) = when (s.asstPhase) {
        "Listening" -> "listening…" to mint
        "Thinking" -> "thinking…" to violet
        "Speaking" -> "speaking" to mint
        "Error" -> "couldn't do it" to Color(0xFFFF6B9A)
        else -> "done" to violet
    }
    Card(
        Modifier.cardPad().animateContentSize(spring(dampingRatio = 0.8f, stiffness = 300f)),
        glow = Color(0xFF5B3DB8), glowAt = Offset(0.15f, 1.3f), glowRadius = 0.8f,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Mascot(
                Modifier.mascotShared().size(70.dp.ms(), 59.dp.ms()),
                skin = skin,
                face = if (s.asstPhase == "Done") Face.Happy else Face.Normal,
                tint = Tint.Lilac,
                badge = when (s.asstPhase) {
                    "Thinking" -> Badge.Thinking
                    "Error" -> Badge.Alert
                    "Done" -> Badge.Done
                    else -> Badge.None
                },
                mouth = if (s.asstPhase == "Speaking") 0.6f else 0f,
                lookX = 0.8f, lookY = -0.2f, onTap = Island::poke,
            )
            Spacer(Modifier.width(mascotGap(skin = skin)))
            Column(Modifier.weight(1f)) {
                Header("Umbra", "· $label", dot = accent) {
                    if (s.asstPhase == "Listening" || s.asstPhase == "Speaking") VoiceBars(accent)
                }
                if (s.asstUser.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "“${s.asstUser}”", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp,
                    )
                }
                if (s.asstReply.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        s.asstReply, color = if (s.asstPhase == "Error") Color(0xFFFF8FB0) else TextSecondary,
                        fontSize = 12.5.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp,
                    )
                } else if (s.asstUser.isBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text("Say what you need", color = TextSecondary, fontSize = 12.5.sp)
                }
            }
        }
    }
}

/** Little equalizer next to the header while Umbra listens or talks. */
@Composable
private fun VoiceBars(color: Color) {
    val inf = rememberInfiniteTransition(label = "voiceBars")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "t")
    Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..4) {
            val h = 4f + 10f * kotlin.math.abs(sin((t + i * 0.19f) * 2f * Math.PI.toFloat()))
            Box(Modifier.width(3.dp).height(h.dp).clip(CircleShape).background(color))
        }
    }
}

// ---- Live activity (Claude tasks, xBuildServer builds) -----------------------------------------

private fun fmtElapsed(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun TaskCard(s: IslandState) {
    val acts by Island.activities.collectAsState()
    val a = acts[s.activityId] ?: return
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(a.running) {
        while (a.running) { now = System.currentTimeMillis(); delay(1000) }
    }
    val elapsed = (if (a.running) now else a.finishedMs.takeIf { it > 0 } ?: a.updatedMs) - a.startedMs
    val accent = when {
        a.failed -> Color(0xFFFF6B9A)
        a.done -> Color(0xFF5EE3A8)
        else -> Color(0xFF9DA8FF)
    }
    val tilt = if (a.done) rememberWiggle(-14f, 8f, -4f) else 0f
    val skin = IslandSettings.skinFor(Island.categoryOf(a))
    Card(
        Modifier.cardPad(),
        glow = when { a.failed -> GlowPink; a.done -> GlowGreen; else -> Color(0xFF2A47C8) },
        glowAt = Offset(0.2f, 1.25f), glowRadius = 0.85f,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Mascot(
                Modifier.mascotShared().size(70.dp.ms(), 59.dp.ms()),
                skin = skin,
                tint = when { a.failed -> Tint.Pink; a.done -> Tint.Mint; else -> Tint.Work },
                badge = when { a.failed -> Badge.Alert; a.done -> Badge.Done; else -> Badge.Working },
                face = when { a.failed -> Face.Dizzy; a.done -> Face.Happy; else -> Face.Normal },
                sparkles = a.done, tilt = tilt, lookX = if (a.running) 0.5f else 0f,
                onTap = Island::poke,
            )
            Spacer(Modifier.width(mascotGap(skin = skin)))
            Column(Modifier.weight(1f)) {
                Header(a.title, a.source, dot = accent) {
                    Text(
                        if (a.total > 0) "${a.step}/${a.total} · ${fmtElapsed(elapsed)}" else fmtElapsed(elapsed),
                        color = TextDim, fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(5.dp))
                AnimatedContent(
                    targetState = Triple(a.step, a.label, a.status),
                    transitionSpec = {
                        (slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 2 } +
                                fadeIn(tween(220, delayMillis = 60))) togetherWith
                                (slideOutVertically(tween(220)) { -it / 2 } + fadeOut(tween(160))) using
                                SizeTransform(clip = false)
                    },
                    label = "taskSteps",
                ) { (step, label, status) ->
                    Column {
                        val prev = a.steps.getOrNull(step - 2)
                        if (prev != null && status == "running") {
                            Text("✓ $prev", color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(2.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val mark = when (status) { "done" -> "✓ "; "failed" -> "✕ "; else -> "› " }
                            Text(mark, color = if (status == "running") TextPrimary else accent, fontSize = 13.sp)
                            ShimmerText(
                                label.ifEmpty { a.steps.getOrNull(step - 1) ?: if (status == "done") "Done" else "Working…" },
                                13.sp, TextPrimary, active = status == "running",
                            )
                        }
                    }
                }
                if (a.detail.isNotEmpty()) {
                    Text(
                        a.detail, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.height(9.dp))
                StepBar(a, accent)
            }
        }
    }
}

/** One segment per step (completed / current / upcoming); a plain bar when there are no steps. */
@Composable
private fun StepBar(a: Activity, accent: Color) {
    val inf = rememberInfiniteTransition(label = "stepbar")
    val pulse by inf.animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")
    val frac by animateFloatAsState(a.fraction, tween(500), label = "frac")
    val track = Color.White.copy(alpha = 0.08f)
    if (a.total in 1..16) {
        Row(Modifier.fillMaxWidth().height(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 0 until a.total) {
                val filled = a.done || i < a.step - 1
                val current = a.running && i == a.step - 1 || a.failed && i == a.step - 1
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(track)
                ) {
                    when {
                        filled -> Box(Modifier.fillMaxSize().background(accent.copy(alpha = if (a.failed) 0.5f else 1f)))
                        current && a.failed -> Box(Modifier.fillMaxSize().background(accent))
                        current -> {
                            val p = a.progress
                            if (p != null) Box(Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0f, 1f)).background(accent))
                            else Box(Modifier.fillMaxSize().background(accent.copy(alpha = pulse)))
                        }
                    }
                }
            }
        }
    } else {
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(track)) {
            if (a.running && a.progress == null) {
                Box(Modifier.fillMaxSize().background(accent.copy(alpha = pulse * 0.6f)))
            } else {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(accent))
            }
        }
    }
}

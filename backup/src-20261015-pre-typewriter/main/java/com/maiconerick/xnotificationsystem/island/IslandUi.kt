package com.maiconerick.xnotificationsystem.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

val PanelBlack get() = IslandSettings.panelColor.color
val CardBg get() = IslandSettings.panelColor.card
val TextPrimary = Color(0xFFF2F2F5)
val TextSecondary = Color(0xFF8C8C96)
val TextDim = Color(0xFF5E5E68)

/** Sizes derived from the real display cutout so the island hugs the front camera. */
data class IslandGeometry(
    val toolbarH: Dp = 40.dp,
    val pillW: Dp = 150.dp,
    val pillH: Dp = 34.dp,
    val cameraW: Dp = 24.dp,
    val cutTop: Dp = 0.dp,
    val cutH: Dp = 0.dp,
)

/** What the overlay window lets the UI control. */
interface IslandHost {
    val focusable: Boolean
    fun setFocusable(focusable: Boolean)
}

val LocalIslandHost = compositionLocalOf<IslandHost> {
    object : IslandHost {
        override val focusable = true
        override fun setFocusable(focusable: Boolean) = Unit
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
private val LocalShared = compositionLocalOf<SharedTransitionScope?> { null }
private val LocalAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** The mascot flies between layouts (pill → card → chat …) instead of cross-fading. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.mascotShared(): Modifier {
    val shared = LocalShared.current ?: return this
    val anim = LocalAnimScope.current ?: return this
    return with(shared) {
        this@mascotShared.sharedElement(
            rememberSharedContentState("mascot"),
            anim,
            boundsTransform = { _, _ -> islandSpring(0.7f, 240f) },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun IslandRoot(geo: IslandGeometry, onPanelSize: (IntSize) -> Unit) {
    val s by Island.state.collectAsState()
    val kind = s.kind
    val expanded = kind.isExpanded
    val hidden = kind == Kind.Hidden
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val set = IslandSettings
    val floating = set.style == IslandStyle.Floating
    val detached = floating || set.topOffset > 0f
    val margin = set.sideMargin.dp + if (floating) 8.dp else 0.dp

    // pill: hugs the camera hole when floating, otherwise a status-bar-tall notch
    val pillH = ((when {
        floating && geo.cutH > 0.dp -> geo.cutH + 10.dp
        floating -> 30.dp
        else -> geo.pillH
    }) + set.pillHeightExtra.dp).coerceAtLeast(18.dp)
    val pillTop = (if (floating) {
        if (geo.cutH > 0.dp) (geo.cutTop - 5.dp).coerceAtLeast(2.dp) else 6.dp
    } else 0.dp) + set.topOffset.dp
    val expandedTop = (if (floating) 6.dp else 0.dp) + set.topOffset.dp
    val g = geo.copy(pillH = pillH)

    val morph = islandSpring<Dp>(0.74f, 320f)
    val width by animateDpAsState(
        when {
            expanded -> screenW - margin * 2
            hidden -> geo.cameraW
            else -> set.pillWidth.dp
        }, morph, label = "w",
    )
    val top by animateDpAsState(if (expanded) expandedTop else pillTop, morph, label = "top")
    val bottomR by animateDpAsState(if (expanded) set.corner.dp else pillH / 2, morph, label = "r")
    val topR by animateDpAsState(
        when {
            expanded && detached -> set.corner.dp
            expanded && set.sideMargin > 0f -> 16.dp
            !expanded && detached -> pillH / 2
            else -> 0.dp
        }, morph, label = "tr",
    )
    val alpha by animateFloatAsState(
        if (hidden) 0f else 1f, tween(if (hidden) 260 else 120), label = "a",
    )
    val shadowElev by animateDpAsState(if (expanded && set.shadow) 24.dp else 0.dp, tween(300), label = "sh")
    // springs overshoot: clamp everything that must not go negative
    val topSafe = top.coerceAtLeast(0.dp)
    val widthSafe = width.coerceAtLeast(1.dp)
    val tr = topR.coerceAtLeast(0.dp)
    val br = bottomR.coerceAtLeast(0.dp)
    val shape = RoundedCornerShape(topStart = tr, topEnd = tr, bottomStart = br, bottomEnd = br)

    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .graphicsLayer {
                    translationY = drag.value
                    this.alpha = alpha
                }
                .onSizeChanged(onPanelSize)
                .padding(top = topSafe)
                .width(widthSafe)
                .shadow(shadowElev, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(PanelBlack)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        Island.touched()
                    }
                }
                .pointerInput(expanded) {
                    if (!expanded) return@pointerInput
                    val dismiss = 36.dp.toPx()
                    val maxDown = 28.dp.toPx()
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (drag.value < -dismiss) Island.collapse()
                                drag.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 420f))
                            }
                        },
                        onDragCancel = { scope.launch { drag.animateTo(0f, spring()) } },
                    ) { change, dy ->
                        change.consume()
                        // rubber band downwards, free upwards
                        val next = drag.value + if (dy > 0 && drag.value >= 0f) dy * 0.3f else dy
                        scope.launch { drag.snapTo(next.coerceAtMost(maxDown)) }
                    }
                }
                .then(
                    if (!expanded) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { Island.open() } else Modifier
                ),
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(240, delayMillis = 110)) + expandVertically(islandSpring(0.8f, 340f)),
                exit = fadeOut(tween(110)) + shrinkVertically(islandSpring(0.9f, 420f)),
            ) {
                Toolbar(geo, kind)
            }
            SharedTransitionLayout {
                CompositionLocalProvider(LocalShared provides this) {
                    AnimatedContent(
                        targetState = s,
                        contentKey = { it.kind },
                        contentAlignment = Alignment.TopCenter,
                        transitionSpec = {
                            val enter = fadeIn(tween(260, delayMillis = 70)) + scaleIn(
                                animationSpec = islandSpring(0.78f, 360f),
                                initialScale = 0.9f,
                                transformOrigin = TransformOrigin(0.5f, 0f),
                            )
                            val exit = fadeOut(tween(110)) + scaleOut(
                                animationSpec = tween(150),
                                targetScale = 0.96f,
                                transformOrigin = TransformOrigin(0.5f, 0f),
                            )
                            (enter togetherWith exit).using(
                                SizeTransform(clip = false) { _, _ ->
                                    islandSpring(0.76f, 320f, IntSize.VisibilityThreshold)
                                }
                            )
                        },
                        label = "island",
                    ) { st ->
                        CompositionLocalProvider(LocalAnimScope provides this) {
                            IslandContent(st, g)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IslandContent(s: IslandState, geo: IslandGeometry) {
    when (s.kind) {
        Kind.Hidden -> Spacer(Modifier.fillMaxWidth().height(geo.pillH))
        Kind.Collapsed -> PillContent(geo)
        Kind.Home -> HomeCard(s)
        Kind.Session -> SessionCard(s)
        Kind.Permission -> PermissionCard()
        Kind.Finished -> FinishedCard()
        Kind.Drop -> DropCard()
        Kind.Upload -> UploadCard()
        Kind.Ready -> ReadyCard()
        Kind.Chat -> ChatCard(s)
        Kind.RateLimit -> RateLimitCard()
        Kind.Message -> MessageCard(s)
        Kind.Task -> TaskCard(s)
        Kind.Assistant -> AssistantCard(s)
    }
}

@Composable
private fun PillContent(geo: IslandGeometry) {
    Row(
        Modifier.fillMaxWidth().height(geo.pillH).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val acts by Island.activities.collectAsState()
        val live = Island.pillActivity(acts)
        val charge by Island.lockCharge.collectAsState()
        Mascot(
            Modifier.mascotShared().size(28.dp, 24.dp).padding(top = 1.dp), glow = false,
            badge = when {
                live == null -> Badge.None
                live.running -> Badge.Working
                live.failed -> Badge.Alert
                else -> Badge.Done
            },
        )
        Spacer(Modifier.weight(1f))
        if (live != null) LiveBadge(live)
        else if (charge != null) ChargeBadge(charge!!)
        else if (IslandSettings.gridIcon) AppGridIcon(Modifier.size(15.dp))
    }
}

/** Right side of the pill while an activity runs: "3/6" + a progress ring (✓ / ! when it ends). */
@Composable
private fun LiveBadge(a: Activity) {
    val frac by animateFloatAsState(a.fraction, tween(600), label = "ring")
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "live")
    val spin by inf.animateFloat(
        0f, 360f, androidx.compose.animation.core.infiniteRepeatable(tween(1400, easing = androidx.compose.animation.core.LinearEasing)),
        label = "spin",
    )
    val color = when {
        a.failed -> Color(0xFFFF6B9A)
        a.done -> Color(0xFF5EE3A8)
        else -> Color(0xFFB9A4FF)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val txt = when {
            a.done -> "done"
            a.failed -> "failed"
            a.total > 0 -> "${a.step}/${a.total}"
            else -> "${(frac * 100).toInt()}%"
        }
        androidx.compose.material3.Text(
            txt, color = color, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
        Spacer(Modifier.width(5.dp))
        Canvas(Modifier.size(16.dp)) {
            val st = androidx.compose.ui.graphics.drawscope.Stroke(2.2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val inset = 1.2.dp.toPx()
            val tl = Offset(inset, inset)
            val sz = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(color.copy(alpha = 0.22f), 0f, 360f, false, tl, sz, style = st)
            if (a.running) {
                drawArc(color, -90f, (frac * 360f).coerceAtLeast(18f), false, tl, sz, style = st)
                // a little comet so it never looks frozen between updates
                drawArc(Color.White.copy(alpha = 0.7f), spin, 26f, false, tl, sz, style = st)
            } else {
                drawArc(color, 0f, 360f, false, tl, sz, style = st)
            }
        }
    }
}

/** Lock-screen pill while charging: a pulsing bolt and the battery percentage. */
@Composable
private fun ChargeBadge(percent: Int) {
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "charge")
    val glow by inf.animateFloat(
        0.55f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(1100), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "bolt",
    )
    val green = Color(0xFF5EE3A8)
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Text(
            "$percent%", color = green, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
        Spacer(Modifier.width(3.dp))
        Icon(
            Icons.Rounded.BatteryChargingFull, null, tint = green.copy(alpha = glow), modifier = Modifier.size(16.dp),
        )
    }
}

/** The colorful 2×2 "apps" glyph from the right side of the pill. */
@Composable
fun AppGridIcon(modifier: Modifier) {
    Canvas(modifier) {
        val g = size.width * 0.12f
        val c = (size.width - g) / 2f
        val colors = listOf(Color(0xFFF5A524), Color(0xFF3B82F6), Color(0xFFE5534B), Color(0xFF7C5CFF))
        colors.forEachIndexed { i, col ->
            val x = (i % 2) * (c + g)
            val y = (i / 2) * (c + g)
            drawRoundRect(col, Offset(x, y), Size(c, c), CornerRadius(c * 0.32f))
        }
    }
}

@Composable
private fun Toolbar(geo: IslandGeometry, kind: Kind) {
    val tab = when (kind) {
        Kind.Chat -> 1
        Kind.Drop, Kind.Upload, Kind.Ready -> 2
        Kind.Task -> -1
        else -> 0
    }
    Row(
        Modifier.fillMaxWidth().height(geo.toolbarH).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabIcon(Icons.Rounded.Home, tab == 0) { Island.goHome() }
        TabIcon(Icons.Rounded.ChatBubble, tab == 1, iconSize = 15.dp) { Island.openChat() }
        TabIcon(Icons.Rounded.Add, tab == 2) { Island.openDrop() }
        Spacer(Modifier.weight(1f))
        TabIcon(Icons.Rounded.LightMode, false) { Island.openApp() }
        TabIcon(
            if (IslandSettings.soundOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, false,
        ) { IslandSettings.soundOn = !IslandSettings.soundOn }
    }
}

@Composable
private fun TabIcon(icon: ImageVector, selected: Boolean, iconSize: Dp = 18.dp, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color(0xFF222227) else Color.Transparent, tween(220), label = "tab")
    val tint by animateColorAsState(if (selected) Color(0xFFE6E6EC) else Color(0xFF85858F), tween(220), label = "tint")
    Box(
        Modifier
            .size(width = 40.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

package com.maiconerick.xnotificationsystem.island

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.BreakIterator

/**
 * Matrix-style typewriter for message text: letters are decoded one by one (a short green trail of
 * scrambling glyphs settles into the real letters) with a blinking `_` on the next letter.
 */
object Typewriter {
    /** Long texts speed up so the whole reveal never takes longer than this. */
    const val MAX_MS = 3500L
    /** How many just-written letters are still scrambling / glowing green. */
    const val TRAIL = 3
    /** The `_` keeps blinking this long after the last letter, then goes away. */
    const val CURSOR_TAIL_MS = 1400L

    private const val GLYPHS = "ｦｱｳｴｵｶｷｹｺｻｼｽｾｿﾀﾂﾃﾅﾆﾇﾈﾊﾋﾎﾏﾐﾑﾒﾓﾔﾕﾗﾘﾜ0123456789Z:.=*+<>"

    val Green = Color(0xFF39FF6A)

    /** User-visible letters (emoji and accented letters stay whole). */
    fun graphemes(text: String): List<String> {
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val out = ArrayList<String>(text.length)
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            out += text.substring(start, end)
            start = end; end = it.next()
        }
        return out
    }

    fun msPerLetter(count: Int): Long {
        val speed = IslandSettings.cat(Category.Messages).typeSpeedMs.toLong().coerceAtLeast(5)
        return if (count <= 0) speed else minOf(speed, MAX_MS / count).coerceAtLeast(5)
    }

    /** How long the reveal of [text] takes on a message card (0 when it isn't animated). */
    fun durationMs(text: String): Long {
        val p = IslandSettings.cat(Category.Messages)
        if (!p.typewriter || !p.preview || text.isEmpty()) return 0
        val n = graphemes(text).size
        return n * msPerLetter(n)
    }

    fun glyph(seed: Long): Char = GLYPHS[((seed * 1103515245L + 12345L) ushr 8).mod(GLYPHS.length.toLong()).toInt()]
}

@Composable
fun TypewriterText(text: String, key: Any, animate: Boolean) {
    val letters = remember(text) { Typewriter.graphemes(text) }
    val step = remember(letters.size) { Typewriter.msPerLetter(letters.size) }
    val total = letters.size * step
    var elapsed by remember(key, text) { mutableLongStateOf(if (animate) 0L else Long.MAX_VALUE / 2) }
    LaunchedEffect(key, text, animate) {
        if (!animate) return@LaunchedEffect
        val start = System.currentTimeMillis()
        while (elapsed < total + Typewriter.CURSOR_TAIL_MS) {
            delay(33)
            elapsed = System.currentTimeMillis() - start
        }
    }
    val shown = (elapsed / step).coerceAtMost(letters.size.toLong()).toInt()
    val done = shown >= letters.size
    val cursorOn = elapsed < total + Typewriter.CURSOR_TAIL_MS && (elapsed / 260) % 2 == 0L
    val glow = SpanStyle(color = Typewriter.Green, shadow = Shadow(Typewriter.Green.copy(alpha = 0.7f), blurRadius = 8f))
    val frame = elapsed / 50 // scramble refresh rate

    val styled = buildAnnotatedString {
        for (i in letters.indices) {
            val l = letters[i]
            val age = shown - 1 - i // 0 = the newest letter
            when {
                i < shown && (age >= Typewriter.TRAIL || done) -> withStyle(SpanStyle(color = TextSecondary)) { append(l) }
                i < shown -> withStyle(glow.copy(color = Typewriter.Green.copy(alpha = 1f - age * 0.18f))) {
                    // the two newest letters are still decoding
                    append(if (age < 2 && l.isNotBlank()) Typewriter.glyph(frame * 31 + i).toString() else l)
                }
                i == shown -> withStyle(glow.copy(color = if (cursorOn) Typewriter.Green else Color.Transparent)) {
                    append(if (l.isBlank()) l else "_")
                }
                // not written yet: invisible but laid out, so the card keeps its final size
                else -> withStyle(SpanStyle(color = Color.Transparent)) { append(l) }
            }
        }
        if (done && animate) withStyle(glow.copy(color = if (cursorOn) Typewriter.Green else Color.Transparent)) { append("_") }
    }
    Text(styled, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
}

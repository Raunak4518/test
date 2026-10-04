package com.raunak.daytimeline.money

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raunak.daytimeline.ui.Chronora
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

internal val MoneyGreen = Color(0xFF16A34A)
private val Mint = Color(0xFF34D399)
private val Amber = Color(0xFFF59E0B)
private val Coral = Color(0xFFEF4444)

/** A money value that counts up to [value] whenever it changes. */
@Composable
fun AnimatedMoney(value: Double, currency: String, modifier: Modifier = Modifier, size: TextUnit = 34.sp, color: Color = Color.Unspecified, weight: FontWeight = FontWeight.Black) {
    val shown by animateFloatAsState(value.toFloat(), tween(900, easing = FastOutSlowInEasing), label = "money")
    Text(MoneyEngine.format(kotlin.math.round(shown.toDouble()), currency), modifier, fontSize = size, fontWeight = weight, color = color, maxLines = 1)
}

/**
 * The headline: a dark green card with soft glows, a 240° gauge for the month (green → amber → red as it fills)
 * and today's safe-to-spend counting up in the middle.
 */
@Composable
fun BudgetHero(spent: Double, budget: Double, safe: Double, daysLeft: Long, projected: Double, currency: String) {
    val ratio = (spent / budget.coerceAtLeast(1.0)).toFloat()
    val p by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(1200, easing = FastOutSlowInEasing), label = "gauge")
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(.0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse), label = "g")
    val tone = when { ratio >= 1f -> Coral; ratio >= .8f -> Amber; else -> Mint }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFF052E1C), Color(0xFF0B4D30), Color(0xFF0F6B42))))) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Brush.radialGradient(listOf(Mint.copy(alpha = .35f), Color.Transparent), Offset(size.width * (.15f + .1f * glow), size.height * .2f), size.width * .55f), size.width * .55f, Offset(size.width * (.15f + .1f * glow), size.height * .2f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF22D3EE).copy(alpha = .22f), Color.Transparent), Offset(size.width * .95f, size.height * (.9f - .1f * glow)), size.width * .5f), size.width * .5f, Offset(size.width * .95f, size.height * (.9f - .1f * glow)))
        }
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(230.dp, 170.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(210.dp).offset(y = 18.dp)) {
                    val w = 18.dp.toPx(); val sz = Size(size.width - w, size.height - w); val tl = Offset(w / 2, w / 2)
                    drawArc(Color.White.copy(alpha = .12f), 150f, 240f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
                    // Soft glow under the arc, then the arc fading from mint into the status colour.
                    drawArc(tone.copy(alpha = .22f), 150f, 240f * p.coerceAtLeast(.005f), false, tl, sz, style = Stroke(w * 1.9f, cap = StrokeCap.Round))
                    drawArc(Brush.linearGradient(listOf(Mint, tone), Offset(0f, size.height), Offset(size.width, 0f)), 150f, 240f * p.coerceAtLeast(.005f), false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
                    // Tick marks every 10%.
                    for (i in 0..10) {
                        val a = Math.toRadians((150 + 24.0 * i)); val r1 = size.width / 2 - w * 1.6f; val r2 = r1 - 6.dp.toPx()
                        val c = Offset(size.width / 2, size.height / 2)
                        drawLine(Color.White.copy(alpha = .35f), c + Offset((r1 * kotlin.math.cos(a)).toFloat(), (r1 * kotlin.math.sin(a)).toFloat()), c + Offset((r2 * kotlin.math.cos(a)).toFloat(), (r2 * kotlin.math.sin(a)).toFloat()), 2f)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = 10.dp)) {
                    Text("SAFE TODAY", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    AnimatedMoney(safe, currency, size = 40.sp, color = Color.White)
                    Text("${(ratio * 100).toInt()}% of budget used", color = tone, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .08f)).padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                HeroStat("Spent", MoneyEngine.format(spent, currency))
                HeroStat(if (budget - spent >= 0) "Left" else "Over", MoneyEngine.format(kotlin.math.abs(budget - spent), currency), if (budget - spent < 0) Coral else Color.White)
                HeroStat("Days left", "$daysLeft")
            }
            if (spent > 0 && projected > budget * 1.02) {
                Spacer(Modifier.height(10.dp))
                Text("⚠️ At this pace you'll spend ${MoneyEngine.format(projected, currency)} this month", color = Amber, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, color: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, color = Color.White.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall)
    }
}

private val cardGradients = listOf(
    listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)), listOf(Color(0xFF0EA5E9), Color(0xFF14B8A6)),
    listOf(Color(0xFFF97316), Color(0xFFEC4899)), listOf(Color(0xFF334155), Color(0xFF0F172A)), listOf(Color(0xFF16A34A), Color(0xFF65A30D))
)

/** A wallet drawn like a bank card: gradient, chip, name and the balance. */
@Composable
fun WalletCard(w: Wallet, balance: Double, index: Int, currency: String, onClick: () -> Unit) {
    val g = cardGradients[index % cardGradients.size]
    Box(Modifier.size(210.dp, 128.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(g)).clickable(onClick = onClick)) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = .10f), size.height * .9f, Offset(size.width * 1.05f, -size.height * .1f))
            drawCircle(Color.White.copy(alpha = .07f), size.height * .7f, Offset(size.width * .85f, size.height * 1.1f))
        }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp, 22.dp).clip(RoundedCornerShape(5.dp)).background(Brush.linearGradient(listOf(Color(0xFFFDE68A), Color(0xFFD97706)))))
                Spacer(Modifier.weight(1f))
                Text(w.emoji, fontSize = 20.sp)
            }
            Column {
                Text(MoneyEngine.format(balance, currency), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(w.name.uppercase() + if (balance < 0 && w.opening == 0.0) " · TAP TO SET" else "", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A quick-spend button in its category's colour; a "-₹15" floats up and fades each time it's tapped. */
@Composable
fun QuickSpendButton(q: QuickSpend, cat: MoneyCategory?, currency: String, onTap: () -> Unit) {
    val c = Color(cat?.color ?: 0xFF16A34A)
    var pops by remember { mutableStateOf(listOf<Long>()) }
    var press by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (press) .9f else 1f, spring(dampingRatio = .4f, stiffness = 600f), label = "press", finishedListener = { press = false })
    Box(contentAlignment = Alignment.TopCenter) {
        Row(Modifier.scale(s).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(c.copy(alpha = .22f), c.copy(alpha = .10f))))
            .clickable { press = true; pops = pops + System.nanoTime(); onTap() }.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(c), contentAlignment = Alignment.Center) { Text(cat?.emoji ?: "💸", fontSize = 17.sp) }
            Column(Modifier.padding(start = 8.dp)) {
                Text(q.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(MoneyEngine.format(q.amount, currency), style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
            }
        }
        pops.forEach { key ->
            androidx.compose.runtime.key(key) {
                val a = remember { Animatable(0f) }
                LaunchedEffect(Unit) { a.animateTo(1f, tween(900, easing = FastOutSlowInEasing)); pops = pops - key }
                Text("-" + MoneyEngine.format(q.amount, currency), Modifier.graphicsLayer { translationY = -a.value * 90f; alpha = 1f - a.value; scaleX = 1f + a.value * .3f; scaleY = scaleX },
                    color = c, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        }
    }
}

/** A thick donut with rounded segments that sweeps in, total counting up in the middle. */
@Composable
fun SpendDonut(parts: List<Pair<Color, Double>>, total: Double, currency: String, diameter: Dp = 150.dp) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(parts) { sweep.snapTo(0f); sweep.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 20.dp.toPx(); val sz = Size(this.size.width - w, this.size.height - w); val tl = Offset(w / 2, w / 2)
            drawArc(Color.Gray.copy(alpha = .12f), 0f, 360f, false, tl, sz, style = Stroke(w))
            var start = -90f
            parts.forEach { (c, v) ->
                val a = (v / total * 360f).toFloat() * sweep.value
                if (a > 4f) drawArc(Brush.sweepGradient(listOf(c, lerp(c, Color.White, .25f), c)), start + 2f, a - 4f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
                start += a
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedMoney(total, currency, size = 20.sp)
            Text("this month", style = MaterialTheme.typography.labelSmall, color = Chronora.muted)
        }
    }
}

/** A budget line: coloured icon, name, spent of budget and a gradient bar that grows in. */
@Composable
fun BudgetRow(c: MoneyCategory, spent: Double, currency: String) {
    val ratio = if (c.budget > 0) (spent / c.budget).toFloat() else 0f
    val p by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(900), label = "bar")
    val col = Color(c.color)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(col.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Text(c.emoji, fontSize = 18.sp) }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row {
                Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(MoneyEngine.format(spent, currency), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (ratio > 1f) Coral else MaterialTheme.colorScheme.onSurface)
                if (c.budget > 0) Text(" / " + MoneyEngine.format(c.budget, currency), style = MaterialTheme.typography.labelMedium, color = Chronora.muted)
            }
            if (c.budget > 0) Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(col.copy(alpha = .12f))) {
                Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(if (ratio > 1f) listOf(Amber, Coral) else listOf(col.copy(alpha = .7f), col))))
            }
        }
    }
}

/** Bars that grow in with a gradient, a dashed daily-budget line, and a bubble over the tapped day. */
@Composable
fun SpendBars(values: List<Double>, labels: List<String>, limit: Double, currency: String, picked: Int, onPick: (Int) -> Unit) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(values) { grow.snapTo(0f); grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(limit * 1.25).coerceAtLeast(1.0)
    val base = MaterialTheme.colorScheme.primary; val muted = Chronora.muted
    Column {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            Canvas(Modifier.fillMaxSize().pointerInput(values) { detectTapGestures { o -> onPick((o.x / (size.width / values.size)).toInt().coerceIn(0, values.lastIndex)) } }) {
                val top = 30.dp.toPx(); val h = size.height - top
                val slot = size.width / values.size; val w = slot * .62f
                val y = top + h - (h * limit / max).toFloat()
                drawLine(muted.copy(alpha = .6f), Offset(0f, y), Offset(size.width, y), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                values.forEachIndexed { i, v ->
                    val bh = (h * (v / max).toFloat() * grow.value).coerceAtLeast(if (v > 0) 4f else 2f)
                    val over = v > limit
                    val colors = if (over) listOf(Coral, Amber) else listOf(base, lerp(base, Mint, .6f))
                    drawRoundRect(Brush.verticalGradient(colors.map { if (i == picked) it else it.copy(alpha = .45f) }, top + h - bh, top + h), Offset(i * slot + (slot - w) / 2, top + h - bh), Size(w, bh), CornerRadius(w / 2))
                }
            }
            if (picked in values.indices) {
                val frac = (picked + .5f) / values.size
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val x = (maxWidth * frac - 44.dp).coerceIn(0.dp, maxWidth - 88.dp)
                    Box(Modifier.offset(x = x).width(88.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.inverseSurface).padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                        Text(MoneyEngine.format(values[picked], currency), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) { labels.forEachIndexed { i, l -> Text(l, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = if (i == picked) base else muted, fontWeight = if (i == picked) FontWeight.Bold else FontWeight.Normal) } }
    }
}

/** A round jar that fills with a gently moving wave as a goal fills. */
@Composable
fun SavingsJar(progress: Float, color: Color, emoji: String, diameter: Dp = 76.dp) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(1200), label = "jar")
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "ph")
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)) }
            drawCircle(color.copy(alpha = .12f))
            clipPath(circle) {
                val level = size.height * (1 - p)
                val wave = Path().apply {
                    moveTo(0f, size.height)
                    var x = 0f
                    while (x <= size.width) { lineTo(x, level + sin(x / size.width * 2 * PI.toFloat() * 1.5f + phase) * 5.dp.toPx()); x += 4f }
                    lineTo(size.width, size.height); close()
                }
                drawPath(wave, Brush.verticalGradient(listOf(lerp(color, Color.White, .2f), color)))
            }
            drawCircle(color.copy(alpha = .5f), style = Stroke(2.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 22.sp)
            Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
        }
    }
}

/** A big square category tile for the add sheet; the chosen one fills with its colour and pops. */
@Composable
fun CategoryTile(c: MoneyCategory, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val col = Color(c.color)
    val s by animateFloatAsState(if (selected) 1.06f else 1f, spring(dampingRatio = .45f, stiffness = 500f), label = "tile")
    Column(modifier.scale(s).clip(RoundedCornerShape(18.dp)).background(if (selected) Brush.linearGradient(listOf(col, lerp(col, Color.Black, .2f))) else Brush.linearGradient(listOf(col.copy(alpha = .12f), col.copy(alpha = .06f))))
        .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(c.emoji, fontSize = 24.sp)
        Text(c.name, style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** A friend's initial on a colour picked from their name, so each friend always looks the same. */
@Composable
fun Avatar(name: String, size: Dp = 44.dp) {
    val colors = listOf(0xFF6366F1, 0xFFEC4899, 0xFFF59E0B, 0xFF10B981, 0xFF0EA5E9, 0xFF8B5CF6, 0xFFEF4444, 0xFF14B8A6)
    val c = Color(colors[(name.lowercase().hashCode() and 0x7FFFFFFF) % colors.size])
    Box(Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(lerp(c, Color.White, .15f), c))), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * .42f).sp)
    }
}

/** A colourful stat tile with an emoji badge. */
@Composable
fun MoneyTile(emoji: String, label: String, value: String, tint: Color, modifier: Modifier = Modifier, note: String? = null) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(tint.copy(alpha = .18f), tint.copy(alpha = .06f)))).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(tint.copy(alpha = .22f)), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 17.sp) }
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Chronora.muted)
        if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

/** The amount in the add sheet: bounces on every key press. */
@Composable
fun BouncyAmount(text: String, color: Color) {
    val s = remember { Animatable(1f) }
    LaunchedEffect(text) { s.snapTo(1.08f); s.animateTo(1f, spring(dampingRatio = .4f, stiffness = 700f)) }
    Text(text, Modifier.fillMaxWidth().scale(s.value), textAlign = TextAlign.Center, fontSize = 54.sp, fontWeight = FontWeight.Black, color = color, maxLines = 1)
}

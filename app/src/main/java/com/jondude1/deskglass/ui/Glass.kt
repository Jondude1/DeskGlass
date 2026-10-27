package com.jondude1.deskglass.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object Glass {
    val Ink = Color(0xFFF4F6FF)
    val InkDim = Color(0xB8F4F6FF)
    val InkFaint = Color(0x80F4F6FF)
    val Base = Color(0xFF070A14)
    val Accent = Color(0xFF9DBEFF)
}

/** Slow, drifting colour behind everything. Standing still when [animate] is false. */
@Composable
fun AuroraBackground(animate: Boolean, modifier: Modifier = Modifier) {
    val phase = if (animate) {
        val transition = rememberInfiniteTransition(label = "aurora")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(90_000, easing = LinearEasing), RepeatMode.Restart),
            label = "phase",
        ).value
    } else {
        0.15f
    }
    Canvas(modifier.fillMaxSize().background(Glass.Base)) {
        val w = size.width
        val h = size.height
        val a = phase * 2f * PI.toFloat()

        fun blob(color: Color, cx: Float, cy: Float, r: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.50f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }

        // faint twinkling stars
        for (i in 0 until 26) {
            val sx = ((i * 7919) % 1000) / 1000f * w
            val sy = ((i * 104729) % 1000) / 1000f * h
            val tw = 0.5f + 0.5f * sin(a * 6f + i * 1.7f)
            drawCircle(Color.White.copy(alpha = 0.10f + 0.28f * tw), radius = 1.2f + (i % 3) * 0.7f, center = Offset(sx, sy))
        }

        blob(Color(0xFF4F5BFF), w * (0.20f + 0.10f * sin(a)), h * (0.30f + 0.12f * cos(a)), w * 0.50f)
        blob(Color(0xFF16C6B5), w * (0.82f + 0.08f * cos(a * 2f)), h * (0.25f + 0.10f * sin(a)), w * 0.42f)
        blob(Color(0xFFC04BDB), w * (0.65f + 0.10f * sin(a + 2f)), h * (0.88f + 0.06f * cos(a)), w * 0.52f)
        blob(Color(0xFF2A7BFF), w * (0.10f + 0.06f * cos(a + 1f)), h * (0.90f + 0.05f * sin(a * 2f)), w * 0.38f)
    }
}

/** A frosted pane: translucent fill, bright top-left edge, soft bottom-right edge. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 32.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.17f), Color.White.copy(alpha = 0.06f))
                )
            )
            .drawBehind {
                // soft glossy highlight along the top edge
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                        endY = size.height * 0.35f,
                    ),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.35f),
                )
            }
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.06f),
                        Color.White.copy(alpha = 0.22f),
                    )
                ),
                shape,
            ),
        content = content,
    )
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "press")
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(if (filled) Glass.Accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (filled) Color(0xFF0B1020) else Glass.Ink,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

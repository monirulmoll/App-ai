package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiCyan
import com.example.ui.theme.GeminiNeonPink
import com.example.ui.theme.GeminiPurple
import com.example.ui.theme.GleamGradientCyan
import com.example.ui.theme.GleamGradientPink
import com.example.ui.theme.GleamGradientViolet

/**
 * Clean, single, ultra-premium AI Thinking indicator.
 * Displays only the polished thinking animation without any stop button
 * (the stop action is solely on the input send button).
 */
@Composable
fun AiGeneratingIndicator(
    modifier: Modifier = Modifier,
    onStopGeneration: (() -> Unit)? = null,
    statusText: String = stringResource(id = R.string.ai_generating)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking_stream")

    // Smooth subtle avatar breathing scale
    val avatarPulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatar_pulse"
    )

    // Wave dot heights/scales with staggered phases
    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, delayMillis = 160, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, delayMillis = 320, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    // Shimmer sweep across the thinking card border
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val borderBrush = Brush.horizontalGradient(
        colors = listOf(
            GeminiCyan.copy(alpha = 0.5f),
            GeminiPurple.copy(alpha = 0.7f),
            GeminiNeonPink.copy(alpha = 0.5f)
        )
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("ai_generating_indicator"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // AI Sparkle Avatar with smooth glowing pulse
        Box(
            modifier = Modifier
                .size(34.dp)
                .scale(avatarPulse)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(GleamGradientViolet, GleamGradientPink, GleamGradientCyan)
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Thinking Bubble: Clean, beautiful, glowing shape with typing dots
        Card(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
            ),
            modifier = Modifier.border(
                width = 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 3 Harmonic Liquid Pulsing Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ThinkingDot(color = GeminiCyan, scale = dot1Scale)
                    ThinkingDot(color = GeminiBlue, scale = dot2Scale)
                    ThinkingDot(color = GeminiPurple, scale = dot3Scale)
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Elegant Status Text
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        letterSpacing = 0.3.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun ThinkingDot(color: Color, scale: Float) {
    Box(
        modifier = Modifier
            .size(7.dp)
            .scale(scale.coerceIn(0.4f, 1.3f))
            .clip(CircleShape)
            .background(color)
            .border(0.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
    )
}

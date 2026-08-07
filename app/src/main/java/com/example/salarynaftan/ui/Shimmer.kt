package com.example.salarynaftan.ui
import com.example.salarynaftan.*

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shimmer-эффект: плавная анимация блика для плейсхолдеров загрузки.
 * Показывается при первом открытии экранов, пока данные грузятся.
 */
@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    cornerRadius: Dp = 12.dp
) {
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -300f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "translateX"
    )

    Box(
        modifier = modifier
            .height(height)
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(translateX, 0f),
                    end = Offset(translateX + 200f, 0f)
                )
            )
    )
}

/** Вертикальный список из shimmer-блоков (для пустой загрузки). */
@Composable
fun ShimmerList(rows: Int = 4, spacing: Dp = 12.dp) {
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        repeat(rows) { index ->
            ShimmerBlock(height = 18.dp)
            ShimmerBlock(height = 12.dp, modifier = Modifier.fillMaxWidth(0.6f))
        }
    }
}

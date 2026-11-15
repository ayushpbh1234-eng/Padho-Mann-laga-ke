package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BotanicalGardenCanvas(
    progress: Float,
    isRunning: Boolean,
    isWilted: Boolean,
    isBreakMode: Boolean,
    plantEmoji: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "garden_ambient_pulse")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )
    val swayAngle by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = if (isRunning || progress > 0f) progress else 0f,
        animationSpec = spring(),
        label = "ring_progress"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val errorColor = MaterialTheme.colorScheme.error
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val potColor = if (isWilted) Color(0xFF8C7A72) else Color(0xFFC06C4A)
    val potRimColor = if (isWilted) Color(0xFFA08D84) else Color(0xFFD6825E)

    Box(
        modifier = modifier.size(204.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.44f

            // Soft glowing botanical aura behind the planter
            val auraScale = if (isRunning) breatheScale else 1f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        (if (isBreakMode) secondaryColor else containerColor).copy(alpha = 0.55f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.12f * auraScale
                ),
                radius = radius * 1.12f * auraScale,
                center = center
            )

            // Outer circular progress track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )

            val activeColor = when {
                isWilted -> errorColor
                isBreakMode -> secondaryColor
                else -> primaryColor
            }

            if (animatedProgress > 0f) {
                drawArc(
                    color = activeColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Custom botanical planter & animated stem/leaves inside the circle
            val potWidth = size.width * 0.34f
            val potHeight = size.height * 0.16f
            val potTop = center.y + radius * 0.34f
            val potLeft = center.x - potWidth / 2f

            // Terracotta pot body
            val potPath = Path().apply {
                moveTo(potLeft + potWidth * 0.08f, potTop)
                lineTo(potLeft + potWidth * 0.92f, potTop)
                lineTo(potLeft + potWidth * 0.78f, potTop + potHeight)
                lineTo(potLeft + potWidth * 0.22f, potTop + potHeight)
                close()
            }
            drawPath(potPath, color = potColor)

            // Pot rim
            drawRoundRect(
                color = potRimColor,
                topLeft = Offset(potLeft, potTop - 5.dp.toPx()),
                size = Size(potWidth, 10.dp.toPx()),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
            )

            // Animated organic stem + leaves rising from the pot
            val growth = if (isWilted) 0.35f else (0.25f + animatedProgress * 0.75f)
            val stemHeight = radius * 0.56f * growth
            val stemColor = if (isWilted) errorColor.copy(alpha = 0.75f) else tertiaryColor
            val leafAngle = if (isWilted) -24f else if (isRunning) swayAngle else 0f

            rotate(degrees = leafAngle, pivot = Offset(center.x, potTop)) {
                drawLine(
                    color = stemColor,
                    start = Offset(center.x, potTop - 3.dp.toPx()),
                    end = Offset(center.x, potTop - stemHeight),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Left & right leaves
                val leafY = potTop - stemHeight * 0.55f
                drawOval(
                    color = if (isWilted) errorColor.copy(alpha = 0.6f) else primaryColor.copy(alpha = 0.85f),
                    topLeft = Offset(center.x - 22.dp.toPx(), leafY - 7.dp.toPx()),
                    size = Size(20.dp.toPx(), 11.dp.toPx())
                )
                drawOval(
                    color = if (isWilted) errorColor.copy(alpha = 0.55f) else tertiaryColor.copy(alpha = 0.9f),
                    topLeft = Offset(center.x + 2.dp.toPx(), leafY - 12.dp.toPx()),
                    size = Size(20.dp.toPx(), 11.dp.toPx())
                )

                // Blooming sun/sparkle dots at high progress
                if (!isWilted && animatedProgress > 0.5f) {
                    val crownY = potTop - stemHeight - 4.dp.toPx()
                    for (i in 0 until 5) {
                        val angleRad = Math.toRadians((i * 72 - 90).toDouble())
                        val px = center.x + (10.dp.toPx() * cos(angleRad)).toFloat()
                        val py = crownY + (10.dp.toPx() * sin(angleRad)).toFloat()
                        drawCircle(
                            color = Color(0xFFF6C453).copy(alpha = 0.85f),
                            radius = 4.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                    drawCircle(
                        color = secondaryColor,
                        radius = 4.5.dp.toPx(),
                        center = Offset(center.x, crownY)
                    )
                }
            }
        }

        // Floating Stage Emoji Badge
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 3.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
        ) {
            Text(
                text = plantEmoji,
                fontSize = 36.sp,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .testTag("plant_stage_emoji")
            )
        }
    }
}

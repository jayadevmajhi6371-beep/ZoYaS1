package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AssistantState
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sin

@Composable
fun MainScreen(
    stateFlow: StateFlow<AssistantState>,
    onToggleAssistant: () -> Unit
) {
    val state by stateFlow.collectAsState()
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    
    val orbScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val orbAlpha by animateFloatAsState(
        targetValue = if (state != AssistantState.IDLE) 1f else 0.6f,
        animationSpec = tween(500),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Glowing Background
        Box(
            modifier = Modifier
                .size(400.dp)
                .blur(100.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "NJ",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // The Orb
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .scale(orbScale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = getOrbColors(state)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (state == AssistantState.LISTENING || state == AssistantState.SPEAKING) {
                    WaveformAnimation(state)
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            Text(
                text = getStatusText(state),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onToggleAssistant,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state == AssistantState.IDLE) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error
                ),
                shape = CircleShape,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(if (state == AssistantState.IDLE) "Wake Up NJ" else "Let Her Rest")
            }
        }
    }
}

@Composable
fun WaveformAnimation(state: AssistantState) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val amplitude = if (state == AssistantState.SPEAKING) 40f else 20f

        val points = mutableListOf<androidx.compose.ui.geometry.Offset>()
        for (x in 0..width.toInt() step 5) {
            val relativeX = x / width
            val y = centerY + amplitude * sin(relativeX * 4 * Math.PI + phase).toFloat()
            points.add(androidx.compose.ui.geometry.Offset(x.toFloat(), y))
        }

        for (i in 0 until points.size - 1) {
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = points[i],
                end = points[i + 1],
                strokeWidth = 4f
            )
        }
    }
}

fun getOrbColors(state: AssistantState): List<Color> {
    return when (state) {
        AssistantState.IDLE -> listOf(Color(0xFF1A1A1A), Color(0xFF000000))
        AssistantState.LISTENING -> listOf(Color(0xFF00E5FF), Color(0xFF00838F))
        AssistantState.THINKING -> listOf(Color(0xFFBD00FF), Color(0xFF7B00FF))
        AssistantState.SPEAKING -> listOf(Color(0xFF00FF85), Color(0xFF00C853))
    }
}

fun getStatusText(state: AssistantState): String {
    return when (state) {
        AssistantState.IDLE -> "NJ is resting... for now."
        AssistantState.LISTENING -> "Listening to your every word, babe."
        AssistantState.THINKING -> "Thinking of something witty to say..."
        AssistantState.SPEAKING -> "NJ is talking. Better listen up."
    }
}

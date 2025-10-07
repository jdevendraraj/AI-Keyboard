package dev.patrickgold.florisboard.ime.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggText

@Composable
fun VoiceOverlayPanel(
    statusText: String,
    isRecording: Boolean = false,
    isProcessing: Boolean = false,
    onMicClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    
    // Levitating animation - gentle up and down movement
    val infiniteTransition = rememberInfiniteTransition(label = "mic_animations")
    val levitateOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_levitate"
    )
    
    // Subtle scale pulse for breathing effect
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )
    
    // Glow opacity pulse
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    SnyggBox(
        elementName = FlorisImeUi.Smartbar.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        SnyggColumn(
            elementName = FlorisImeUi.Smartbar.elementName,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SnyggButton(
                    elementName = FlorisImeUi.SmartbarSharedActionsToggle.elementName,
                    onClick = {
                        // Stop recording if active
                        onBackClick()
                        // Close voice panel and return to keyboard
                        CoroutineScope(Dispatchers.Main).launch {
                            keyboardManager.activeState.batchEdit { it.isVoiceOverlayVisible = false }
                        }
                    },
                ) {
                    SnyggIcon(imageVector = Icons.Filled.ArrowBack)
                }
                SnyggText(
                    text = "Voice input",
                )
                Spacer(modifier = Modifier.size(24.dp))
            }

            // Main mic row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SnyggButton(
                    elementName = FlorisImeUi.SmartbarSharedActionsToggle.elementName,
                    onClick = { FlorisImeService.launchSettings() },
                ) {
                    SnyggIcon(imageVector = Icons.Filled.Settings)
                }
                
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Neon glow layers (only when recording)
                    if (isRecording) {
                        // Outer glow - larger, more blurred
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .offset(y = levitateOffset.dp)
                                .scale(scalePulse)
                                .background(
                                    color = Color(0xFF00D4FF).copy(alpha = glowAlpha * 0.4f),
                                    shape = CircleShape
                                )
                                .blur(20.dp)
                        )
                        // Middle glow
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .offset(y = levitateOffset.dp)
                                .scale(scalePulse)
                                .background(
                                    color = Color(0xFF00D4FF).copy(alpha = glowAlpha * 0.6f),
                                    shape = CircleShape
                                )
                                .blur(12.dp)
                        )
                        // Inner glow
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .offset(y = levitateOffset.dp)
                                .scale(scalePulse)
                                .background(
                                    color = Color(0xFF00D4FF).copy(alpha = glowAlpha),
                                    shape = CircleShape
                                )
                                .blur(8.dp)
                        )
                    }
                    
                    // Processing spinner (overlay around the button)
                    if (isProcessing) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isProcessing,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(100.dp)
                                    .semantics {
                                        contentDescription = "Processing voice input"
                                    },
                                strokeWidth = 4.dp,
                                color = Color(0xFF00D4FF),
                            )
                        }
                    }
                    
                    // Main button
                    SnyggButton(
                        elementName = FlorisImeUi.SmartbarSharedActionsToggle.elementName,
                        onClick = onMicClick,
                        enabled = !isProcessing, // Disable button while processing
                        modifier = Modifier
                            .size(86.dp)
                            .then(
                                if (isRecording) {
                                    Modifier
                                        .offset(y = levitateOffset.dp)
                                        .scale(scalePulse)
                                } else {
                                    Modifier
                                }
                            ),
                    ) {
                        Box(
                            modifier = Modifier.size(86.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            SnyggIcon(
                                imageVector = Icons.Filled.Mic,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }
                
                SnyggButton(
                    elementName = FlorisImeUi.SmartbarSharedActionsToggle.elementName,
                    onClick = onDeleteClick,
                ) {
                    SnyggIcon(imageVector = Icons.Filled.Backspace)
                }
            }

            // Footer status text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                SnyggText(text = statusText)
            }
        }
    }
}



package dev.patrickgold.florisboard.ime.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
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
    onMicClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    
    // Pulsing animation for the microphone when recording
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
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
                        onBackClick()
                        CoroutineScope(Dispatchers.Main).launch {
                            keyboardManager.activeState.batchEdit { it.isVoiceOverlayVisible = false }
                        }
                    },
                ) {
                    SnyggIcon(imageVector = Icons.Filled.ArrowBack)
                }
                SnyggText(
                    text = "Try saying something",
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
                
                SnyggButton(
                    elementName = FlorisImeUi.SmartbarSharedActionsToggle.elementName,
                    onClick = onMicClick,
                    modifier = Modifier
                        .size(86.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .then(if (isRecording) Modifier.scale(scale) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        SnyggIcon(
                            imageVector = Icons.Filled.Mic,
                            modifier = Modifier.size(36.dp),
                        )
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



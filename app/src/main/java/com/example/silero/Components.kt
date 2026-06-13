package com.example.silero

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.silero.audio.RepeatEngine
import com.example.silero.audio.VoicePreset

@Composable
fun StatusCard(state: RepeatEngine.EngineState, prob: Float) {
    val (label, color) = when (state) {
        RepeatEngine.EngineState.IDLE -> "未启动" to Color(0xFF9E9E9E)
        RepeatEngine.EngineState.LISTENING -> "聆听中…" to Color(0xFF42A5F5)
        RepeatEngine.EngineState.SPEAKING -> "正在说话…" to Color(0xFF66BB6A)
        RepeatEngine.EngineState.PROCESSING -> "变声处理中…" to Color(0xFFFFA726)
        RepeatEngine.EngineState.PLAYING -> "复读播放中…" to Color(0xFFAB47BC)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = color, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Text(
                "语音活动概率",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { prob.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
        }
    }
}

@Composable
fun PresetRow(selected: VoicePreset, onSelect: (VoicePreset) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("音色预设", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VoicePreset.entries.forEach { p ->
                FilterChip(
                    selected = selected == p,
                    onClick = { onSelect(p) },
                    label = { Text(p.label) }
                )
            }
        }
    }
}

/** 固定的「可调参数」标题行 + 恢复默认按钮（不随滑竿滚动）。 */
@Composable
fun ParamSectionHeader(vm: MainViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("可调参数", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { vm.resetDefaults() }) {
            Text("恢复默认")
        }
    }
}

/** 可滚动的滑竿列表。以后追加新滑竿只需在此处加一项 LabeledSlider。 */
@Composable
fun ParamSlidersList(vm: MainViewModel) {
    Column(Modifier.fillMaxWidth()) {
        LabeledSlider(
            label = "音高",
            value = vm.pitch,
            valueText = String.format("%+.1f 半音", vm.pitch),
            range = -12f..12f,
            onChange = { vm.updatePitch(it) }
        )
        LabeledSlider(
            label = "语速",
            value = vm.tempo,
            valueText = String.format("%.2fx", vm.tempo),
            range = 0.5f..2.0f,
            onChange = { vm.updateTempo(it) }
        )
        LabeledSlider(
            label = "断句静音时长",
            value = vm.silenceTailMs,
            valueText = "${vm.silenceTailMs.toInt()} ms",
            range = 300f..1500f,
            onChange = { vm.updateSilenceTail(it) }
        )
        LabeledSlider(
            label = "麦克风灵敏度",
            value = vm.micSensitivity,
            valueText = "${vm.micSensitivity.toInt()}x",
            range = 1f..30f,
            onChange = { vm.updateMicSensitivity(it) }
        )
        LabeledSlider(
            label = "延迟播放",
            value = vm.playbackDelayMs,
            valueText = "${vm.playbackDelayMs.toInt()} ms",
            range = 0f..3000f,
            onChange = { vm.updatePlaybackDelay(it) }
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                valueText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range
        )
    }
}

@Composable
fun StartStopButton(running: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (running) Color(0xFFE53935) else MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            if (running) "停止" else "开始复读",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

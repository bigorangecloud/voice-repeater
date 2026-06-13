package com.example.silero

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.silero.audio.RepeatEngine

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "需要麦克风权限",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "本应用通过麦克风录音，检测你说完一句话后变声复读。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("授予麦克风权限") }
    }
}

@Composable
fun MainScreen(vm: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部固定区
        Text(
            "Silero 变声复读机",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(8.dp))
        StatusCard(vm.engineState, vm.speechProb)
        Spacer(Modifier.height(20.dp))

        PresetRow(vm.preset) { vm.selectPreset(it) }
        Spacer(Modifier.height(20.dp))

        // 「可调参数」标题 + 恢复默认按钮：固定不动
        ParamSectionHeader(vm)
        Spacer(Modifier.height(8.dp))

        // 中间可滚动区：只有滑竿列表滚动，占据剩余空间
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            ParamSlidersList(vm)
        }

        // 底部固定按钮
        Spacer(Modifier.height(16.dp))
        StartStopButton(vm.engineState != RepeatEngine.EngineState.IDLE) { vm.toggle() }
    }
}

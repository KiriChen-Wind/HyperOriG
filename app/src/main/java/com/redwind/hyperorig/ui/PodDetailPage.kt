package com.redwind.hyperorig.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.redwind.hyperorig.R
import com.redwind.hyperorig.pods.EqMode
import com.redwind.hyperorig.pods.NoiseControlMode
import com.redwind.hyperorig.ui.components.AncSwitch
import com.redwind.hyperorig.ui.components.PodStatus
import com.redwind.hyperorig.utils.miuiStrongToast.data.BatteryParams
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.basic.Scaffold

@Composable
fun PodDetailPage(
    batteryParams: BatteryParams,
    ancMode: NoiseControlMode,
    onAncModeChange: (NoiseControlMode) -> Unit,
    gameMode: Boolean = false,
    onGameModeChange: (Boolean) -> Unit = {},
    lowLatencyMode: Boolean = false,
    onLowLatencyModeChange: (Boolean) -> Unit = {},
    dualConnMode: Boolean = false,
    onDualConnModeChange: (Boolean) -> Unit = {},
    eqMode: EqMode = EqMode.BALANCED,
    onEqModeChange: (EqMode) -> Unit = {},
    inEarDetection: Boolean = false,
    onInEarDetectionChange: (Boolean) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Image(
                painter = painterResource(R.drawable.img_box),
                contentDescription = "Earphones",
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(vertical = 32.dp),
                contentScale = ContentScale.FillWidth
            )
        }

        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                PodStatus(batteryParams, modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp))
            }
        }

        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                AncSwitch(ancMode, onAncModeChange)
            }
        }

        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                val eqOptions = EqMode.values().map { it.label }
                val selectedIndex = remember(eqMode) {
                    EqMode.values().indexOf(eqMode)
                }
                SuperDropdown(
                    title = stringResource(R.string.eq_mode),
                    summary = stringResource(R.string.eq_mode_summary),
                    items = eqOptions,
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = { index ->
                        val selectedMode = EqMode.values()[index]
                        onEqModeChange(selectedMode)
                    }
                )
            }
        }

        item {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 12.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Column {
                    SuperSwitch(
                        title = stringResource(R.string.game_mode),
                        summary = stringResource(R.string.game_mode_summary),
                        checked = gameMode,
                        onCheckedChange = onGameModeChange
                    )
                    SuperSwitch(
                        title = stringResource(R.string.low_latency_mode),
                        summary = stringResource(R.string.low_latency_mode_summary),
                        checked = lowLatencyMode,
                        onCheckedChange = onLowLatencyModeChange,
                        enabled = !gameMode
                    )
                    SuperSwitch(
                        title = stringResource(R.string.dual_conn_mode),
                        summary = stringResource(R.string.dual_conn_mode_summary),
                        checked = dualConnMode,
                        onCheckedChange = onDualConnModeChange
                    )
                    SuperSwitch(
                        title = stringResource(R.string.in_ear_detection),
                        summary = stringResource(R.string.in_ear_detection_summary),
                        checked = inEarDetection,
                        onCheckedChange = onInEarDetectionChange
                    )
                }
            }
        }
    }
}

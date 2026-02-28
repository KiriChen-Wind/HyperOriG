package com.redwind.hyperorig

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.redwind.hyperorig.pods.EqMode
import com.redwind.hyperorig.pods.NoiseControlMode
import com.redwind.hyperorig.ui.AppTheme
import com.redwind.hyperorig.ui.components.AncSwitch
import com.redwind.hyperorig.ui.components.PodStatus
import com.redwind.hyperorig.utils.miuiStrongToast.data.BatteryParams
import com.redwind.hyperorig.utils.miuiStrongToast.data.HyperOriGAction
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

class PopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val prefs = getSharedPreferences("hyperorig_settings", Context.MODE_PRIVATE)
            val colorSchemeMode = when (prefs.getInt("theme_mode", 0)) {
                1 -> ColorSchemeMode.Light
                2 -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            }
            AppTheme(colorSchemeMode = colorSchemeMode) {
                PopupContent(
                    onMore = {
                        val prefs = getSharedPreferences("hyperorig_settings", Context.MODE_PRIVATE)
                        if (prefs.getBoolean("open_orig", false)) {
                            val intent = packageManager.getLaunchIntentForPackage("com.orig.headset")
                            if (intent != null) {
                                startActivity(intent)
                            } else {
                                startActivity(Intent(this@PopupActivity, MainActivity::class.java))
                            }
                        } else {
                            startActivity(Intent(this@PopupActivity, MainActivity::class.java))
                        }
                        finish()
                    },
                    onDone = { finish() }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // 当活动失去焦点时（如用户返回桌面），自动结束弹窗
        finish()
    }
}

@Composable
private fun PopupContent(onMore: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val showDialog = remember { mutableStateOf(true) }
    val isMoreClicked = remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("hyperorig_settings", Context.MODE_PRIVATE) }
    val themeMode = remember { prefs.getInt("theme_mode", 0) }
    val systemDark = isSystemInDarkTheme()
    val isDarkMode = when (themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }

    val batteryParams = remember { mutableStateOf(BatteryParams()) }
    val ancMode = remember { mutableStateOf(NoiseControlMode.OFF) }
    val gameMode = remember { mutableStateOf(false) }
    val eqMode = remember { mutableStateOf(EqMode.BALANCED) }
    
    // 从缓存读取设备名称
    val cachedDeviceName = remember {
        val prefs = context.getSharedPreferences("hyperorig_device", Context.MODE_PRIVATE)
        val name = prefs.getString("device_name", "") ?: ""
        Log.d("HyperOriG-Popup", "从缓存读取设备名称: '$name'")
        name
    }
    val deviceName = remember { mutableStateOf(cachedDeviceName) }

    val broadcastReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                when (p1?.action) {
                    HyperOriGAction.ACTION_PODS_ANC_CHANGED -> {
                        val status = p1.getIntExtra("status", 1)
                        ancMode.value = when (status) {
                            1 -> NoiseControlMode.OFF
                            2 -> NoiseControlMode.TRANSPARENT
                            3 -> NoiseControlMode.NORMAL
                            4 -> NoiseControlMode.DEEP
                            5 -> NoiseControlMode.EXPERIMENT
                            6 -> NoiseControlMode.WIND_SUPPRESSION
                            else -> NoiseControlMode.OFF
                        }
                    }
                    HyperOriGAction.ACTION_PODS_BATTERY_CHANGED -> {
                        batteryParams.value =
                            p1.getParcelableExtra("status", BatteryParams::class.java)!!
                    }
                    HyperOriGAction.ACTION_PODS_CONNECTED -> {
                        val name = p1.getStringExtra("device_name") ?: ""
                        Log.d("HyperOriG-Popup", "收到DeviceName: $name")
                        deviceName.value = name
                        Log.d("HyperOriG-Popup", "发送DeviceName: ${deviceName.value}")
                        
                        // 保存设备名称到缓存
                        val prefs = context.getSharedPreferences("hyperorig_device", Context.MODE_PRIVATE)
                        prefs.edit().putString("device_name", name).apply()
                        Log.d("HyperOriG-Popup", "设备名称已保存到缓存: $name")
                    }
                    HyperOriGAction.ACTION_PODS_DISCONNECTED -> {
                        showDialog.value = false
                    }
                    HyperOriGAction.ACTION_PODS_GAME_MODE_CHANGED -> {
                        gameMode.value = p1.getBooleanExtra("enabled", false)
                    }
                    HyperOriGAction.ACTION_PODS_EQ_CHANGED -> {
                        val value = p1.getIntExtra("value", 0)
                        eqMode.value = EqMode.fromValue(value)
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        Log.d("HyperOriG-Popup", "注册广播接收器，监听设备状态更新")
        context.registerReceiver(broadcastReceiver, IntentFilter().apply {
            addAction(HyperOriGAction.ACTION_PODS_ANC_CHANGED)
            addAction(HyperOriGAction.ACTION_PODS_BATTERY_CHANGED)
            addAction(HyperOriGAction.ACTION_PODS_CONNECTED)
            addAction(HyperOriGAction.ACTION_PODS_DISCONNECTED)
            addAction(HyperOriGAction.ACTION_PODS_GAME_MODE_CHANGED)
            addAction(HyperOriGAction.ACTION_PODS_EQ_CHANGED)
        }, Context.RECEIVER_EXPORTED)

        Log.d("HyperOriG-Popup", "发送初始化广播，请求设备名称和状态")
        context.sendBroadcast(Intent(HyperOriGAction.ACTION_PODS_UI_INIT))
        // 弹窗显示时查询耳机状态
        Log.d("HyperOriG-Popup", "发送刷新状态广播")
        context.sendBroadcast(Intent(HyperOriGAction.ACTION_REFRESH_STATUS))

        onDispose {
            Log.d("HyperOriG-Popup", "弹窗销毁，注销广播接收器")
            try { context.unregisterReceiver(broadcastReceiver) } catch (_: Exception) {}
        }
    }

    fun setAncMode(mode: NoiseControlMode) { 
        ancMode.value = mode
        val status = when (mode) {
            NoiseControlMode.OFF -> 1
            NoiseControlMode.TRANSPARENT -> 2
            NoiseControlMode.NORMAL -> 3
            NoiseControlMode.DEEP -> 4
            NoiseControlMode.EXPERIMENT -> 5
            NoiseControlMode.WIND_SUPPRESSION -> 6
        }
        Intent(HyperOriGAction.ACTION_ANC_SELECT).apply {
            putExtra("status", status)
            context.sendBroadcast(this)
        }
    }

    fun setGameMode(enabled: Boolean) {
        gameMode.value = enabled
        Intent(HyperOriGAction.ACTION_GAME_MODE_SET).apply {
            putExtra("enabled", enabled)
            context.sendBroadcast(this)
        }
    }

    fun setEqMode(mode: EqMode) {
        eqMode.value = mode
        Intent(HyperOriGAction.ACTION_EQ_SET).apply {
            putExtra("value", mode.value)
            context.sendBroadcast(this)
        }
    }

    val dialogBgColor = if (isDarkMode) Color(0xFF1A1A1A) else Color(0xFFF7F7F7)

    // 弹窗显示时，如果设备名称为空，主动请求设备名称
    LaunchedEffect(showDialog.value) {
        if (showDialog.value && deviceName.value.isEmpty()) {
            Log.d("HyperOriG-Popup", "设备名称为空，主动请求设备名称")
            // 发送初始化广播，触发设备名称更新
            context.sendBroadcast(Intent(HyperOriGAction.ACTION_PODS_UI_INIT))
            // 延迟后再次检查，确保设备名称更新
            kotlinx.coroutines.delay(500)
            if (deviceName.value.isEmpty()) {
                Log.d("HyperOriG-Popup", "延迟后设备名称仍为空，发送刷新状态广播")
                context.sendBroadcast(Intent(HyperOriGAction.ACTION_REFRESH_STATUS))
            }
        }
    }

    // 监听设备名称变化
    LaunchedEffect(deviceName.value) {
        Log.d("HyperOriG-Popup", "注册DeviceName: ${deviceName.value}")
    }

    Scaffold(containerColor = Color.Transparent) { _ ->
        val dialogTitle = deviceName.value.ifEmpty { "HyperOriG" }
        Log.d("HyperOriG-Popup", "deviceName=${deviceName.value}, dialogTitle=$dialogTitle")
        
        SuperDialog(
            title = dialogTitle,
            show = showDialog,
            backgroundColor = dialogBgColor,
            onDismissRequest = {
                showDialog.value = false
            },
            onDismissFinished = {
                if (isMoreClicked.value) {
                    onMore()
                } else {
                    onDone()
                }
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    PodStatus(
                        batteryParams.value,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    AncSwitch(ancMode.value, onAncModeChange = { setAncMode(it) })
                }
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    SuperSwitch(
                        title = stringResource(R.string.game_mode),
                        summary = stringResource(R.string.game_mode_summary),
                        checked = gameMode.value,
                        onCheckedChange = { setGameMode(it) }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.more),
                        onClick = {
                            isMoreClicked.value = true
                            showDialog.value = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = stringResource(R.string.done),
                        onClick = {
                            showDialog.value = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

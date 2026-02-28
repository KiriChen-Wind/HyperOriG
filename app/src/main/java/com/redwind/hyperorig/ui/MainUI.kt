package com.redwind.hyperorig.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import com.redwind.hyperorig.MainActivity
import com.redwind.hyperorig.R
import com.redwind.hyperorig.pods.AppRfcommController
import com.redwind.hyperorig.pods.EqMode
import com.redwind.hyperorig.pods.NoiseControlMode
import kotlinx.coroutines.delay
import com.redwind.hyperorig.utils.miuiStrongToast.data.BatteryParams
import com.redwind.hyperorig.utils.miuiStrongToast.data.HyperOriGAction
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import android.Manifest

sealed interface Screen : NavKey {
    data object Home : Screen
    data object Settings : Screen
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainUI(
    themeMode: MutableState<Int> = mutableStateOf(0),
    onThemeModeChange: (Int) -> Unit = {}
) {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val context = LocalContext.current

    val mainTitle = remember { mutableStateOf("") }
    val batteryParams = remember { mutableStateOf(BatteryParams()) }
    val ancMode = remember { mutableStateOf(NoiseControlMode.OFF) }
    val hookConnected = remember { mutableStateOf(false) }
    val gameMode = remember { mutableStateOf(false) }
    val lowLatencyMode = remember { mutableStateOf(false) }
    val dualConnMode = remember { mutableStateOf(false) }
    val eqMode = remember { mutableStateOf(EqMode.BALANCED) }
    val windSuppressionMode = remember { mutableStateOf(false) }
    val inEarDetection = remember { mutableStateOf(false) }

    // Auto game mode preference (persisted)
    val prefs = remember { context.getSharedPreferences("hyperorig_settings", Context.MODE_PRIVATE) }
    val autoGameMode = remember { mutableStateOf(prefs.getBoolean("auto_game_mode", false)) }
    val openorig = remember { mutableStateOf(prefs.getBoolean("open_orig", false)) }

    val appController = remember { AppRfcommController() }
    // 初始化电量缓存
    LaunchedEffect(Unit) {
        appController.initBatteryCache(context)
    }
    val appConnState by appController.connectionState.collectAsState()
    val appBattery by appController.batteryParams.collectAsState()
    val appAnc by appController.ancMode.collectAsState()
    val appDeviceName by appController.deviceName.collectAsState()
    val appGameMode by appController.gameMode.collectAsState()
    val appLowLatency by appController.lowLatency.collectAsState()
    val appDualConn by appController.dualConn.collectAsState()
    val appEq by appController.eq.collectAsState()
    val appWindSuppression by appController.windSuppression.collectAsState()
    val appInEarDetection by appController.inEarDetection.collectAsState()

    val isStandaloneConnected = appConnState == AppRfcommController.ConnectionState.CONNECTED
    val isConnecting = appConnState == AppRfcommController.ConnectionState.CONNECTING
    val isError = appConnState == AppRfcommController.ConnectionState.ERROR
    val canShowDetailPage = hookConnected.value || isStandaloneConnected

    val displayBattery = if (isStandaloneConnected) appBattery else batteryParams.value
    val displayAnc = if (isStandaloneConnected) appAnc else ancMode.value
    val displayGameMode = if (isStandaloneConnected) appGameMode else gameMode.value
    val displayLowLatencyMode = if (isStandaloneConnected) appLowLatency else lowLatencyMode.value
    val displayDualConnMode = if (isStandaloneConnected) appDualConn else dualConnMode.value
    val displayEqMode = if (isStandaloneConnected) appEq else eqMode.value
    val displayWindSuppressionMode = if (isStandaloneConnected) appWindSuppression else windSuppressionMode.value
    val displayInEarDetection = if (isStandaloneConnected) appInEarDetection else inEarDetection.value
    val displayTitle = when {
        hookConnected.value -> mainTitle.value
        isStandaloneConnected -> appDeviceName
        isConnecting -> stringResource(R.string.connecting)
        isError -> stringResource(R.string.connection_failed_title)
        else -> ""
    }

    LaunchedEffect(displayTitle) {
        mainTitle.value = displayTitle
    }

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
                        val deviceName = p1.getStringExtra("device_name")
                        mainTitle.value = deviceName ?: ""
                        hookConnected.value = true
                        Log.i("HyperOriG", "pod connected via hook: $deviceName")
                    }

                    HyperOriGAction.ACTION_PODS_DISCONNECTED -> {
                        mainTitle.value = ""
                        hookConnected.value = false
                        if (p0 is MainActivity) {
                            p0.finish()
                        }
                    }

                    HyperOriGAction.ACTION_PODS_GAME_MODE_CHANGED -> {
                        val enabled = p1.getBooleanExtra("enabled", false)
                        gameMode.value = enabled
                        // 游戏模式开启时同步更新低延迟模式的UI状态
                        lowLatencyMode.value = enabled
                    }

                    HyperOriGAction.ACTION_PODS_LOW_LATENCY_CHANGED -> {
                        val enabled = p1.getBooleanExtra("enabled", false)
                        lowLatencyMode.value = enabled
                    }

                    HyperOriGAction.ACTION_PODS_DUAL_CONN_CHANGED -> {
                        val enabled = p1.getBooleanExtra("enabled", false)
                        dualConnMode.value = enabled
                    }

                    HyperOriGAction.ACTION_PODS_EQ_CHANGED -> {
                        val value = p1.getIntExtra("value", 0)
                        eqMode.value = EqMode.fromValue(value)
                    }

                    HyperOriGAction.ACTION_PODS_WIND_SUPPRESSION_CHANGED -> {
                        val enabled = p1.getBooleanExtra("enabled", false)
                        windSuppressionMode.value = enabled
                    }

                    HyperOriGAction.ACTION_PODS_IN_EAR_DETECTION_CHANGED -> {
                        val enabled = p1.getBooleanExtra("enabled", false)
                        inEarDetection.value = enabled
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        context.registerReceiver(broadcastReceiver, IntentFilter().apply {
            addAction(HyperOriGAction.ACTION_PODS_ANC_CHANGED)
            addAction(HyperOriGAction.ACTION_PODS_BATTERY_CHANGED)
            addAction(HyperOriGAction.ACTION_PODS_CONNECTED)
            addAction(HyperOriGAction.ACTION_PODS_DISCONNECTED)
            addAction(HyperOriGAction.ACTION_PODS_GAME_MODE_CHANGED)
            addAction(HyperOriGAction.ACTION_PODS_LOW_LATENCY_CHANGED)
            addAction(HyperOriGAction.ACTION_PODS_DUAL_CONN_CHANGED)
            addAction(HyperOriGAction.ACTION_PODS_EQ_CHANGED)
            addAction(HyperOriGAction.ACTION_PODS_WIND_SUPPRESSION_CHANGED)
            addAction(HyperOriGAction.ACTION_PODS_IN_EAR_DETECTION_CHANGED)
        }, Context.RECEIVER_EXPORTED)

        context.sendBroadcast(Intent(HyperOriGAction.ACTION_PODS_UI_INIT))

        onDispose {
            try {
                context.unregisterReceiver(broadcastReceiver)
            } catch (_: Exception) {}
            appController.disconnect()
        }
    }

    fun setAncMode(mode: NoiseControlMode) {
        if (isStandaloneConnected) {
            appController.setANCMode(mode)
            return
        }
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
            this.putExtra("status", status)
            context.sendBroadcast(this)
        }
    }

    fun setGameMode(enabled: Boolean) {
        if (isStandaloneConnected) {
            appController.setGameMode(enabled)
            return
        }
        gameMode.value = enabled
        // 游戏模式开启时同步更新低延迟模式的UI状态
        lowLatencyMode.value = enabled
        Intent(HyperOriGAction.ACTION_GAME_MODE_SET).apply {
            this.putExtra("enabled", enabled)
            context.sendBroadcast(this)
        }
    }

    fun setLowLatencyMode(enabled: Boolean) {
        if (isStandaloneConnected) {
            // 如果用户关闭低延迟模式，且游戏模式处于开启状态，则同时关闭游戏模式
            if (!enabled && appGameMode) {
                appController.setGameMode(false)
            }
            appController.setLowLatency(enabled)
            return
        }
        lowLatencyMode.value = enabled
        // 如果用户关闭低延迟模式，且游戏模式处于开启状态，则同时关闭游戏模式
        if (!enabled && gameMode.value) {
            gameMode.value = false
            Intent(HyperOriGAction.ACTION_GAME_MODE_SET).apply {
                this.putExtra("enabled", false)
                context.sendBroadcast(this)
            }
        }
        Intent(HyperOriGAction.ACTION_LOW_LATENCY_SET).apply {
            this.putExtra("enabled", enabled)
            context.sendBroadcast(this)
        }
    }

    fun setDualConnMode(enabled: Boolean) {
        if (isStandaloneConnected) {
            appController.setDualConn(enabled)
            return
        }
        dualConnMode.value = enabled
        Intent(HyperOriGAction.ACTION_DUAL_CONN_SET).apply {
            this.putExtra("enabled", enabled)
            context.sendBroadcast(this)
        }
    }

    fun setEqMode(mode: EqMode) {
        if (isStandaloneConnected) {
            appController.setEq(mode)
            return
        }
        eqMode.value = mode
        Intent(HyperOriGAction.ACTION_EQ_SET).apply {
            this.putExtra("value", mode.value)
            context.sendBroadcast(this)
        }
    }

    fun setWindSuppressionMode(enabled: Boolean) {
        if (isStandaloneConnected) {
            appController.setWindSuppression(enabled)
            return
        }
        windSuppressionMode.value = enabled
        Intent(HyperOriGAction.ACTION_WIND_SUPPRESSION_SET).apply {
            this.putExtra("enabled", enabled)
            context.sendBroadcast(this)
        }
    }

    fun setInEarDetection(enabled: Boolean) {
        if (isStandaloneConnected) {
            appController.setInEarDetection(enabled)
            return
        }
        inEarDetection.value = enabled
        Intent(HyperOriGAction.ACTION_IN_EAR_DETECTION_SET).apply {
            this.putExtra("enabled", enabled)
            context.sendBroadcast(this)
        }
    }

    fun onDeviceSelected(device: BluetoothDevice) {
        appController.connect(device, autoGameMode = autoGameMode.value)
    }

    fun refreshStatus() {
        if (isStandaloneConnected) {
            appController.refreshStatus()
        } else if (hookConnected.value) {
            context.sendBroadcast(Intent(HyperOriGAction.ACTION_REFRESH_STATUS))
        }
    }

    // 自动检测并连接已配对的耳机设备
    fun autoConnectToHeadset() {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter
        
        // 检查蓝牙是否开启
        if (adapter?.isEnabled != true) {
            Log.d("HyperOriG", "Bluetooth is not enabled, skipping auto connect")
            return
        }
        
        // 检查权限
        val hasConnectPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasConnectPermission) {
            Log.d("HyperOriG", "Bluetooth connect permission not granted, skipping auto connect")
            return
        }
        
        // 检查当前是否已经连接到耳机
        if (hookConnected.value || isStandaloneConnected) {
            Log.d("HyperOriG", "Already connected to headset, skipping auto connect")
            return
        }
        
        val pairedDevices: Set<BluetoothDevice> = adapter.bondedDevices ?: emptySet()
        
        // 查找已配对的耳机设备
        for (device in pairedDevices) {
            // 检查设备名称是否包含关键词（检查原始名称，不使用用户自定义的别名）
            val originalName = device.name ?: ""
            
            if (originalName.lowercase().contains("yuandao") || 
                originalName.lowercase().contains("orig") || 
                originalName.lowercase().contains("nicehck")) {
                // 自动连接到该设备
                appController.connect(device, autoGameMode = autoGameMode.value)
                return
            }
        }
    }

    // 当应用启动时，检查是否已经连接到耳机
    // 如果已经连接，则自动连接到该设备
    // 如果未连接，则显示设备选择界面
    LaunchedEffect(Unit) {
        // 延迟一点时间，确保蓝牙服务已初始化
        delay(500)
        
        // 检查当前是否已经连接到耳机
        if (hookConnected.value || isStandaloneConnected) {
            // 已经连接，不需要自动连接
            Log.d("HyperOriG", "Already connected to headset, skipping auto connect")
        } else {
            // 未连接，检查是否有已配对的耳机设备
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter
            
            // 检查蓝牙是否开启
            if (adapter?.isEnabled == true) {
                // 检查权限
                val hasConnectPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
                
                if (hasConnectPermission) {
                    // 检查是否有已配对的耳机设备
                    val pairedDevices: Set<BluetoothDevice> = adapter.bondedDevices ?: emptySet()
                    
                    // 查找已配对的耳机设备
                    for (device in pairedDevices) {
                        // 检查设备名称是否包含关键词（检查原始名称，不使用用户自定义的别名）
                        val originalName = device.name ?: ""
                        
                        if (originalName.lowercase().contains("yuandao") || 
                            originalName.lowercase().contains("orig") || 
                            originalName.lowercase().contains("nicehck")) {
                            // 检查该设备是否当前已连接
                            val isDeviceConnected = try {
                                // 尝试通过反射获取设备的连接状态
                                val method = BluetoothDevice::class.java.getMethod("isConnected")
                                method.invoke(device) as? Boolean ?: false
                            } catch (e: Exception) {
                                false
                            }
                            
                            // 如果设备已连接，则自动连接到该设备
                            if (isDeviceConnected) {
                                appController.connect(device, autoGameMode = autoGameMode.value)
                                return@LaunchedEffect
                            }
                        }
                    }
                }
            }
        }
    }

    // Each entry has its own Scaffold+TopAppBar so the full page transitions together
    val entryProvider = entryProvider<Screen> {
        entry<Screen.Home> {
            val appName = stringResource(R.string.app_name)
            val homeTitle = mainTitle.value.ifEmpty { appName }
            val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

            Scaffold(
                contentWindowInsets = WindowInsets(0),
                topBar = {
                    TopAppBar(
                        title = homeTitle,
                        largeTitle = homeTitle,
                        scrollBehavior = topAppBarScrollBehavior,
                        navigationIcon = {
                            if (canShowDetailPage || isConnecting || isError) {
                                IconButton(
                                    onClick = {
                                        // 返回到设备选择界面
                                        if (isStandaloneConnected) {
                                            appController.disconnect()
                                        } else if (hookConnected.value) {
                                            hookConnected.value = false
                                            mainTitle.value = ""
                                        }
                                    },
                                    modifier = Modifier.padding(start = 16.dp)
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Back,
                                        contentDescription = "Back"
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { backStack.add(Screen.Settings) },
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Settings,
                                    contentDescription = "Settings"
                                )
                            }
                        }
                    )
                }
            ) { padding ->
                AnimatedContent(
                    targetState = when {
                        canShowDetailPage -> "detail"
                        isConnecting -> "connecting"
                        isError -> "error"
                        else -> "picker"
                    },
                    label = "MainPageAnim"
                ) { state ->
                    Box(modifier = Modifier.padding(top = padding.calculateTopPadding())) {
                        when (state) {
                            "detail" -> {
                                // 进入耳机控制页面时查询耳机状态
                                LaunchedEffect(Unit) {
                                    refreshStatus()
                                }
                                // 当连接状态变化时重新查询耳机状态
                                LaunchedEffect(isStandaloneConnected, hookConnected.value) {
                                    if (isStandaloneConnected || hookConnected.value) {
                                        refreshStatus()
                                    }
                                }
                                PodDetailPage(
                                    batteryParams = displayBattery,
                                    ancMode = displayAnc,
                                    onAncModeChange = { setAncMode(it) },
                                    gameMode = displayGameMode,
                                    onGameModeChange = { setGameMode(it) },
                                    lowLatencyMode = displayLowLatencyMode,
                                    onLowLatencyModeChange = { setLowLatencyMode(it) },
                                    dualConnMode = displayDualConnMode,
                                    onDualConnModeChange = { setDualConnMode(it) },
                                    eqMode = displayEqMode,
                                    onEqModeChange = { setEqMode(it) },
                                    inEarDetection = displayInEarDetection,
                                    onInEarDetectionChange = { setInEarDetection(it) }
                                )
                            }
                            "connecting" -> ConnectingPage()
                            "error" -> ErrorPage(onRetry = { appController.disconnect() })
                            else -> DevicePickerPage(onDeviceSelected = { onDeviceSelected(it) })
                        }
                    }
                }
            }
        }
        entry<Screen.Settings> {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                topBar = {
                    TopAppBar(
                        title = stringResource(R.string.settings),
                        navigationIcon = {
                            IconButton(
                                onClick = { backStack.removeLast() },
                                modifier = Modifier.padding(start = 16.dp)
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    )
                }
            ) { padding ->
                SettingsPage(
                    modifier = Modifier.padding(top = padding.calculateTopPadding()),
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    openorig = openorig,
                    onOpenorigChange = {
                        openorig.value = it
                        prefs.edit().putBoolean("open_orig", it).apply()
                    }
                )
            }
        }
    }

    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryProvider = entryProvider
    )

    NavDisplay(
        entries = entries,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLast()
            } else {
                (context as? Activity)?.finish()
            }
        }
    )
}

@Composable
fun ConnectingPage() {
    val primaryColor = MiuixTheme.colorScheme.primary
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(modifier = Modifier.size(48.dp)) {
                drawArc(
                    color = primaryColor,
                    startAngle = angle,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Text(
                stringResource(R.string.connecting),
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun ErrorPage(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(R.string.connect_failed),
                color = Color(0xFFFF3B30)
            )
            TextButton(
                text = stringResource(R.string.go_back),
                onClick = onRetry,
                modifier = Modifier.padding(top = 16.dp),
                minWidth = 135.dp,
                minHeight = 5.dp
            )
        }
    }
}

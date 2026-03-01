package com.redwind.hyperorig.pods

import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaRoute2Info
import android.media.MediaRouter2
import android.media.RouteDiscoveryPreference
import android.util.Log
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge
import de.robv.android.xposed.XposedHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.redwind.hyperorig.BuildConfig
import com.redwind.hyperorig.utils.MediaControl
import com.redwind.hyperorig.utils.SystemApisUtils
import com.redwind.hyperorig.utils.SystemApisUtils.setIconVisibility
import com.redwind.hyperorig.utils.miuiStrongToast.MiuiStrongToastUtil
import com.redwind.hyperorig.utils.miuiStrongToast.MiuiStrongToastUtil.cancelPodsNotificationByMiuiBt
import com.redwind.hyperorig.utils.miuiStrongToast.data.BatteryParams
import com.redwind.hyperorig.utils.miuiStrongToast.data.HyperOriGAction
import com.redwind.hyperorig.utils.miuiStrongToast.data.PodParams
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.Executor

// 持久化电量相关常量
private const val PREFS_NAME = "hyperorig_battery"
private const val KEY_LEFT_BATTERY = "left_battery"
private const val KEY_LEFT_CHARGING = "left_charging"
private const val KEY_RIGHT_BATTERY = "right_battery"
private const val KEY_RIGHT_CHARGING = "right_charging"
private const val KEY_CASE_BATTERY = "case_battery"
private const val KEY_CASE_CHARGING = "case_charging"

@SuppressLint("MissingPermission", "StaticFieldLeak")
object RfcommController {
    private const val TAG = "HyperOriG-RfcommController"
    private const val BATTERY_POLL_INTERVAL_MS = 30_000L

    private val SPP_UUID: UUID = UUID.fromString("0000a100-1000-8000-4e48-434b4354524c")

    private var socket: BluetoothSocket? = null
    private var mContext: Context? = null
    lateinit var mDevice: BluetoothDevice
    private val audioManager: AudioManager? by lazy {
        mContext?.getSystemService(AudioManager::class.java)
    }
    private lateinit var mPrefsBridge: YukiHookPrefsBridge

    private var scanToken: MediaRouter2.ScanToken? = null
    var routes: List<MediaRoute2Info> = listOf()
    private lateinit var mediaRouter: MediaRouter2

    private var mShowedConnectedToast = false
    private var isConnected = false
    private var lastTempBatt = 0
    lateinit var currentBatteryParams: BatteryParams
    private var currentAnc: Int = 1
    private var currentGameMode: Boolean = false
    private var currentLowLatency: Boolean = false
    private var currentDualConn: Boolean = false
    private var currentEq: EqMode = EqMode.BALANCED
    private var currentWindSuppression: Boolean = false
    private var currentInEarDetection: Boolean = false

    // 缓存电量，只在收到汇报时更新
    private var cachedLeftBattery: PodParams? = null
    private var cachedRightBattery: PodParams? = null
    private var cachedCaseBattery: PodParams? = null

    private var batteryPollJob: kotlinx.coroutines.Job? = null

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            if (p1?.action == HyperOriGAction.ACTION_GET_PODS_MAC) {
                Intent(HyperOriGAction.ACTION_PODS_MAC_RECEIVED).apply {
                    Log.i(TAG, "${p1.action} ,mac ${mDevice.address}")
                    this.`package` = "com.android.systemui"
                    this.putExtra("mac", mDevice.address)
                    this.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    p0?.sendBroadcast(this)
                    return
                }
            }
            handleUIEvent(p1!!)
        }
    }

    private fun changeUIAncStatus(status: Int) {
        if (status < 1 || status > 6) return
        Intent(HyperOriGAction.ACTION_PODS_ANC_CHANGED).apply {
            this.putExtra("status", status)
            this.`package` = BuildConfig.APPLICATION_ID
            this.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            mContext!!.sendBroadcast(this)
        }
    }

    private fun changeUIBatteryStatus(status: BatteryParams) {
        Intent(HyperOriGAction.ACTION_PODS_BATTERY_CHANGED).apply {
            this.putExtra("status", status)
            this.`package` = BuildConfig.APPLICATION_ID
            this.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            mContext!!.sendBroadcast(this)
        }
    }

    private fun changeUIGameModeStatus(enabled: Boolean) {
        Intent(HyperOriGAction.ACTION_PODS_GAME_MODE_CHANGED).apply {
            this.putExtra("enabled", enabled)
            this.`package` = BuildConfig.APPLICATION_ID
            this.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            mContext!!.sendBroadcast(this)
        }
    }

    private fun changeUILowLatencyStatus(enabled: Boolean) {
        Intent(HyperOriGAction.ACTION_PODS_LOW_LATENCY_CHANGED).apply {
            this.putExtra("enabled", enabled)
            this.`package` = BuildConfig.APPLICATION_ID
            this.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            mContext!!.sendBroadcast(this)
        }
    }

    private fun changeUIDualConnStatus(enabled: Boolean) {
        Intent(HyperOriGAction.ACTION_PODS_DUAL_CONN_CHANGED).apply {
            this.putExtra("enabled", enabled)
            this.`package` = BuildConfig.APPLICATION_ID
            this.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            mContext!!.sendBroadcast(this)
        }
    }

    private fun changeUIEqStatus(mode: EqMode) {
        Intent(HyperOriGAction.ACTION_PODS_EQ_CHANGED).apply {
            this.putExtra("value", mode.value)
            this.`package` = BuildConfig.APPLICATION_ID
            this.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            mContext!!.sendBroadcast(this)
        }
    }

    private fun changeUIWindSuppressionStatus(enabled: Boolean) {
        Intent(HyperOriGAction.ACTION_PODS_WIND_SUPPRESSION_CHANGED).apply {
            this.putExtra("enabled", enabled)
            this.`package` = BuildConfig.APPLICATION_ID
            this.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            mContext!!.sendBroadcast(this)
        }
    }

    private fun changeUIInEarDetectionStatus(enabled: Boolean) {
        Intent(HyperOriGAction.ACTION_PODS_IN_EAR_DETECTION_CHANGED).apply {
            this.putExtra("enabled", enabled)
            this.`package` = BuildConfig.APPLICATION_ID
            this.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            mContext!!.sendBroadcast(this)
        }
    }

    fun handleUIEvent(intent: Intent) {
        when (intent.action) {
            HyperOriGAction.ACTION_PODS_UI_INIT -> {
                Log.i(TAG, "UI Init")
                val deviceName = mDevice.alias ?: mDevice.name ?: mDevice.address
                Intent(HyperOriGAction.ACTION_PODS_CONNECTED).apply {
                    this.putExtra("device_name", deviceName)
                    mContext!!.sendBroadcast(this)
                }
                // 保存设备名称到缓存
                val prefs = mContext!!.getSharedPreferences("hyperorig_device", Context.MODE_PRIVATE)
                prefs.edit().putString("device_name", deviceName).apply()
                Log.d(TAG, "设备名称已保存到缓存: $deviceName")
                // 先查询耳机状态，然后再发送状态给UI
                queryStatus()
            }
            HyperOriGAction.ACTION_ANC_SELECT -> {
                val status = intent.getIntExtra("status", 0)
                setANCMode(status)
            }
            HyperOriGAction.ACTION_REFRESH_STATUS -> {
                // 直接查询耳机状态，不先发送缓存状态
                // 这样可以避免状态闪烁
                queryStatus()
            }
            HyperOriGAction.ACTION_GAME_MODE_SET -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                setGameMode(enabled)
            }
            HyperOriGAction.ACTION_LOW_LATENCY_SET -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                setLowLatency(enabled)
            }
            HyperOriGAction.ACTION_DUAL_CONN_SET -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                setDualConn(enabled)
            }
            HyperOriGAction.ACTION_EQ_SET -> {
                val value = intent.getIntExtra("value", 0)
                setEq(EqMode.fromValue(value))
            }
            HyperOriGAction.ACTION_WIND_SUPPRESSION_SET -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                setWindSuppression(enabled)
            }
            HyperOriGAction.ACTION_IN_EAR_DETECTION_SET -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                setInEarDetection(enabled)
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun handleBatteryChanged(result: BatteryParser.BatteryResult) {
        // 更新左耳电量缓存
        if (result.left != null) {
            cachedLeftBattery = PodParams(
                result.left.level,
                result.left.isCharging,
                true,
                0
            )
            saveBattery(KEY_LEFT_BATTERY, KEY_LEFT_CHARGING, result.left.level, result.left.isCharging)
        }
        // 更新右耳电量缓存
        if (result.right != null) {
            cachedRightBattery = PodParams(
                result.right.level,
                result.right.isCharging,
                true,
                0
            )
            saveBattery(KEY_RIGHT_BATTERY, KEY_RIGHT_CHARGING, result.right.level, result.right.isCharging)
        }
        // 更新耳机盒电量缓存
        if (result.case != null) {
            cachedCaseBattery = PodParams(
                result.case.level,
                result.case.isCharging,
                true,
                0
            )
            saveBattery(KEY_CASE_BATTERY, KEY_CASE_CHARGING, result.case.level, result.case.isCharging)
        }

        // 使用缓存的电量（如果有的话）
        val left = cachedLeftBattery ?: PodParams(
            result.left?.level ?: 0,
            result.left?.isCharging == true,
            result.left != null,
            0
        )
        val right = cachedRightBattery ?: PodParams(
            result.right?.level ?: 0,
            result.right?.isCharging == true,
            result.right != null,
            0
        )
        val case = cachedCaseBattery ?: PodParams(
            result.case?.level ?: 0,
            result.case?.isCharging == true,
            result.case != null,
            0
        )

        if (BuildConfig.DEBUG) {
            Log.v(TAG, "batt left ${left.battery} right ${right.battery} case ${case.battery}")
        }

        val shouldShowToast = !mShowedConnectedToast
        if (shouldShowToast) {
            val hasValidData = (left.isConnected && left.battery > 0) ||
                    (right.isConnected && right.battery > 0)
            if (!hasValidData) return
        }

        val batteryParams = BatteryParams(left, right, case)
        currentBatteryParams = batteryParams

        if (shouldShowToast) {
            MiuiStrongToastUtil.showPodsBatteryToastByMiuiBt(mContext!!, batteryParams)
            mShowedConnectedToast = true
        }
        MiuiStrongToastUtil.showPodsNotificationByMiuiBt(mContext!!, batteryParams, mDevice)
        changeUIBatteryStatus(batteryParams)

        lastTempBatt = if (left.isConnected && right.isConnected)
            minOf(left.battery, right.battery)
        else if (left.isConnected)
            left.battery
        else if (right.isConnected)
            right.battery
        else SystemApisUtils.BATTERY_LEVEL_UNKNOWN

        setRegularBatteryLevel(lastTempBatt)
    }

    private val routeCallback = object : MediaRouter2.RouteCallback() {
        override fun onRoutesUpdated(routes: List<MediaRoute2Info>) {
            Log.v(TAG, "routes updated: $routes")
            this@RfcommController.routes = routes
        }
    }

    private fun startRoutesScan() {
        val executor = Executor { p0 ->
            CoroutineScope(Dispatchers.IO).launch { p0?.run() }
        }
        val preferredFeature = listOf(MediaRoute2Info.FEATURE_LIVE_AUDIO, MediaRoute2Info.FEATURE_LIVE_VIDEO)
        mediaRouter.registerRouteCallback(executor, routeCallback, RouteDiscoveryPreference.Builder(preferredFeature, true).build())
        scanToken = mediaRouter.requestScan(MediaRouter2.ScanRequest.Builder().build())
    }

    private fun stopRoutesScan() {
        scanToken?.let { mediaRouter.cancelScanRequest(it) }
        mediaRouter.unregisterRouteCallback(routeCallback)
    }

    // 初始化时从 SharedPreferences 读取缓存的电量
    fun initBatteryCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 读取左耳电量
        val leftBattery = prefs.getInt(KEY_LEFT_BATTERY, 0)
        val leftCharging = prefs.getBoolean(KEY_LEFT_CHARGING, false)
        if (leftBattery > 0) {
            cachedLeftBattery = PodParams(leftBattery, leftCharging, true, 0)
        }

        // 读取右耳电量
        val rightBattery = prefs.getInt(KEY_RIGHT_BATTERY, 0)
        val rightCharging = prefs.getBoolean(KEY_RIGHT_CHARGING, false)
        if (rightBattery > 0) {
            cachedRightBattery = PodParams(rightBattery, rightCharging, true, 0)
        }

        // 读取耳机盒电量
        val caseBattery = prefs.getInt(KEY_CASE_BATTERY, 0)
        val caseCharging = prefs.getBoolean(KEY_CASE_CHARGING, false)
        if (caseBattery > 0) {
            cachedCaseBattery = PodParams(caseBattery, caseCharging, true, 0)
        }
    }

    // 保存电量到 SharedPreferences
    private fun saveBattery(keyBattery: String, keyCharging: String, battery: Int, isCharging: Boolean) {
        mContext?.let { context ->
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putInt(keyBattery, battery)
                putBoolean(keyCharging, isCharging)
                apply()
            }
        }
    }

    fun connectPod(context: Context, device: BluetoothDevice, prefsBridge: YukiHookPrefsBridge) {
        mContext = context
        mDevice = device
        mPrefsBridge = prefsBridge

        // 初始化电池缓存
        initBatteryCache(context)

        context.registerReceiver(broadcastReceiver, IntentFilter().apply {
            this.addAction(HyperOriGAction.ACTION_ANC_SELECT)
            this.addAction(HyperOriGAction.ACTION_PODS_UI_INIT)
            this.addAction(HyperOriGAction.ACTION_GET_PODS_MAC)
            this.addAction(HyperOriGAction.ACTION_REFRESH_STATUS)
            this.addAction(HyperOriGAction.ACTION_GAME_MODE_SET)
            this.addAction(HyperOriGAction.ACTION_LOW_LATENCY_SET)
            this.addAction(HyperOriGAction.ACTION_DUAL_CONN_SET)
            this.addAction(HyperOriGAction.ACTION_EQ_SET)
            this.addAction(HyperOriGAction.ACTION_WIND_SUPPRESSION_SET)
            this.addAction(HyperOriGAction.ACTION_IN_EAR_DETECTION_SET)
        }, Context.RECEIVER_EXPORTED)

        val deviceName = device.alias ?: device.name ?: device.address
        Intent(HyperOriGAction.ACTION_PODS_CONNECTED).apply {
            this.putExtra("device_name", deviceName)
            context.sendBroadcast(this)
        }
        // 保存设备名称到缓存
        val prefs = context.getSharedPreferences("hyperorig_device", Context.MODE_PRIVATE)
        prefs.edit().putString("device_name", deviceName).apply()
        Log.d(TAG, "设备名称已保存到缓存: $deviceName")

        MediaControl.mContext = mContext
        mediaRouter = MediaRouter2.getInstance(mContext!!)
        startRoutesScan()

        isConnected = true

        CoroutineScope(Dispatchers.IO).launch {
            delay(500)
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter?.isDiscovering == true) {
                    adapter.cancelDiscovery()
                }
                
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket!!.connect()
                Log.d(TAG, "RFCOMM connected via UUID!")

                startPacketReader(socket!!.inputStream)

                delay(300)
                queryStatus()

                val prefs = context.getSharedPreferences("hyperorig_settings", Context.MODE_PRIVATE)
                if (prefs.getBoolean("auto_game_mode", false)) {
                    delay(100)
                    sendPacketSafe(Enums.GAME_MODE_ON)
                }
            } catch (e: IOException) {
                Log.e(TAG, "RFCOMM connect failed, trying insecure...", e)
                try {
                    socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                    socket!!.connect()
                    Log.d(TAG, "RFCOMM connected via insecure UUID!")
                    
                    startPacketReader(socket!!.inputStream)
                    delay(300)
                    queryStatus()
                } catch (e2: IOException) {
                    Log.e(TAG, "RFCOMM connect failed completely", e2)
                    isConnected = false
                }
            }
        }

        batteryPollJob = CoroutineScope(Dispatchers.IO).launch {
            delay(2000)
            while (isConnected) {
                delay(BATTERY_POLL_INTERVAL_MS)
                if (isConnected) {
                    queryStatus()
                }
            }
        }
    }

    private fun startPacketReader(inputStream: InputStream) {
        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            val dataBuffer = ByteArray(2048)
            var dataBufferPos = 0
            
            try {
                while (isConnected) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        System.arraycopy(buffer, 0, dataBuffer, dataBufferPos, bytesRead)
                        dataBufferPos += bytesRead
                        dataBufferPos = processPackets(dataBuffer, dataBufferPos)
                    } else if (bytesRead == -1) {
                        Log.d(TAG, "RFCOMM stream ended")
                        break
                    }
                }
            } catch (e: IOException) {
                if (isConnected) {
                    Log.e(TAG, "RFCOMM read error", e)
                }
            }
        }
    }

    private fun processPackets(buffer: ByteArray, length: Int): Int {
        var pos = 0
        while (pos < length) {
            if ((buffer[pos].toInt() and 0xFF) != 0x4E) {
                pos++
                continue
            }

            if (pos + 3 >= length) break

            val packetLength = (buffer[pos + 1].toInt() and 0xFF) + 3

            if (pos + packetLength > length) break

            val packet = ByteArray(packetLength)
            System.arraycopy(buffer, pos, packet, 0, packetLength)
            handleOriGPacket(packet)

            pos += packetLength
        }

        if (pos < length) {
            val remaining = length - pos
            System.arraycopy(buffer, pos, buffer, 0, remaining)
            return remaining
        }
        return 0
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun handleOriGPacket(packet: ByteArray) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Received: ${packet.toHexString(HexFormat.UpperCase)}")
        }

        val batteryResult = BatteryParser.parse(packet)
        if (batteryResult != null) {
            handleBatteryChanged(batteryResult)
            return
        }

        val ancResult = AncModeParser.parse(packet)
        if (ancResult != null) {
            Log.d(TAG, "ANC mode received: $ancResult")
            // 只有当抗风噪开启时才忽略 ANC 响应
            // 这样可以避免抗风噪开启时 ANC 查询返回 OFF 导致的闪烁
            // 同时确保抗风噪关闭时能正确同步 ANC 状态
            if (!currentWindSuppression) {
                currentAnc = when (ancResult) {
                    NoiseControlMode.OFF -> 1
                    NoiseControlMode.TRANSPARENT -> 2
                    NoiseControlMode.NORMAL -> 3
                    NoiseControlMode.DEEP -> 4
                    NoiseControlMode.EXPERIMENT -> 5
                    NoiseControlMode.WIND_SUPPRESSION -> 6
                }
                changeUIAncStatus(currentAnc)
            }
            return
        }

        val gameModeResult = GameModeParser.parse(packet)
        if (gameModeResult != null) {
            Log.d(TAG, "Game mode received: $gameModeResult")
            currentGameMode = gameModeResult
            changeUIGameModeStatus(gameModeResult)
            return
        }

        val lowLatencyResult = LowLatencyParser.parse(packet)
        if (lowLatencyResult != null) {
            Log.d(TAG, "Low latency received: $lowLatencyResult")
            currentLowLatency = lowLatencyResult
            changeUILowLatencyStatus(lowLatencyResult)
            return
        }

        val dualConnResult = DualConnParser.parse(packet)
        if (dualConnResult != null) {
            Log.d(TAG, "Dual conn received: $dualConnResult")
            currentDualConn = dualConnResult
            changeUIDualConnStatus(dualConnResult)
            return
        }

        val eqResult = EqParser.parse(packet)
        if (eqResult != null) {
            Log.d(TAG, "EQ received: $eqResult")
            currentEq = eqResult
            changeUIEqStatus(eqResult)
            return
        }

        val windSuppressionResult = WindSuppressionParser.parse(packet)
        if (windSuppressionResult != null) {
            Log.d(TAG, "Wind suppression received: $windSuppressionResult")
            val oldWindSuppression = currentWindSuppression
            currentWindSuppression = windSuppressionResult
            // 抗风噪也是ANC模式的一种，需要同步更新ANC状态
            if (windSuppressionResult) {
                currentAnc = 6 // WIND_SUPPRESSION
                changeUIAncStatus(currentAnc)
            } else if (oldWindSuppression && currentAnc == 6) {
                // 抗风噪关闭时，不要直接设置为OFF
                // 等待ANC查询的响应来更新实际状态
                // 这样可以确保从抗风噪切换到其他模式时状态正确同步
            }
            changeUIWindSuppressionStatus(windSuppressionResult)
            return
        }

        val inEarDetectionResult = InEarDetectionParser.parse(packet)
        if (inEarDetectionResult != null) {
            Log.d(TAG, "In-ear detection received: $inEarDetectionResult")
            currentInEarDetection = inEarDetectionResult
            changeUIInEarDetectionStatus(inEarDetectionResult)
            return
        }

        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Unknown OriG packet: ${packet.toHexString(HexFormat.UpperCase)}")
        }
    }

    fun disconnectedPod(context: Context, device: BluetoothDevice) {
        isConnected = false
        batteryPollJob?.cancel()

        try {
            socket?.close()
        } catch (_: IOException) {}
        socket = null

        mContext?.let {
            stopRoutesScan()
            cancelPodsNotificationByMiuiBt(context, device)
            Intent(HyperOriGAction.ACTION_PODS_DISCONNECTED).apply {
                context.sendBroadcast(this)
            }
            it.unregisterReceiver(broadcastReceiver)
        }

        mShowedConnectedToast = false
        mContext = null
        MediaControl.mContext = null
    }

    private fun sendPacketSafe(packet: ByteArray) {
        try {
            socket?.outputStream?.write(packet)
            socket?.outputStream?.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Send packet failed", e)
        }
    }

    fun setGameMode(enabled: Boolean) {
        Log.d(TAG, "setGameMode: $enabled")
        currentGameMode = enabled
        currentLowLatency = enabled
        val packet = if (enabled) Enums.GAME_MODE_ON else Enums.GAME_MODE_OFF
        CoroutineScope(Dispatchers.IO).launch {
            sendPacketSafe(packet)
        }
        changeUIGameModeStatus(enabled)
        changeUILowLatencyStatus(enabled)
    }

    fun setANCMode(mode: Int) {
        Log.d(TAG, "setANCMode: $mode")
        if (mode == currentAnc) {
            Log.d(TAG, "Current ANC mode is already $mode, skipping")
            return
        }
        val packet = when (mode) {
            1 -> Enums.ANC_OFF
            2 -> Enums.ANC_TRANSPARENT
            3 -> Enums.ANC_NORMAL
            4 -> Enums.ANC_DEEP
            5 -> Enums.ANC_EXPERIMENT
            6 -> Enums.ANC_WIND_SUPPRESSION
            else -> return
        }
        currentAnc = mode
        // 抗风噪模式需要同步更新windSuppression状态
        if (mode == 6) {
            currentWindSuppression = true
            changeUIWindSuppressionStatus(true)
        } else if (currentWindSuppression) {
            currentWindSuppression = false
            changeUIWindSuppressionStatus(false)
        }
        CoroutineScope(Dispatchers.IO).launch {
            sendPacketSafe(packet)
        }
        changeUIAncStatus(currentAnc)
    }

    fun setLowLatency(enabled: Boolean) {
        Log.d(TAG, "setLowLatency: $enabled")
        currentLowLatency = enabled
        val packet = if (enabled) Enums.LOW_LATENCY_ON else Enums.LOW_LATENCY_OFF
        CoroutineScope(Dispatchers.IO).launch {
            sendPacketSafe(packet)
        }
        changeUILowLatencyStatus(enabled)
    }

    fun setDualConn(enabled: Boolean) {
        Log.d(TAG, "setDualConn: $enabled")
        currentDualConn = enabled
        val packet = if (enabled) Enums.DUAL_CONN_ON else Enums.DUAL_CONN_OFF
        CoroutineScope(Dispatchers.IO).launch {
            sendPacketSafe(packet)
        }
        changeUIDualConnStatus(enabled)
    }

    fun setEq(mode: EqMode) {
        Log.d(TAG, "setEq: $mode")
        if (mode == currentEq) {
            Log.d(TAG, "Current EQ mode is already $mode, skipping")
            return
        }
        currentEq = mode
        val packet = OriGPackets.buildPacket(Op.EQ_SET, mode.value.toByte(), 0x00)
        CoroutineScope(Dispatchers.IO).launch {
            sendPacketSafe(packet)
        }
        changeUIEqStatus(mode)
    }

    fun setWindSuppression(enabled: Boolean) {
        Log.d(TAG, "setWindSuppression: $enabled")
        val oldEnabled = currentWindSuppression
        currentWindSuppression = enabled
        val packet = if (enabled) Enums.WIND_SUPPRESSION_ON else Enums.WIND_SUPPRESSION_OFF
        CoroutineScope(Dispatchers.IO).launch {
            sendPacketSafe(packet)
        }
        // 抗风噪开启时，设置ANC状态为抗风噪
        if (enabled) {
            currentAnc = 6 // WIND_SUPPRESSION
            changeUIAncStatus(currentAnc)
        } else if (oldEnabled && currentAnc == 6) {
            // 抗风噪关闭时，不要直接设置为OFF
            // 等待查询状态时的ANC响应来更新实际状态
        }
        changeUIWindSuppressionStatus(enabled)
    }

    fun setInEarDetection(enabled: Boolean) {
        Log.d(TAG, "setInEarDetection: $enabled")
        currentInEarDetection = enabled
        val packet = if (enabled) Enums.IN_EAR_DETECTION_ON else Enums.IN_EAR_DETECTION_OFF
        CoroutineScope(Dispatchers.IO).launch {
            sendPacketSafe(packet)
        }
        changeUIInEarDetectionStatus(enabled)
    }

    fun queryBattery() {
        CoroutineScope(Dispatchers.IO).launch {
            sendPacketSafe(Enums.QUERY_BATTERY)
        }
    }

    fun queryStatus() {
        CoroutineScope(Dispatchers.IO).launch {
            sendPacketSafe(Enums.QUERY_BATTERY)
            delay(50)
            sendPacketSafe(OriGPackets.buildPacket(Op.WIND_SUPPRESSION_QUERY))
            delay(50)
            sendPacketSafe(Enums.QUERY_ANC)
            delay(50)
            sendPacketSafe(Enums.QUERY_GAME_MODE)
            delay(50)
            sendPacketSafe(OriGPackets.buildPacket(Op.LOW_LATENCY_QUERY))
            delay(50)
            sendPacketSafe(OriGPackets.buildPacket(Op.DUAL_CONN_QUERY))
            delay(50)
            sendPacketSafe(OriGPackets.buildPacket(Op.EQ_QUERY))
            delay(50)
            sendPacketSafe(Enums.QUERY_IN_EAR_DETECTION)
        }
    }

    fun disconnectAudio(context: Context, device: BluetoothDevice?) {
        val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter

        MediaControl.sendPause()

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HEADSET) {
                    try {
                        val method = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                    }
                }
            }
            override fun onServiceDisconnected(profile: Int) { }
        }, BluetoothProfile.HEADSET)

        CoroutineScope(Dispatchers.Default).launch {
            delay(500)
            for (route in routes) {
                if (route.type == MediaRoute2Info.TYPE_BUILTIN_SPEAKER) {
                    Log.d(TAG, "found speaker route $route")
                    mediaRouter.transferTo(route)
                }
            }
        }

        setRegularBatteryLevel(lastTempBatt)
    }

    fun connectAudio(context: Context, device: BluetoothDevice?) {
        val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HEADSET) {
                    try {
                        val method = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                    }
                }
            }
            override fun onServiceDisconnected(profile: Int) { }
        }, BluetoothProfile.HEADSET)

        for (route in routes) {
            if (route.type == MediaRoute2Info.TYPE_BLUETOOTH_A2DP && route.name == device!!.name) {
                Log.d(TAG, "found bt route $route")
                mediaRouter.transferTo(route)
            }
        }

        val statusBarManager = context.getSystemService("statusbar") as StatusBarManager
        statusBarManager.setIconVisibility("wireless_headset", true)
        setRegularBatteryLevel(lastTempBatt)
    }

    fun setRegularBatteryLevel(level: Int) {
        try {
            val service = XposedHelpers.getObjectField(mContext, "mAdapterService")
            XposedHelpers.callMethod(service, "setBatteryLevel", mDevice, level, false)
        } catch (e: Exception) {
            Log.e(TAG, "setRegularBatteryLevel failed", e)
        }
    }
}

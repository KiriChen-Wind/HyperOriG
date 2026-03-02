package com.redwind.hyperorig.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.redwind.hyperorig.BuildConfig
import com.redwind.hyperorig.pods.*
import com.redwind.hyperorig.utils.miuiStrongToast.data.BatteryParams
import com.redwind.hyperorig.utils.miuiStrongToast.data.PodParams
import java.io.IOException
import java.io.InputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class AppRfcommController {
    companion object {
        private const val TAG = "HyperOriG-AppRfcomm"
        private const val BATTERY_POLL_INTERVAL_MS = 30_000L
        private const val PREFS_NAME = "hyperorig_battery"
        private const val KEY_LEFT_BATTERY = "left_battery"
        private const val KEY_LEFT_CHARGING = "left_charging"
        private const val KEY_RIGHT_BATTERY = "right_battery"
        private const val KEY_RIGHT_CHARGING = "right_charging"
        private const val KEY_CASE_BATTERY = "case_battery"
        private const val KEY_CASE_CHARGING = "case_charging"
        private val SPP_UUID: UUID = UUID.fromString("0000a100-1000-8000-4e48-434b4354524c")
    }

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    private var socket: BluetoothSocket? = null
    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var batteryPollJob: Job? = null
    private var appContext: Context? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _batteryParams = MutableStateFlow(BatteryParams())
    val batteryParams: StateFlow<BatteryParams> = _batteryParams

    // 缓存电量，只在收到汇报时更新
    private var cachedLeftBattery: PodParams? = null
    private var cachedRightBattery: PodParams? = null
    private var cachedCaseBattery: PodParams? = null

    private val _ancMode = MutableStateFlow(NoiseControlMode.OFF)

    // 初始化时从 SharedPreferences 读取缓存的电量
    fun initBatteryCache(context: Context) {
        appContext = context.applicationContext
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
        appContext?.let { context ->
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putInt(keyBattery, battery)
                putBoolean(keyCharging, isCharging)
                apply()
            }
        }
    }
    val ancMode: StateFlow<NoiseControlMode> = _ancMode

    private val _deviceName = MutableStateFlow("")
    val deviceName: StateFlow<String> = _deviceName

    private val _gameMode = MutableStateFlow(false)
    val gameMode: StateFlow<Boolean> = _gameMode

    private val _lowLatency = MutableStateFlow(false)
    val lowLatency: StateFlow<Boolean> = _lowLatency

    private val _dualConn = MutableStateFlow(false)
    val dualConn: StateFlow<Boolean> = _dualConn

    private val _eq = MutableStateFlow(EqMode.BALANCED)
    val eq: StateFlow<EqMode> = _eq

    private val _windSuppression = MutableStateFlow(false)
    val windSuppression: StateFlow<Boolean> = _windSuppression

    private val _inEarDetection = MutableStateFlow(false)
    val inEarDetection: StateFlow<Boolean> = _inEarDetection

    fun connect(device: BluetoothDevice, autoGameMode: Boolean = false) {
        if (_connectionState.value == ConnectionState.CONNECTING) return

        // 显示名称使用用户自定义的别名，如果没有则使用设备名称
        _deviceName.value = device.alias ?: device.name ?: device.address
        _connectionState.value = ConnectionState.CONNECTING

        scope.launch {
            try {
                delay(300)
                
                val adapter = BluetoothAdapter.getDefaultAdapter()
                // 移除 isDiscovering 检查，因为这需要 BLUETOOTH_SCAN 权限
                // if (adapter?.isDiscovering == true) {
                //     adapter.cancelDiscovery()
                // }
                
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket!!.connect()
                Log.d(TAG, "RFCOMM connected to ${device.name}")
                isConnected = true
                _connectionState.value = ConnectionState.CONNECTED

                startPacketReader(socket!!.inputStream)

                delay(300)
                queryStatus()

                if (autoGameMode) {
                    delay(100)
                    sendPacket(Enums.GAME_MODE_ON)
                    _gameMode.value = true
                    _lowLatency.value = true
                }
            } catch (e: IOException) {
                Log.e(TAG, "RFCOMM connect failed, trying insecure...", e)
                try {
                    socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                    socket!!.connect()
                    Log.d(TAG, "RFCOMM connected via insecure to ${device.name}")
                    isConnected = true
                    _connectionState.value = ConnectionState.CONNECTED

                    startPacketReader(socket!!.inputStream)
                    delay(300)
                    queryStatus()
                } catch (e2: IOException) {
                    Log.e(TAG, "RFCOMM connect failed completely", e2)
                    _connectionState.value = ConnectionState.ERROR
                    isConnected = false
                }
            }
        }

        batteryPollJob = scope.launch {
            delay(2000)
            while (isConnected) {
                delay(BATTERY_POLL_INTERVAL_MS)
                if (isConnected) queryStatus()
            }
        }
    }

    private fun startPacketReader(inputStream: InputStream) {
        scope.launch {
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
                        break
                    }
                }
            } catch (e: IOException) {
                if (isConnected) Log.e(TAG, "Read error", e)
            }
            if (isConnected) disconnect()
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
            handlePacket(packet)

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
    private fun handlePacket(packet: ByteArray) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Received: ${packet.toHexString(HexFormat.UpperCase)}")
        }

        val result = BatteryParser.parse(packet)
        if (result != null) {
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
            _batteryParams.value = BatteryParams(left, right, case)
            return
        }

        val ancResult = AncModeParser.parse(packet)
        if (ancResult != null) {
            Log.d(TAG, "ANC mode received: $ancResult")
            // 只有当抗风噪开启时才忽略 ANC 响应
            // 这样可以避免抗风噪开启时 ANC 查询返回 OFF 导致的闪烁
            // 同时确保抗风噪关闭时能正确同步 ANC 状态
            if (!_windSuppression.value) {
                _ancMode.value = ancResult
            }
            return
        }

        val gameModeResult = GameModeParser.parse(packet)
        if (gameModeResult != null) {
            Log.d(TAG, "Game mode received: $gameModeResult")
            _gameMode.value = gameModeResult
            return
        }

        val lowLatencyResult = LowLatencyParser.parse(packet)
        if (lowLatencyResult != null) {
            Log.d(TAG, "Low latency received: $lowLatencyResult")
            _lowLatency.value = lowLatencyResult
            return
        }

        val dualConnResult = DualConnParser.parse(packet)
        if (dualConnResult != null) {
            Log.d(TAG, "Dual conn received: $dualConnResult")
            _dualConn.value = dualConnResult
            return
        }

        val eqResult = EqParser.parse(packet)
        if (eqResult != null) {
            Log.d(TAG, "EQ received: $eqResult")
            _eq.value = eqResult
            return
        }

        val windSuppressionResult = WindSuppressionParser.parse(packet)
        if (windSuppressionResult != null) {
            Log.d(TAG, "Wind suppression received: $windSuppressionResult")
            val oldWindSuppression = _windSuppression.value
            _windSuppression.value = windSuppressionResult
            // 抗风噪也是ANC模式的一种，需要同步更新ANC状态
            if (windSuppressionResult) {
                _ancMode.value = NoiseControlMode.WIND_SUPPRESSION
            } else if (oldWindSuppression && _ancMode.value == NoiseControlMode.WIND_SUPPRESSION) {
                // 抗风噪关闭时，不要直接设置为OFF
                // 等待ANC查询的响应来更新实际状态
                // 这样可以确保从抗风噪切换到其他模式时状态正确同步
            }
            return
        }

        val inEarDetectionResult = InEarDetectionParser.parse(packet)
        if (inEarDetectionResult != null) {
            Log.d(TAG, "In-ear detection received: $inEarDetectionResult")
            _inEarDetection.value = inEarDetectionResult
            return
        }
    }

    private fun sendPacket(packet: ByteArray) {
        try {
            socket?.outputStream?.write(packet)
            socket?.outputStream?.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Send failed", e)
        }
    }

    fun setGameMode(enabled: Boolean) {
        _gameMode.value = enabled
        // 游戏模式开启时同步更新低延迟模式的状态
        _lowLatency.value = enabled
        val packet = if (enabled) Enums.GAME_MODE_ON else Enums.GAME_MODE_OFF
        scope.launch {
            sendPacket(packet)
        }
    }

    fun setANCMode(mode: NoiseControlMode) {
        // 如果用户点击的是当前已处于的降噪模式，忽略操作
        if (mode == _ancMode.value) {
            Log.d(TAG, "Current ANC mode is already $mode, skipping")
            return
        }
        val packet = when (mode) {
            NoiseControlMode.OFF -> Enums.ANC_OFF
            NoiseControlMode.TRANSPARENT -> Enums.ANC_TRANSPARENT
            NoiseControlMode.NORMAL -> Enums.ANC_NORMAL
            NoiseControlMode.DEEP -> Enums.ANC_DEEP
            NoiseControlMode.EXPERIMENT -> Enums.ANC_EXPERIMENT
            NoiseControlMode.WIND_SUPPRESSION -> Enums.ANC_WIND_SUPPRESSION
        }
        _ancMode.value = mode
        // 抗风噪模式需要同步更新windSuppression状态
        if (mode == NoiseControlMode.WIND_SUPPRESSION) {
            _windSuppression.value = true
        } else if (_windSuppression.value) {
            _windSuppression.value = false
        }
        scope.launch { sendPacket(packet) }
    }

    fun setLowLatency(enabled: Boolean) {
        _lowLatency.value = enabled
        val packet = if (enabled) Enums.LOW_LATENCY_ON else Enums.LOW_LATENCY_OFF
        scope.launch { sendPacket(packet) }
    }

    fun setDualConn(enabled: Boolean) {
        _dualConn.value = enabled
        val packet = if (enabled) Enums.DUAL_CONN_ON else Enums.DUAL_CONN_OFF
        scope.launch { sendPacket(packet) }
    }

    fun setEq(mode: EqMode) {
        // 如果用户选择的是当前已处于的EQ模式，忽略操作
        if (mode == _eq.value) {
            Log.d(TAG, "Current EQ mode is already $mode, skipping")
            return
        }
        _eq.value = mode
        val packet = OriGPackets.buildPacket(Op.EQ_SET, mode.value.toByte(), 0x00)
        scope.launch { sendPacket(packet) }
    }

    fun setWindSuppression(enabled: Boolean) {
        val oldEnabled = _windSuppression.value
        _windSuppression.value = enabled
        // 抗风噪也是ANC模式的一种，需要同步更新ANC状态
        if (enabled) {
            _ancMode.value = NoiseControlMode.WIND_SUPPRESSION
        } else if (oldEnabled && _ancMode.value == NoiseControlMode.WIND_SUPPRESSION) {
            // 抗风噪关闭时，不要直接设置为OFF
            // 等待查询状态时的ANC响应来更新实际状态
        }
        val packet = if (enabled) Enums.WIND_SUPPRESSION_ON else Enums.WIND_SUPPRESSION_OFF
        scope.launch { sendPacket(packet) }
    }

    fun setInEarDetection(enabled: Boolean) {
        _inEarDetection.value = enabled
        val packet = if (enabled) Enums.IN_EAR_DETECTION_ON else Enums.IN_EAR_DETECTION_OFF
        scope.launch { sendPacket(packet) }
    }

    private fun queryStatus() {
        scope.launch {
            sendPacket(Enums.QUERY_BATTERY)
            delay(50)
            sendPacket(OriGPackets.buildPacket(Op.WIND_SUPPRESSION_QUERY))
            delay(50)
            sendPacket(Enums.QUERY_ANC)
            delay(50)
            sendPacket(Enums.QUERY_GAME_MODE)
            delay(50)
            sendPacket(OriGPackets.buildPacket(Op.LOW_LATENCY_QUERY))
            delay(50)
            sendPacket(OriGPackets.buildPacket(Op.DUAL_CONN_QUERY))
            delay(50)
            sendPacket(OriGPackets.buildPacket(Op.EQ_QUERY))
            delay(50)
            sendPacket(Enums.QUERY_IN_EAR_DETECTION)
        }
    }

    fun refreshStatus() {
        if (!isConnected) return
        queryStatus()
    }

    fun disconnect() {
        isConnected = false
        batteryPollJob?.cancel()
        try { socket?.close() } catch (_: IOException) {}
        socket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _batteryParams.value = BatteryParams()
        _ancMode.value = NoiseControlMode.OFF
        _deviceName.value = ""
        _gameMode.value = false
        _lowLatency.value = false
        _dualConn.value = false
        _eq.value = EqMode.BALANCED
        _windSuppression.value = false
    }
}

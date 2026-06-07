package moe.chenxy.oppopods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import moe.chenxy.oppopods.BuildConfig
import moe.chenxy.oppopods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.oppopods.utils.miuiStrongToast.data.PodParams
import java.io.IOException
import java.io.InputStream

/**
 * Standalone RFCOMM controller for direct use from the app process.
 * Does not depend on the hook runtime.
 */
@SuppressLint("MissingPermission")
class AppRfcommController {
    companion object {
        private const val TAG = "OppoPods-AppRfcomm"
        private const val RFCOMM_CHANNEL = 15
        private const val BATTERY_POLL_INTERVAL_MS = 30_000L
    }

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    private var socket: BluetoothSocket? = null
    private var isConnected = false
    private var gameModeImplementation = GameModeImplementation.STANDARD
    private var lastGameModeStatusUpdateMs = 0L
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var batteryPollJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _batteryParams = MutableStateFlow(BatteryParams())
    val batteryParams: StateFlow<BatteryParams> = _batteryParams

    private val _ancMode = MutableStateFlow(NoiseControlMode.OFF)
    val ancMode: StateFlow<NoiseControlMode> = _ancMode

    private val _deviceName = MutableStateFlow("")
    val deviceName: StateFlow<String> = _deviceName

    private val _deviceAddress = MutableStateFlow("")
    val deviceAddress: StateFlow<String> = _deviceAddress

    private val _gameMode = MutableStateFlow(false)
    val gameMode: StateFlow<Boolean> = _gameMode

    private val _transparencyVocalEnhancement = MutableStateFlow(false)
    val transparencyVocalEnhancement: StateFlow<Boolean> = _transparencyVocalEnhancement

    @SuppressLint("DiscouragedPrivateApi")
    private fun createRfcommSocket(device: BluetoothDevice): BluetoothSocket {
        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        return method.invoke(device, RFCOMM_CHANNEL) as BluetoothSocket
    }

    fun connect(
        device: BluetoothDevice,
        autoGameMode: Boolean = false,
        gameModeImplementation: GameModeImplementation = GameModeImplementation.STANDARD
    ) {
        if (_connectionState.value == ConnectionState.CONNECTING) return

        this.gameModeImplementation = gameModeImplementation
        _deviceName.value = device.name ?: device.address
        _deviceAddress.value = device.address
        _connectionState.value = ConnectionState.CONNECTING

        scope.launch {
            try {
                delay(300)
                socket = createRfcommSocket(device)
                socket!!.connect()
                Log.d(TAG, "RFCOMM connected to ${device.name}")
                isConnected = true
                _connectionState.value = ConnectionState.CONNECTED

                startPacketReader(socket!!.inputStream)

                delay(300)
                queryStatus()

                if (autoGameMode) {
                    enableGameModeOnConnect()
                }
            } catch (e: IOException) {
                Log.e(TAG, "RFCOMM connect failed", e)
                _connectionState.value = ConnectionState.ERROR
                isConnected = false
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
            try {
                while (isConnected) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        handlePacket(buffer.copyOfRange(0, bytesRead))
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

    @OptIn(ExperimentalStdlibApi::class)
    private fun handlePacket(packet: ByteArray) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Received: ${packet.toHexString(HexFormat.UpperCase)}")
        }

        val result = BatteryParser.parse(packet)
        if (result != null) {
            val left = PodParams(
                result.left?.level ?: 0,
                result.left?.isCharging == true,
                result.left != null,
                0
            )
            val right = PodParams(
                result.right?.level ?: 0,
                result.right?.isCharging == true,
                result.right != null,
                0
            )
            val case = PodParams(
                result.case?.level ?: 0,
                result.case?.isCharging == true,
                result.case != null,
                0
            )
            _batteryParams.value = BatteryParams(left, right, case)
            return
        }

        // Try parse as active battery report (unsolicited, Cmd=0x0204, type=0x01)
        val activeResult = BatteryParser.parseActiveReport(packet)
        if (activeResult != null) {
            val left = PodParams(
                activeResult.left?.level ?: 0,
                activeResult.left?.isCharging == true,
                activeResult.left != null,
                0
            )
            val right = PodParams(
                activeResult.right?.level ?: 0,
                activeResult.right?.isCharging == true,
                activeResult.right != null,
                0
            )
            val case = PodParams(
                activeResult.case?.level ?: 0,
                activeResult.case?.isCharging == true,
                activeResult.case != null,
                0
            )
            _batteryParams.value = BatteryParams(left, right, case)
            return
        }

        val transparencyVocalEnhancementResult = TransparencyVocalEnhancementParser.parse(packet)
        if (transparencyVocalEnhancementResult != null) {
            Log.d(TAG, "Transparency vocal enhancement received: $transparencyVocalEnhancementResult")
            _transparencyVocalEnhancement.value = transparencyVocalEnhancementResult
            return
        }

        val ancResult = AncModeParser.parse(packet)
        if (ancResult != null) {
            Log.d(TAG, "ANC mode received: $ancResult")
            _ancMode.value = ancResult
            return
        }

        // Try parse as batch query response for game mode (Cmd=0x810D)
        val gameModeResult = GameModeParser.parse(packet, gameModeImplementation)
        if (gameModeResult != null) {
            Log.d(TAG, "Game mode received: $gameModeResult")
            lastGameModeStatusUpdateMs = SystemClock.elapsedRealtime()
            _gameMode.value = gameModeResult
            return
        }

        val setFeatureResult = SwitchFeatureSetParser.parse(packet)
        if (setFeatureResult != null) {
            Log.d(TAG, "Switch feature response: status=${setFeatureResult.status}, value=${setFeatureResult.value}")
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
        scope.launch { sendGameModePackets(enabled) }
    }

    fun setGameModeImplementation(implementation: GameModeImplementation) {
        gameModeImplementation = implementation
    }

    fun setTransparencyVocalEnhancement(enabled: Boolean) {
        _transparencyVocalEnhancement.value = enabled
        val packet = if (enabled) {
            Enums.TRANSPARENCY_VOCAL_ENHANCEMENT_ON
        } else {
            Enums.TRANSPARENCY_VOCAL_ENHANCEMENT_OFF
        }
        scope.launch { sendPacket(packet) }
    }

    fun setANCMode(mode: NoiseControlMode) {
        val packet = when (mode) {
            NoiseControlMode.OFF -> Enums.ANC_OFF
            NoiseControlMode.NOISE_CANCELLATION -> Enums.ANC_NOISE_CANCEL
            NoiseControlMode.NOISE_CANCELLATION_SMART -> Enums.ANC_NOISE_CANCEL_SMART
            NoiseControlMode.NOISE_CANCELLATION_LIGHT -> Enums.ANC_NOISE_CANCEL_LIGHT
            NoiseControlMode.NOISE_CANCELLATION_MEDIUM -> Enums.ANC_NOISE_CANCEL_MEDIUM
            NoiseControlMode.NOISE_CANCELLATION_DEEP -> Enums.ANC_NOISE_CANCEL_DEEP
            NoiseControlMode.ADAPTIVE -> Enums.ANC_ADAPTIVE
            NoiseControlMode.TRANSPARENCY -> Enums.ANC_TRANSPARENCY
        }
        _ancMode.value = mode
        scope.launch { sendPacket(packet) }
    }

    private suspend fun enableGameModeOnConnect() {
        delay(500)
        repeat(3) { attempt ->
            if (!isConnected) return

            val attemptStartedMs = SystemClock.elapsedRealtime()
            Log.d(TAG, "Auto game mode: enabling after connect, attempt=${attempt + 1}, implementation=$gameModeImplementation")
            _gameMode.value = true
            sendGameModePackets(true)

            delay(300)
            if (!isConnected) return
            sendPacket(Enums.QUERY_STATUS)

            delay(if (attempt == 0) 700 else 1_500)
            if (lastGameModeStatusUpdateMs >= attemptStartedMs && _gameMode.value) {
                return
            }
            Log.d(TAG, "Auto game mode: attempt ${attempt + 1} did not verify, retrying")
        }
    }

    private suspend fun sendGameModePackets(enabled: Boolean) {
        Enums.gameModePackets(enabled, gameModeImplementation).forEachIndexed { index, packet ->
            if (index > 0) delay(120)
            sendPacket(packet)
        }
    }

    /**
     * Combo query strategy: send batch query (wake + game mode), then battery, then ANC.
     */
    private fun queryStatus() {
        scope.launch {
            sendPacket(Enums.QUERY_STATUS)
            delay(50)
            sendPacket(Enums.QUERY_BATTERY)
            delay(50)
            sendPacket(Enums.QUERY_ANC)
        }
    }

    /**
     * Public method for UI refresh button.
     */
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
        _deviceAddress.value = ""
        _gameMode.value = false
        _transparencyVocalEnhancement.value = false
    }
}

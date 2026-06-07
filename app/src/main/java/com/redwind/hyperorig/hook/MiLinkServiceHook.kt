package com.redwind.hyperorig.hook

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.redwind.hyperorig.pods.RfcommController
import com.redwind.hyperorig.utils.miuiStrongToast.data.BatteryParams
import com.redwind.hyperorig.utils.miuiStrongToast.data.HyperOriGAction
import com.redwind.hyperorig.utils.miuiStrongToast.data.PodParams

@SuppressLint("MissingPermission")
object MiLinkServiceHook : HookContext() {
    private const val TAG = "HyperOriG-MiLink"
    private const val PREFS_NAME = "hyperorig_milink_state"
    private val knownAddresses = linkedSetOf<String>()
    private var context: Context? = null
    private var receiverRegistered = false
    private var currentAddress: String? = null
    private var currentName: String? = null
    private var currentBattery: BatteryParams = BatteryParams()
    private var currentAnc = 1

    override fun onHook() {
        hookContextEntry()
        hookMxBluetoothRuntime()
        hookHeadsetRuntimeDisplay()
        hookMoreSettingsButton()
    }

    private fun hookContextEntry() {
        listOf(
            "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService",
            "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager"
        ).forEach { className ->
            runCatching {
                hookBefore(findMethod(className, "getInstanceForIsMiTWS", Context::class.java)) {
                    registerStatusReceiver(args[0] as? Context)
                }
            }.onFailure { Log.w(TAG, "hook $className.getInstanceForIsMiTWS skipped", it) }
        }
    }

    private fun hookMxBluetoothRuntime() {
        val classes = listOf(
            "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager",
            "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService"
        )
        classes.forEach { className ->
            hookBluetoothDeviceResult(className, "checkIsMiTWS") { 1 }
            hookBluetoothDeviceResult(className, "getDeviceId") { fakeDeviceId() }
            hookBluetoothDeviceResult(className, "getBatteryLevel") { 1 }
            hookBluetoothDeviceResult(className, "getAncState") { miLinkAncState() }
            hookBluetoothDeviceResult(className, "getDeviceRunInfo") { 0 }
            hookBluetoothDeviceResult(className, "getSpatialMode") { 0 }
            hookBluetoothDeviceResult(className, "getWearStatus") { "0,0" }
            hookBluetoothDeviceResult(className, "isLeAudio") { false }
            hookAncCommand(className, "openAnc", 3, 1)      // HyperOriG mode 3 = NC
            hookAncCommand(className, "closeAnc", 1, 0)     // HyperOriG mode 1 = OFF
            hookAncCommand(className, "openTransparent", 2, 2) // HyperOriG mode 2 = Transparency
        }
        classes.forEach { className ->
            hookStringAddressResult(className, "isMiTWS") { true }
            hookStringAddressResult(className, "isSupportAudioSwitch") { 1 }
            hookStringAddressResult(className, "getRingFindState") { false }
        }
    }

    private fun hookHeadsetRuntimeDisplay() {
        hookBluetoothDeviceResult("com.miui.headset.runtime.ProfileContext", "getDeviceId") { fakeDeviceId() }
        hookBluetoothDeviceResult("com.miui.headset.runtime.ProfileContext", "getBatteryLevel") { miLinkBatteryLevels() }
        hookBluetoothDeviceResult("com.miui.headset.runtime.AncBatteryController", "getDeviceId") { fakeDeviceId() }
        hookBluetoothDeviceResult("com.miui.headset.runtime.AncBatteryController", "getAncState") { miLinkAncState() }
        hookBluetoothDeviceResult("com.miui.headset.runtime.AncBatteryController", "getBatteryLevelCache") { miLinkBatteryLevels() }
        hookBluetoothDeviceResult("com.miui.headset.runtime.AncBatteryController", "getHeadsetPropertyBlock") { batteryPercentForMiLink() }
        hookAncStateBlock()
        hookHeadsetInfoNoArg("getDeviceId") { fakeDeviceId() }
        hookHeadsetInfoNoArg("component3") { fakeDeviceId() }
        hookHeadsetInfoNoArg("getPowers") { miLinkBatteryLevels() }
        hookHeadsetInfoNoArg("component4") { miLinkBatteryLevels() }
        hookHeadsetInfoNoArg("getMode") { miLinkAncState() }
        hookHeadsetInfoNoArg("component5") { miLinkAncState() }
    }

    private fun hookMoreSettingsButton() {
        listOf(
            "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager",
            "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService"
        ).forEach { className ->
            runCatching {
                val method = findMethod(className, "switchToHeadsetActivity", BluetoothDevice::class.java)
                hookBefore(method) {
                    try {
                        val device = args[0] as? BluetoothDevice
                        Log.d(TAG, "switchToHeadsetActivity called device=${device?.address} pkg=$packageName")
                        if (device == null) return@hookBefore
                        if (!isOriGPod(device)) {
                            Log.d(TAG, "switchToHeadsetActivity not OriG, skip")
                            return@hookBefore
                        }
                        val ctx = instance?.let { obj ->
                            runCatching { getObjectField(obj, "mContext") as? Context }.getOrNull()
                        }
                        if (ctx == null) {
                            Log.w(TAG, "switchToHeadsetActivity context is null")
                            return@hookBefore
                        }
                        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage("com.redwind.hyperorig")?.apply {
                            putExtra("open_detail", true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                        this.result = null
                        Log.d(TAG, "switchToHeadsetActivity → HyperOriG address=${device.address}")
                    } catch (e: Exception) {
                        Log.e(TAG, "switchToHeadsetActivity hook error", e)
                    }
                }
                Log.d(TAG, "$className.switchToHeadsetActivity hook OK pkg=$packageName")
            }.onFailure { Log.w(TAG, "hook $className.switchToHeadsetActivity skipped pkg=$packageName", it) }
        }
    }

    private fun hookBluetoothDeviceResult(className: String, methodName: String, result: () -> Any) {
        runCatching {
            hookAfter(findMethod(className, methodName, BluetoothDevice::class.java)) {
                val device = args[0] as? BluetoothDevice ?: return@hookAfter
                if (!isOriGPod(device)) return@hookAfter
                val old = this.result
                this.result = result()
                if (className == "com.miui.headset.runtime.AncBatteryController" && methodName == "getHeadsetPropertyBlock") {
                    notifyHeadsetPropertyChanged(instance, device, 4)
                }
                Log.d(TAG, "$className.$methodName forced old=$old new=${this.result} address=${device.address}")
            }
        }.onFailure { Log.w(TAG, "hook $className.$methodName(BluetoothDevice) skipped", it) }
    }

    private fun hookStringAddressResult(className: String, methodName: String, result: () -> Any) {
        runCatching {
            hookAfter(findMethod(className, methodName, String::class.java)) {
                val address = args[0] as? String ?: return@hookAfter
                if (!isOriGAddress(address)) return@hookAfter
                val old = this.result
                this.result = result()
                Log.d(TAG, "$className.$methodName forced old=$old new=${this.result} address=$address")
            }
        }.onFailure { Log.w(TAG, "hook $className.$methodName(String) skipped", it) }
    }

    private fun hookAncCommand(className: String, methodName: String, oriGAnc: Int, result: Int) {
        runCatching {
            hookBefore(findMethod(className, methodName, BluetoothDevice::class.java)) {
                val device = args[0] as? BluetoothDevice ?: return@hookBefore
                if (!isOriGPod(device)) return@hookBefore
                currentAnc = oriGAnc
                sendOriGAnc(oriGAnc)
                this.result = result
                Log.d(TAG, "$className.$methodName handled address=${device.address} oriGAnc=$oriGAnc result=$result")
            }
        }.onFailure { Log.w(TAG, "hook $className.$methodName command skipped", it) }
    }

    private fun hookAncStateBlock() {
        runCatching {
            hookBefore(findMethod("com.miui.headset.runtime.AncBatteryController", "setAncStateBlock", BluetoothDevice::class.java, Int::class.javaPrimitiveType!!)) {
                val device = args[0] as? BluetoothDevice ?: return@hookBefore
                if (!isOriGPod(device)) return@hookBefore
                val miLinkMode = args[1] as? Int ?: return@hookBefore
                val oriGAnc = oriGAncFromMiLink(miLinkMode)
                val instanceContext = runCatching { getObjectField(instance, "context") as? Context }.getOrNull()
                if (instanceContext != null) {
                    context = instanceContext.applicationContext ?: instanceContext
                }
                currentAnc = oriGAnc
                sendOriGAnc(oriGAnc, instanceContext)
                sendMiLinkAncChanged(oriGAnc, instanceContext)
                notifyHeadsetPropertyChanged(instance, device, 8)
                notifyHeadsetPropertyChanged(instance, device, 4)
                this.result = miLinkAncState()
                Log.d(TAG, "AncBatteryController.setAncStateBlock handled address=${device.address} miLinkMode=$miLinkMode oriGAnc=$oriGAnc result=${this.result} context=${instanceContext ?: context}")
            }
        }.onFailure { Log.w(TAG, "hook AncBatteryController.setAncStateBlock skipped", it) }
    }

    private fun hookHeadsetInfoNoArg(methodName: String, result: () -> Any) {
        runCatching {
            hookAfter(findMethodByParamCount("com.miui.headset.api.HeadsetInfo", methodName, 0)) {
                if (!isTargetHeadsetInfo(instance)) return@hookAfter
                val old = this.result
                this.result = result()
                Log.d(TAG, "HeadsetInfo.$methodName forced old=$old new=${this.result}")
            }
        }.onFailure { Log.w(TAG, "hook HeadsetInfo.$methodName skipped", it) }
    }

    private fun registerStatusReceiver(ctx: Context?) {
        if (ctx == null || receiverRegistered) return
        context = ctx.applicationContext ?: ctx
        val filter = IntentFilter().apply {
            addAction(HyperOriGAction.ACTION_PODS_CONNECTED)
            addAction(HyperOriGAction.ACTION_PODS_DISCONNECTED)
            addAction(HyperOriGAction.ACTION_PODS_BATTERY_CHANGED)
            addAction(HyperOriGAction.ACTION_PODS_ANC_CHANGED)
            addAction(HyperOriGAction.ACTION_CONFIG_CHANGED)
        }
        context?.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    HyperOriGAction.ACTION_CONFIG_CHANGED -> {
                        refreshConfig()
                    }
                    HyperOriGAction.ACTION_PODS_CONNECTED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                        currentName = intent.getStringExtra("device_name") ?: currentName
                        currentAddress?.let { knownAddresses.add(it.uppercase()) }
                    }
                    HyperOriGAction.ACTION_PODS_DISCONNECTED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                    }
                    HyperOriGAction.ACTION_PODS_BATTERY_CHANGED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                        currentBattery = intent.batteryStatusFromExtras() ?: intent.parcelableStatus() ?: currentBattery
                        currentAddress?.let { knownAddresses.add(it.uppercase()) }
                        saveState(context)
                    }
                    HyperOriGAction.ACTION_PODS_ANC_CHANGED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                        currentAnc = intent.getIntExtra("status", currentAnc)
                        currentAddress?.let { knownAddresses.add(it.uppercase()) }
                        saveState(context)
                    }
                }
                Log.d(TAG, "state action=${intent?.action} address=$currentAddress name=$currentName anc=$currentAnc rawBattery=${currentBattery.debugString()} miLinkBattery=${miLinkBatteryLevels()}")
            }
        }, filter, Context.RECEIVER_EXPORTED)
        receiverRegistered = true
        context?.sendBroadcast(Intent(HyperOriGAction.ACTION_REFRESH_STATUS).apply {
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "registered status receiver context=$context")
    }

    private fun isOriGPod(device: BluetoothDevice): Boolean {
        val address = runCatching { device.address }.getOrNull()
        if (address != null && isOriGAddress(address)) return true
        val name = runCatching { device.name ?: device.alias }.getOrNull().orEmpty()
        val result = name.contains("YUANDAO", ignoreCase = true) ||
                     name.contains("OriG", ignoreCase = true) ||
                     name.contains("NiceHCK", ignoreCase = true)
        if (result && address != null) {
            knownAddresses.add(address.uppercase())
            currentAddress = address
            currentName = name
        }
        return result
    }

    private fun isOriGAddress(address: String): Boolean {
        val normalized = address.uppercase()
        return normalized == currentAddress?.uppercase() || normalized in knownAddresses
    }

    private fun isTargetHeadsetInfo(info: Any?): Boolean {
        if (info == null) return false
        listOf("getAddress", "component1").forEach { method ->
            val address = runCatching { callMethod(info, method) as? String }.getOrNull()
            if (address != null && isOriGAddress(address)) return true
        }
        return false
    }

    private fun miLinkAncState(): Int {
        loadState()
        return when (currentAnc) {
            3, 4, 5, 6 -> 1  // HyperOriG: 3=NC, 4=Deep, 5=Experiment, 6=WindSuppression → MiLink NC
            2 -> 2            // HyperOriG: 2=Transparency → MiLink Transparency
            else -> 0         // HyperOriG: 1=OFF → MiLink OFF
        }
    }

    private fun oriGAncFromMiLink(mode: Int): Int {
        return when (mode) {
            1 -> 3   // MiLink NC → HyperOriG mode 3 (NC)
            2 -> 2   // MiLink Transparency → HyperOriG mode 2 (Transparency)
            else -> 1 // MiLink OFF → HyperOriG mode 1 (OFF)
        }
    }

    private fun miLinkBatteryLevels(): List<Int> {
        loadState()
        syncBluetoothBackendState()
        val left = batteryValue(currentBattery.left)
        val right = batteryValue(currentBattery.right)
        val box = batteryValue(currentBattery.case)
        return listOf(
            box,
            left,
            right,
            chargingValue(currentBattery.case),
            chargingValue(currentBattery.left),
            chargingValue(currentBattery.right)
        )
    }

    private fun syncBluetoothBackendState() {
        val localSnapshot = runCatching { RfcommController.currentStatusSnapshot() }
            .getOrNull()
            ?.takeIf { it.address != null || it.battery != null }
        localSnapshot?.battery?.let { currentBattery = it }
        localSnapshot?.anc?.let { currentAnc = it }
        localSnapshot?.address?.let {
            currentAddress = it
            knownAddresses.add(it.uppercase())
        }
        localSnapshot?.deviceName?.let { currentName = it }
    }

    private fun batteryPercentForMiLink(): Int {
        loadState()
        val values = listOfNotNull(currentBattery.left, currentBattery.right)
            .filter { it.isConnected }
            .map { it.battery.coerceIn(0, 100) }
        return values.minOrNull() ?: 0
    }

    private fun batteryValue(params: PodParams?): Int {
        if (params?.isConnected != true) return -1
        return params.battery.coerceIn(0, 100)
    }

    private fun chargingValue(params: PodParams?): Int {
        return if (params?.isConnected == true && params.isCharging) 1 else 0
    }

    private fun sendOriGAnc(mode: Int, fallbackContext: Context? = null) {
        val ctx = fallbackContext ?: context ?: run {
            Log.w(TAG, "sendOriGAnc skipped: context is null mode=$mode")
            return
        }
        Intent(HyperOriGAction.ACTION_ANC_SELECT).apply {
            putExtra("status", mode)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            ctx.sendBroadcast(this)
        }
        Log.d(TAG, "sendOriGAnc broadcast sent mode=$mode")
    }

    private fun sendMiLinkAncChanged(mode: Int, fallbackContext: Context? = null) {
        val ctx = fallbackContext ?: context ?: return
        ctx.sendBroadcast(Intent(HyperOriGAction.ACTION_PODS_ANC_CHANGED).apply {
            putExtra("status", mode)
            setPackage("com.milink.service")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "sendMiLinkAncChanged broadcast sent mode=$mode")
    }

    private fun notifyHeadsetPropertyChanged(controller: Any?, device: BluetoothDevice, updateType: Int) {
        val listener = runCatching { getObjectField(controller, "headsetPropertyChangeListener") }.getOrNull()
        if (listener == null) {
            Log.w(TAG, "notifyHeadsetPropertyChanged skipped: listener is null updateType=$updateType")
            return
        }
        runCatching {
            callMethod(listener, "invoke", device, updateType)
            Log.d(TAG, "notifyHeadsetPropertyChanged invoked updateType=$updateType address=${device.address}")
        }.onFailure { Log.w(TAG, "notifyHeadsetPropertyChanged failed updateType=$updateType", it) }
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableStatus(): BatteryParams? {
        return runCatching { getParcelableExtra("status", BatteryParams::class.java) }.getOrNull()
            ?: runCatching { getParcelableExtra<BatteryParams>("status") }.getOrNull()
    }

    private fun Intent.batteryStatusFromExtras(): BatteryParams? {
        if (!hasExtra("left_connected") && !hasExtra("right_connected") && !hasExtra("case_connected")) return null
        return BatteryParams(
            left = PodParams(
                getIntExtra("left_battery", 0),
                getBooleanExtra("left_charging", false),
                getBooleanExtra("left_connected", false),
                0
            ),
            right = PodParams(
                getIntExtra("right_battery", 0),
                getBooleanExtra("right_charging", false),
                getBooleanExtra("right_connected", false),
                0
            ),
            case = PodParams(
                getIntExtra("case_battery", 0),
                getBooleanExtra("case_charging", false),
                getBooleanExtra("case_connected", false),
                0
            )
        )
    }

    private fun BatteryParams.debugString(): String {
        return "left=${left?.battery}/${left?.isCharging}/${left?.isConnected} right=${right?.battery}/${right?.isCharging}/${right?.isConnected} case=${case?.battery}/${case?.isCharging}/${case?.isConnected}"
    }

    private fun saveState(ctx: Context?) {
        val prefs = (ctx ?: context)?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        prefs.edit()
            .putString("address", currentAddress)
            .putString("name", currentName)
            .putInt("anc", currentAnc)
            .putInt("left_battery", currentBattery.left?.battery ?: 0)
            .putBoolean("left_charging", currentBattery.left?.isCharging == true)
            .putBoolean("left_connected", currentBattery.left?.isConnected == true)
            .putInt("right_battery", currentBattery.right?.battery ?: 0)
            .putBoolean("right_charging", currentBattery.right?.isCharging == true)
            .putBoolean("right_connected", currentBattery.right?.isConnected == true)
            .putInt("case_battery", currentBattery.case?.battery ?: 0)
            .putBoolean("case_charging", currentBattery.case?.isCharging == true)
            .putBoolean("case_connected", currentBattery.case?.isConnected == true)
            .apply()
    }

    private fun loadState() {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        currentAddress = prefs.getString("address", currentAddress)
        currentName = prefs.getString("name", currentName)
        currentAnc = prefs.getInt("anc", currentAnc)
        currentAddress?.let { knownAddresses.add(it.uppercase()) }
        currentBattery = BatteryParams(
            left = PodParams(
                prefs.getInt("left_battery", currentBattery.left?.battery ?: 0),
                prefs.getBoolean("left_charging", currentBattery.left?.isCharging == true),
                prefs.getBoolean("left_connected", currentBattery.left?.isConnected == true),
                0
            ),
            right = PodParams(
                prefs.getInt("right_battery", currentBattery.right?.battery ?: 0),
                prefs.getBoolean("right_charging", currentBattery.right?.isCharging == true),
                prefs.getBoolean("right_connected", currentBattery.right?.isConnected == true),
                0
            ),
            case = PodParams(
                prefs.getInt("case_battery", currentBattery.case?.battery ?: 0),
                prefs.getBoolean("case_charging", currentBattery.case?.isCharging == true),
                prefs.getBoolean("case_connected", currentBattery.case?.isConnected == true),
                0
            )
        )
    }
}

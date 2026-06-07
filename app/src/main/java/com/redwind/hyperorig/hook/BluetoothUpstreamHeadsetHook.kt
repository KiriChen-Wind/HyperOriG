package com.redwind.hyperorig.hook

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Bundle
import android.os.Parcel
import java.lang.reflect.Method
import com.redwind.hyperorig.config.ConfigManager
import com.redwind.hyperorig.pods.RfcommController
import com.redwind.hyperorig.utils.miuiStrongToast.data.BatteryParams
import com.redwind.hyperorig.utils.miuiStrongToast.data.HyperOriGAction
import com.redwind.hyperorig.utils.miuiStrongToast.data.PodParams
import org.json.JSONObject

@SuppressLint("MissingPermission")
object BluetoothUpstreamHeadsetHook : HookContext() {
    private const val TAG = "HyperOriG-Upstream"
    private const val DESCRIPTOR = "com.android.bluetooth.ble.app.IMiuiHeadsetService"
    private val knownAddresses = linkedSetOf<String>()
    private val callbacks = linkedMapOf<IBinder, Any>()
    private val handler = Handler(Looper.getMainLooper())
    private val hookedBinderClasses = linkedSetOf<String>()
    private var lastOriGDevice: BluetoothDevice? = null
    private var context: Context? = null
    private var receiverRegistered = false
    private var currentBattery: BatteryParams? = null
    private var currentAnc = 1
    private var currentTransparencyVocalEnhancement = false
    private var hasTransparencyVocalEnhancementState = false
    private var currentAddress: String? = null
    private var currentName: String? = null

    override fun onHook() {
        hookHeadsetServiceBinder()
        hookNotificationBatteryUpstream()
    }

    private fun hookNotificationBatteryUpstream() {
        val notificationApiClass = findClassOrNull("com.android.bluetooth.ble.app.MiuiBluetoothNotificationApi")
        if (notificationApiClass != null) {
            runCatching {
                hookBefore(
                    notificationApiClass.method(
                        "showNewConnectedToast",
                        Int::class.java,
                        Int::class.java,
                        Int::class.java,
                        Int::class.java,
                        BluetoothDevice::class.java,
                        String::class.java
                    )
                ) {
                    val device = args[4] as? BluetoothDevice
                    if (!isOriGPod(device)) return@hookBefore
                    val battery = effectiveBattery() ?: return@hookBefore
                    val leftBattery = displayBattery(battery.left) ?: (args[1] as? Int ?: 0)
                    val rightBattery = displayBattery(battery.right) ?: (args[2] as? Int ?: 0)
                    val wearState = displayWearState(battery, args[3] as? Int ?: 1)
                    val notification = currentMiuiBluetoothNotification() ?: return@hookBefore
                    result = null
                    callMethod(
                        notification,
                        "showConnectedToast",
                        args[0] as? Int ?: 2,
                        leftBattery,
                        rightBattery,
                        wearState,
                        device,
                        args[5] as? String
                    )
                    Log.d(TAG, "showNewConnectedToast patched device=${device.describe()} left=$leftBattery right=$rightBattery wear=$wearState oldLeft=${args[1]} oldRight=${args[2]} oldWear=${args[3]}")
                }
                Log.d(TAG, "MiuiBluetoothNotificationApi.showNewConnectedToast hook installed")
            }.onFailure { Log.w(TAG, "hook MiuiBluetoothNotificationApi.showNewConnectedToast skipped", it) }
        }

        val notificationClass = findClassOrNull("com.android.bluetooth.ble.app.MiuiBluetoothNotification")
        val requestClass = findClassOrNull("com.android.bluetooth.ble.app.C4705R2")
        if (notificationClass != null) {
            runCatching {
                hookBefore(notificationClass.method("invokeStatusBar", Context::class.java, String::class.java, Bundle::class.java)) {
                    val bundle = args[2] as? Bundle
                    if (shouldInterceptHeadsetWearIsland(bundle)) {
                        when (ConfigManager.islandMode()) {
                            ConfigManager.ISLAND_MODE_NONE, ConfigManager.ISLAND_MODE_MODULE -> {
                                result = null
                                Log.d(TAG, "invokeStatusBar swallowed headset_wear_notification island mode=${ConfigManager.islandMode()}")
                                return@hookBefore
                            }
                        }
                    }
                    patchHeadsetWearIslandBundle(bundle)
                    Log.d(TAG, "invokeStatusBar upstream action=${args[1]} bundle=$bundle focus=${bundle?.getString("miui.focus.param")}")
                }
                Log.d(TAG, "MiuiBluetoothNotification.invokeStatusBar debug hook installed")
            }.onFailure { Log.w(TAG, "hook MiuiBluetoothNotification.invokeStatusBar skipped", it) }
        }
        if (notificationClass != null && requestClass != null) {
            runCatching {
                hookAfter(notificationClass.method("updateParameters", requestClass)) {
                    val request = args[0] ?: return@hookAfter
                    val device = getObjectField(request, "f18110e") as? BluetoothDevice
                    if (!isOriGPod(device)) return@hookAfter
                    val battery = effectiveBattery() ?: return@hookAfter
                    val leftBattery = displayBattery(battery.left)
                    val rightBattery = displayBattery(battery.right)
                    val wearState = displayWearState(battery, getObjectField(request, "f18109d") as? Int ?: 1)
                    leftBattery?.let { setObjectField(request, "f18107b", it) }
                    rightBattery?.let { setObjectField(request, "f18108c", it) }
                    setObjectField(request, "f18109d", wearState)
                    Log.d(TAG, "updateParameters patched device=${device.describe()} left=$leftBattery right=$rightBattery wear=$wearState")
                }
                Log.d(TAG, "MiuiBluetoothNotification.updateParameters hook installed")
            }.onFailure { Log.w(TAG, "hook MiuiBluetoothNotification.updateParameters skipped", it) }
        }
    }

    private fun hookHeadsetServiceBinder() {
        val serviceClassName = "com.android.bluetooth.ble.app.headset.BluetoothHeadsetService"
        val serviceClass = findClassOrNull(serviceClassName)
        if (serviceClass != null) {
            runCatching {
                hookAfter(serviceClass.method("onBind", Intent::class.java)) {
                    registerStatusReceiver(instance as? Context)
                    val binder = result ?: return@hookAfter
                    installHeadsetBinderHooks(binder.javaClass)
                }
                Log.d(TAG, "BluetoothHeadsetService.onBind hook installed package=$packageName")
            }.onFailure { Log.w(TAG, "hook BluetoothHeadsetService.onBind failed package=$packageName", it) }
            runCatching {
                hookAfter(serviceClass.method("onCreate")) {
                    registerStatusReceiver(instance as? Context)
                }
                Log.d(TAG, "BluetoothHeadsetService.onCreate hook installed package=$packageName")
            }.onFailure { Log.d(TAG, "hook BluetoothHeadsetService.onCreate skipped package=$packageName: ${it.message}") }
        } else {
            Log.d(TAG, "BluetoothHeadsetService class not present package=$packageName")
        }

        listOf(
            "com.android.bluetooth.ble.app.headset.BinderC6776v",
            "com.android.bluetooth.ble.app.headset.v"
        ).forEach { className ->
            findClassOrNull(className)?.let { installHeadsetBinderHooks(it) }
        }
    }

    private fun findClassOrNull(className: String): Class<*>? {
        return runCatching { findClass(className) }.getOrNull()
    }

    private fun registerStatusReceiver(ctx: Context?) {
        if (ctx == null || receiverRegistered) return
        context = ctx.applicationContext ?: ctx
        val filter = IntentFilter().apply {
            addAction(HyperOriGAction.ACTION_PODS_CONNECTED)
            addAction(HyperOriGAction.ACTION_PODS_DISCONNECTED)
            addAction(HyperOriGAction.ACTION_PODS_BATTERY_CHANGED)
            addAction(HyperOriGAction.ACTION_PODS_ANC_CHANGED)
            addAction(HyperOriGAction.ACTION_PODS_TRANSPARENCY_VOCAL_ENHANCEMENT_CHANGED)
            addAction(HyperOriGAction.ACTION_CONFIG_CHANGED)
        }
        context?.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    HyperOriGAction.ACTION_CONFIG_CHANGED -> {
                        refreshConfig()
                        notifyRealStatus("config-changed")
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
                    }
                    HyperOriGAction.ACTION_PODS_ANC_CHANGED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                        currentAnc = intent.getIntExtra("status", currentAnc)
                        currentAddress?.let { knownAddresses.add(it.uppercase()) }
                    }
                    HyperOriGAction.ACTION_PODS_TRANSPARENCY_VOCAL_ENHANCEMENT_CHANGED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                        currentTransparencyVocalEnhancement = intent.getBooleanExtra("enabled", currentTransparencyVocalEnhancement)
                        hasTransparencyVocalEnhancementState = true
                        currentAddress?.let { knownAddresses.add(it.uppercase()) }
                    }
                }
                Log.d(TAG, "state action=${intent?.action} address=$currentAddress name=$currentName anc=$currentAnc battery=${currentBattery.debugString()}")
                notifyRealStatus("broadcast:${intent?.action}")
            }
        }, filter, Context.RECEIVER_EXPORTED)
        receiverRegistered = true
        context?.sendBroadcast(Intent(HyperOriGAction.ACTION_REFRESH_STATUS).apply {
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "registered status receiver context=$context")
    }

    private fun installHeadsetBinderHooks(binderClass: Class<*>) {
        val className = binderClass.name
        if (!hookedBinderClasses.add(className)) return
        Log.d(TAG, "BluetoothHeadsetService binder class=$className")

        runCatching {
            hookBefore(binderClass.method("checkSupport", BluetoothDevice::class.java)) {
                val device = args[0] as? BluetoothDevice
                if (!isOriGPod(device)) return@hookBefore
                lastOriGDevice = device
                result = fakeSupport()
                Log.d(TAG, "BinderC6776v.checkSupport forced device=${device.describe()} support=$result")
            }
            Log.d(TAG, "BinderC6776v.checkSupport hook installed")
        }.onFailure { Log.w(TAG, "hook BinderC6776v.checkSupport skipped", it) }

        hookAddressStringResult(binderClass, listOf("getDeviceInfo"), "getDeviceInfo") { fakeSupport() }
        hookAddressStringResult(binderClass, listOf("isSupportAudioSwitch", "mo19775z1", "z1"), "isSupportAudioSwitch") { "1" }
        hookAddressBooleanResult(binderClass, listOf("isMiTWS", "mo19771O0", "O0"), "isMiTWS", true)
        hookAddressBooleanResult(binderClass, listOf("checkIsMiTWS", "mo19766B", "B"), "checkIsMiTWS", true)
        hookAddressBooleanResult(binderClass, listOf("getRingFindState", "mo19772m0", "m0"), "getRingFindState", false)

        runCatching {
            hookBefore(binderClass.method("setCommonCommand", Int::class.java, String::class.java, BluetoothDevice::class.java)) {
                val command = args[0] as? Int
                val value = args[1] as? String
                val device = args[2] as? BluetoothDevice
                if (!isOriGPod(device)) return@hookBefore
                lastOriGDevice = device
                result = when (command) {
                    102 -> "1"
                    123 -> "4"
                    else -> "1"
                }
                Log.d(TAG, "BinderC6776v.setCommonCommand forced command=$command value=$value device=${device.describe()} result=$result")
                sendRealStatus(device, "setCommonCommand:$command")
            }
            Log.d(TAG, "BinderC6776v.setCommonCommand hook installed")
        }.onFailure { Log.w(TAG, "hook BinderC6776v.setCommonCommand skipped", it) }

        hookBinderVoidDevice(binderClass, "connect") { device, method -> sendRealStatus(device, method) }
        hookBinderVoidDevice(binderClass, "getDeviceConfig") { device, method -> sendRealStatus(device, method) }
        hookBinderVoidDeviceString(binderClass, "getCommonConfig") { device, method -> sendRealStatus(device, method) }
        hookBinderAncMode(binderClass)
        hookBinderAncLevel(binderClass)

        runCatching {
            val callbackClass = findClass("com.android.bluetooth.ble.app.IMiuiHeadsetCallback")
            hookBefore(binderClass.method("register", callbackClass)) {
                val callback = args[0]
                if (callback != null && lastOriGDevice != null) {
                    rememberCallback(callback)
                    result = null
                    Log.d(TAG, "BinderC6776v.register swallowed callback=$callback device=${lastOriGDevice.describe()}")
                    requestBluetoothStatus("register")
                    sendRealStatus(lastOriGDevice, "register")
                    sendRealStatusDelayed(lastOriGDevice, "register-refresh", 350L)
                }
            }
            hookBefore(binderClass.method("registerCallbackDevice", callbackClass, BluetoothDevice::class.java)) {
                val callback = args[0]
                val device = args[1] as? BluetoothDevice
                if (!isOriGPod(device) || callback == null) return@hookBefore
                lastOriGDevice = device
                rememberCallback(callback)
                result = null
                Log.d(TAG, "BinderC6776v.registerCallbackDevice swallowed callback=$callback device=${device.describe()}")
                requestBluetoothStatus("registerCallbackDevice")
                sendRealStatus(device, "registerCallbackDevice")
                sendRealStatusDelayed(device, "registerCallbackDevice-refresh", 350L)
            }
            hookBefore(binderClass.method("unregister", callbackClass, BluetoothDevice::class.java)) {
                val callback = args[0]
                val device = args[1] as? BluetoothDevice
                if (!isOriGPod(device) || callback == null) return@hookBefore
                forgetCallback(callback)
                result = null
                Log.d(TAG, "BinderC6776v.unregister swallowed callback=$callback device=${device.describe()}")
            }
            Log.d(TAG, "BinderC6776v callback hooks installed")
        }.onFailure { Log.w(TAG, "hook BinderC6776v callback methods skipped", it) }
    }

    private fun hookBinderVoidDevice(binderClass: Class<*>, methodName: String, after: (BluetoothDevice?, String) -> Unit) {
        runCatching {
            hookBefore(binderClass.method(methodName, BluetoothDevice::class.java)) {
                val device = args[0] as? BluetoothDevice
                if (!isOriGPod(device)) return@hookBefore
                lastOriGDevice = device
                result = null
                Log.d(TAG, "BinderC6776v.$methodName swallowed device=${device.describe()}")
                requestBluetoothStatus(methodName)
                after(device, methodName)
                sendRealStatusDelayed(device, "$methodName-refresh", 350L)
            }
        }.onFailure { Log.w(TAG, "hook BinderC6776v.$methodName skipped", it) }
    }

    private fun hookAddressStringResult(binderClass: Class<*>, methodNames: List<String>, label: String, forced: () -> String) {
        val methodName = methodNames.firstOrNull { name ->
            runCatching { binderClass.method(name, String::class.java) }.isSuccess
        } ?: run {
            Log.w(TAG, "hook BinderC6776v.$label skipped: no method in $methodNames")
            return
        }
        runCatching {
            hookBefore(binderClass.method(methodName, String::class.java)) {
                val address = args[0] as? String
                if (address == null || !isOriGAddress(address)) return@hookBefore
                result = forced()
                Log.d(TAG, "BinderC6776v.$label forced address=$address result=$result method=$methodName")
            }
            Log.d(TAG, "BinderC6776v.$label hook installed method=$methodName")
        }.onFailure { Log.w(TAG, "hook BinderC6776v.$label skipped", it) }
    }

    private fun hookAddressBooleanResult(binderClass: Class<*>, methodNames: List<String>, label: String, forced: Boolean) {
        val methodName = methodNames.firstOrNull { name ->
            runCatching { binderClass.method(name, String::class.java) }.isSuccess
        } ?: run {
            Log.w(TAG, "hook BinderC6776v.$label skipped: no method in $methodNames")
            return
        }
        runCatching {
            hookBefore(binderClass.method(methodName, String::class.java)) {
                val address = args[0] as? String
                if (address == null || !isOriGAddress(address)) return@hookBefore
                result = forced
                Log.d(TAG, "BinderC6776v.$label forced address=$address result=$forced method=$methodName")
            }
            Log.d(TAG, "BinderC6776v.$label hook installed method=$methodName")
        }.onFailure { Log.w(TAG, "hook BinderC6776v.$label skipped", it) }
    }

    private fun hookBinderVoidDeviceString(binderClass: Class<*>, methodName: String, after: (BluetoothDevice?, String) -> Unit) {
        runCatching {
            hookBefore(binderClass.method(methodName, BluetoothDevice::class.java, String::class.java)) {
                val device = args[0] as? BluetoothDevice
                val value = args[1] as? String
                if (!isOriGPod(device)) return@hookBefore
                lastOriGDevice = device
                result = null
                Log.d(TAG, "BinderC6776v.$methodName swallowed value=$value device=${device.describe()}")
                requestBluetoothStatus("$methodName:$value")
                after(device, "$methodName:$value")
                sendRealStatusDelayed(device, "$methodName-refresh:$value", 350L)
            }
        }.onFailure { Log.w(TAG, "hook BinderC6776v.$methodName skipped", it) }
    }

    private fun hookBinderAncMode(binderClass: Class<*>) {
        runCatching {
            hookBefore(binderClass.method("changeAncMode", Int::class.java, BluetoothDevice::class.java)) {
                val mode = args[0] as? Int
                val device = args[1] as? BluetoothDevice
                if (!isOriGPod(device)) return@hookBefore
                lastOriGDevice = device
                result = null
                Log.d(TAG, "BinderC6776v.changeAncMode swallowed mode=$mode device=${device.describe()}")
                mode?.let { sendOriGAnc(oriGAncFromMiuiMode(it)) }
                sendRealStatus(device, "changeAncMode:$mode")
            }
        }.onFailure { Log.w(TAG, "hook BinderC6776v.changeAncMode skipped", it) }
    }

    private fun hookBinderAncLevel(binderClass: Class<*>) {
        runCatching {
            hookBefore(binderClass.method("changeAncLevel", String::class.java, BluetoothDevice::class.java)) {
                val level = args[0] as? String
                val device = args[1] as? BluetoothDevice
                if (!isOriGPod(device)) return@hookBefore
                lastOriGDevice = device
                result = null
                Log.d(TAG, "BinderC6776v.changeAncLevel swallowed level=$level device=${device.describe()}")
                level?.let { sendOriGAncLevel(it) }
                sendRealStatus(device, "changeAncLevel:$level")
            }
        }.onFailure { Log.w(TAG, "hook BinderC6776v.changeAncLevel skipped", it) }
    }

    private fun rememberCallback(callback: Any) {
        (callMethod(callback, "asBinder") as? IBinder)?.let { callbacks[it] = callback }
    }

    private fun forgetCallback(callback: Any) {
        (callMethod(callback, "asBinder") as? IBinder)?.let { callbacks.remove(it) }
    }

    private fun Class<*>.method(name: String, vararg parameterTypes: Class<*>): Method {
        return getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
    }

    private fun isOriGPod(device: BluetoothDevice?): Boolean {
        if (device == null) return false
        val address = runCatching { device.address }.getOrNull()
        val name = runCatching { device.name ?: device.alias }.getOrNull().orEmpty()
        val result = name.contains("YUANDAO", ignoreCase = true) ||
                     name.contains("OriG", ignoreCase = true) ||
                     name.contains("NiceHCK", ignoreCase = true) ||
                     (address != null && isOriGAddress(address))
        if (result && address != null) knownAddresses.add(address.uppercase())
        return result
    }

    private fun notifyRealStatus(reason: String) {
        val device = lastOriGDevice
        if (device != null) {
            sendRealStatus(device, reason)
            return
        }
        val address = currentAddress ?: return
        sendRealStatus(address, reason)
    }

    private fun sendRealStatus(device: BluetoothDevice?, reason: String) {
        val address = device?.address ?: return
        sendRealStatus(address, reason)
    }

    private fun sendRealStatusDelayed(device: BluetoothDevice?, reason: String, delayMs: Long) {
        val address = device?.address ?: return
        handler.postDelayed({ sendRealStatus(address, reason) }, delayMs)
    }

    private fun sendRealStatus(address: String, reason: String) {
        if (callbacks.isEmpty()) {
            Log.d(TAG, "send real status skipped: no callback reason=$reason address=$address")
            return
        }
        val payload = realRefreshPayload()
        handler.post {
            callbacks.values.toList().forEach { callback ->
                runCatching {
                    callMethod(callback, "refreshStatus", address, payload)
                    Log.d(TAG, "sent real refreshStatus reason=$reason address=$address payload=$payload callback=$callback")
                }.onFailure {
                    forgetCallback(callback)
                    Log.w(TAG, "send real refreshStatus failed reason=$reason callback=$callback", it)
                }
            }
        }
    }

    private fun realRefreshPayload(): String {
        val localSnapshot = runCatching { RfcommController.currentStatusSnapshot() }
            .getOrNull()
            ?.takeIf { it.address != null || it.battery != null }
        val battery = localSnapshot?.battery ?: currentBattery
        val anc = currentAnc
        if (!hasTransparencyVocalEnhancementState && localSnapshot != null) {
            currentTransparencyVocalEnhancement = localSnapshot.transparencyVocalEnhancement
            hasTransparencyVocalEnhancementState = true
        }
        val transparencyVocalEnhancement = if (hasTransparencyVocalEnhancementState) {
            currentTransparencyVocalEnhancement
        } else {
            localSnapshot?.transparencyVocalEnhancement ?: currentTransparencyVocalEnhancement
        }
        localSnapshot?.address?.let {
            currentAddress = it
            knownAddresses.add(it.uppercase())
        }
        localSnapshot?.deviceName?.let { currentName = it }
        return RfcommController.miuiRefreshPayload(battery, anc, transparencyVocalEnhancement)
    }

    private fun effectiveBattery(): BatteryParams? {
        return runCatching { RfcommController.currentStatusSnapshot().battery }.getOrNull() ?: currentBattery
    }

    private fun displayBattery(params: PodParams?): Int? {
        if (params?.isConnected != true) return null
        return params.battery.coerceIn(0, 100)
    }

    private fun displayWearState(battery: BatteryParams, fallback: Int): Int {
        val leftConnected = battery.left?.isConnected == true
        val rightConnected = battery.right?.isConnected == true
        return when {
            leftConnected && rightConnected -> 1
            leftConnected -> 3
            rightConnected -> 2
            fallback != 0 -> fallback
            else -> 1
        }
    }

    private fun currentMiuiBluetoothNotification(): Any? {
        return runCatching {
            findClass("com.android.bluetooth.ble.app.headset.BluetoothHeadsetService")
                .getField("mMiuiBluetoothNotification")
                .apply { isAccessible = true }
                .get(null)
        }.getOrNull()
    }

    private fun patchHeadsetWearIslandBundle(bundle: Bundle?) {
        if (bundle == null) return
        if (!shouldInterceptHeadsetWearIsland(bundle)) return
        if (ConfigManager.islandMode() != ConfigManager.ISLAND_MODE_OFFICIAL) return
        val battery = effectiveBattery() ?: return
        val leftText = displayBattery(battery.left)?.let { "$it%" }
        val rightText = displayBattery(battery.right)?.let { "$it%" }
        if (leftText == null && rightText == null) return
        patchIslandJson(bundle, "param", leftText, rightText)
        patchIslandJson(bundle, "island_param", leftText, rightText)
        Log.d(TAG, "patched headset_wear_notification island text left=$leftText right=$rightText")
    }

    private fun shouldInterceptHeadsetWearIsland(bundle: Bundle?): Boolean {
        return bundle?.getString("notifyId") == "headset_wear_notification"
    }

    private fun patchIslandJson(bundle: Bundle, key: String, leftText: String?, rightText: String?) {
        val raw = bundle.getString(key) ?: return
        runCatching {
            val json = JSONObject(raw)
            leftText?.let { putTextParams(json.optJSONObject("left"), it) }
            rightText?.let { putTextParams(json.optJSONObject("right"), it) }
            bundle.putString(key, json.toString())
        }.onFailure {
            Log.w(TAG, "patch island json failed key=$key raw=$raw", it)
        }
    }

    private fun putTextParams(area: JSONObject?, text: String) {
        if (area == null) return
        area.put(
            "textParams",
            JSONObject().apply {
                put("text", text)
                put("textColor", -1)
                put("turnAnim", true)
            }
        )
    }

    private fun requestBluetoothStatus(reason: String) {
        runCatching {
            if (packageName == "com.android.bluetooth") {
                RfcommController.queryStatus()
            } else {
                context?.sendBroadcast(Intent(HyperOriGAction.ACTION_REFRESH_STATUS).apply {
                    setPackage("com.android.bluetooth")
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                })
            }
            Log.d(TAG, "requested bluetooth status reason=$reason package=$packageName")
        }.onFailure {
            Log.w(TAG, "request bluetooth status failed reason=$reason package=$packageName", it)
        }
    }

    private fun oriGAncFromMiuiMode(mode: Int): Int {
        return when (mode) {
            1 -> 3   // MIUI NC → HyperOriG mode 3 (NC)
            2 -> 2   // MIUI Transparency → HyperOriG mode 2 (Transparency)
            else -> 1
        }
    }

    private fun oriGAncFromMiuiLevel(level: String): Int {
        // MIUI level codes: 0103=Smart, 0101=Light, 0100=Medium, 0102=Deep, 02xx=Transparency.
        return when {
            level.startsWith("0103") -> 3  // Smart NC → HyperOriG NC
            level.startsWith("0101") -> 3  // Light NC → HyperOriG NC
            level.startsWith("0100") -> 3  // Medium NC → HyperOriG NC
            level.startsWith("0102") -> 4  // Deep NC → HyperOriG Deep
            level.startsWith("01") -> 3    // Other NC → HyperOriG NC
            level.startsWith("02") -> 2    // Transparency → HyperOriG Transparency
            else -> 1
        }
    }

    private fun sendOriGAncLevel(level: String) {
        when {
            level.startsWith("0201") -> {
                currentAnc = 2
                sendOriGTransparencyVocalEnhancement(true)
            }
            level.startsWith("0200") -> {
                currentAnc = 2
                sendOriGTransparencyVocalEnhancement(false)
            }
            else -> sendOriGAnc(oriGAncFromMiuiLevel(level))
        }
    }

    private fun sendOriGAnc(mode: Int) {
        currentAnc = mode
        val ctx = context ?: run {
            Log.w(TAG, "sendOriGAnc skipped: context is null mode=$mode")
            return
        }
        ctx.sendBroadcast(Intent(HyperOriGAction.ACTION_ANC_SELECT).apply {
            putExtra("status", mode)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        ctx.sendBroadcast(Intent(HyperOriGAction.ACTION_PODS_ANC_CHANGED).apply {
            putExtra("status", mode)
            setPackage(ctx.packageName)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "sendOriGAnc broadcast sent mode=$mode")
    }

    private fun sendOriGTransparencyVocalEnhancement(enabled: Boolean) {
        currentTransparencyVocalEnhancement = enabled
        hasTransparencyVocalEnhancementState = true
        val ctx = context ?: run {
            Log.w(TAG, "sendOriGTransparencyVocalEnhancement skipped: context is null enabled=$enabled")
            return
        }
        ctx.sendBroadcast(Intent(HyperOriGAction.ACTION_TRANSPARENCY_VOCAL_ENHANCEMENT_SET).apply {
            putExtra("enabled", enabled)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "sendOriGTransparencyVocalEnhancement broadcast sent enabled=$enabled")
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

    private fun BatteryParams?.debugString(): String {
        if (this == null) return "null"
        return "left=${left?.battery}/${left?.isCharging}/${left?.isConnected} right=${right?.battery}/${right?.isCharging}/${right?.isConnected} case=${case?.battery}/${case?.isCharging}/${case?.isConnected}"
    }

    private fun isOriGAddress(address: String): Boolean {
        return address.uppercase() in knownAddresses
    }

    private fun BluetoothDevice?.describe(): String {
        if (this == null) return "null"
        val address = runCatching { this.address }.getOrNull()
        val name = runCatching { this.name }.getOrNull()
        val alias = runCatching { this.alias }.getOrNull()
        return "BluetoothDevice(address=$address,name=$name,alias=$alias)"
    }
}

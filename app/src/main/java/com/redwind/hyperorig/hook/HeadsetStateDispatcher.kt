package com.redwind.hyperorig.hook

import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.ContextWrapper
import android.os.Handler
import com.redwind.hyperorig.pods.RfcommController
import com.redwind.hyperorig.utils.SystemApisUtils.setIconVisibility

object HeadsetStateDispatcher : HookContext() {

    override fun onHook() {
        hookAfter(findMethodByParamCount("com.android.bluetooth.a2dp.A2dpService", "handleConnectionStateChanged", 3)) {
            val currState = args[2] as Int
            val fromState = args[1] as Int
            val device = args[0] as BluetoothDevice?
            val handler = getObjectField(instance, "mHandler") as Handler
            if (device == null || currState == fromState) {
                return@hookAfter
            }
            handler.post {
                Log.d("HyperOriG", "A2DP Connection State: $currState, isOriG ${isOriGPod(device)}")
                val context = instance as ContextWrapper
                if (!isOriGPod(device)) return@post

                val statusBarManager = context.getSystemService("statusbar") as StatusBarManager
                if (currState == BluetoothHeadset.STATE_CONNECTED) {
                    statusBarManager.setIconVisibility("wireless_headset", true)
                    RfcommController.connectPod(context, device, prefs)
                } else if (currState == BluetoothHeadset.STATE_DISCONNECTING || currState == BluetoothHeadset.STATE_DISCONNECTED) {
                    statusBarManager.setIconVisibility("wireless_headset", false)
                    RfcommController.disconnectedPod(context, device)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun isOriGPod(device: BluetoothDevice): Boolean {
        val name = device.name ?: return false
        return name.contains("YUANDAO", ignoreCase = true) ||
               name.contains("OriG", ignoreCase = true) ||
               name.contains("NiceHCK", ignoreCase = true)
    }
}

package com.redwind.hyperorig.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import com.redwind.hyperorig.utils.miuiStrongToast.data.HyperOriGAction

object DeviceCardHook : HookContext() {
    @Volatile
    var mac = ""

    override fun onHook() {
        val handlerThread = HandlerThread("broadcast_handler_thread").also {
            it.start()
        }
        val handler = Handler(handlerThread.looper)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                if (p1?.action == HyperOriGAction.ACTION_PODS_MAC_RECEIVED) {
                    mac = p1.getStringExtra("mac")!!
                    Log.i("HyperOriG", "received mac $mac")
                }
            }
        }
        var isRegistered = false

        var panelController: Any? = null
        hookAfter(findMethod("miui.systemui.controlcenter.panel.main.MainPanelController", "onCreate")) {
            panelController = instance
        }

        fun hidePanel() {
            panelController?.let {
                callMethod(panelController, "exitOrHide")
            }
        }

        hookBefore(findMethod("miui.systemui.devicecenter.devices.DeviceInfoWrapper", "performClicked", Context::class.java)) {
            val context = args[0] as Context
            val deviceInfo = callMethod(instance, "getDeviceInfo")
            val id = callMethod(deviceInfo, "getId") as String
            val deviceType = callMethod(deviceInfo, "getDeviceType") as String
            if (deviceType != "third_headset") {
                return@hookBefore
            }

            if (!isRegistered) {
                context.registerReceiver(
                    receiver,
                    IntentFilter(HyperOriGAction.ACTION_PODS_MAC_RECEIVED),
                    null,
                    handler,
                    Context.RECEIVER_EXPORTED,
                )
                isRegistered = true
            }

            Intent(HyperOriGAction.ACTION_GET_PODS_MAC).apply {
                this.`package` = "com.android.bluetooth"
                this.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                context.sendBroadcast(this)
            }

            var waitCnt = 10
            while (mac.isEmpty() && waitCnt-- > 0) {
                Thread.sleep(50)
            }

            if (mac == id) {
                Intent("com.redwind.hyperorig.action.show_pods_ui").apply {
                    setClassName("com.redwind.hyperorig", "com.redwind.hyperorig.PopupActivity")
                    this.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(this)
                }
                mac = ""
                hidePanel()
                result = null
            }

            Log.i("HyperOriG", "performClicked $id mac $mac")
        }
    }
}

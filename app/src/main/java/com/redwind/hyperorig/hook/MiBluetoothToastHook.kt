package com.redwind.hyperorig.hook

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Bundle
import com.xzakota.hyper.notification.focus.FocusNotification
import com.redwind.hyperorig.utils.FocusIslandUtil
import com.redwind.hyperorig.utils.SystemApisUtils
import com.redwind.hyperorig.utils.SystemApisUtils.cancelAsUser
import com.redwind.hyperorig.utils.SystemApisUtils.notifyAsUser
import com.redwind.hyperorig.config.ConfigManager
import com.redwind.hyperorig.utils.miuiStrongToast.data.BatteryParams
import com.redwind.hyperorig.utils.miuiStrongToast.data.HyperOriGAction
import com.redwind.hyperorig.R

@SuppressLint("MissingPermission")
object MiBluetoothToastHook : HookContext() {

    // ANC 模式本地缓存，用于循环切换和状态同步
    private var localAncMode = 1
    private var lastConnectedDevice: BluetoothDevice? = null
    private var lastBatteryParams: BatteryParams? = null

    override fun onHook() {

        fun deleteIntent(context: Context, bluetoothDevice: BluetoothDevice): PendingIntent? {
            val intent = Intent("com.android.bluetooth.headset.notification.cancle")
            intent.putExtra("android.bluetooth.device.extra.DEVICE", bluetoothDevice)
            return PendingIntent.getBroadcast(context, 0, intent, 201326592)
        }

        @SuppressLint("WrongConstant")
        fun createPodsNotification(bluetoothDevice: BluetoothDevice?, context: Context, batteryParams: BatteryParams) {
            val miheadset_notification_Box = context.resources.getIdentifier("miheadset_notification_Box", "string", "com.xiaomi.bluetooth")
            val miheadset_notification_LeftEar = context.resources.getIdentifier("miheadset_notification_LeftEar", "string", "com.xiaomi.bluetooth")
            val miheadset_notification_RightEar = context.resources.getIdentifier("miheadset_notification_RightEar", "string", "com.xiaomi.bluetooth")
            val miheadset_notification_Disconnect = context.resources.getIdentifier("miheadset_notification_Disconnect", "string", "com.xiaomi.bluetooth")
            val system_notification_accent_color = context.resources.getIdentifier("system_notification_accent_color", "color", "android")
            if (bluetoothDevice == null) {
                Log.e("HyperOriG", "createPodsNotification: btDevice null")
                return
            }
            try {
                val address: String = bluetoothDevice.address
                var alias: String? = bluetoothDevice.alias
                if (alias?.isEmpty() == true) {
                    alias = bluetoothDevice.name
                }

                val caseBattStr = if (batteryParams.case != null && batteryParams.case!!.isConnected)
                    " ${context.resources.getString(miheadset_notification_Box)}${batteryParams.case!!.battery}%" +
                            "${if (batteryParams.case!!.isCharging) "⚡" else ""}"
                else ""
                val leftEar = if (batteryParams.left != null && batteryParams.left!!.isConnected)
                    "${context.resources.getString(miheadset_notification_LeftEar)}${batteryParams.left!!.battery}%" +
                        (if (batteryParams.left!!.isCharging) "⚡" else "")
                else ""
                val leftToRight = if (batteryParams.left?.isConnected == true && batteryParams.right?.isConnected == true) " " else ""
                val rightEar = if (batteryParams.right != null && batteryParams.right!!.isConnected)
                    "$leftToRight${context.resources.getString(miheadset_notification_RightEar)}${batteryParams.right!!.battery}%" +
                        (if (batteryParams.right!!.isCharging) "⚡ " else " ")
                else ""

                val contentText: String = leftEar + rightEar + caseBattStr
                val notificationManager = context.getSystemService("notification") as NotificationManager
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        "BTHeadset$address",
                        alias,
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        setSound(null, null)
                        setAllowBubbles(true)
                    }
                )
                val bundle = Bundle()
                bundle.putParcelable("Device", bluetoothDevice)
                val intent = Intent("com.android.bluetooth.headset.notification")
                intent.putExtra("btData", bundle)
                intent.putExtra("disconnect", "1")
                intent.setIdentifier("BTHeadset$address")
                val disconnectAction = Notification.Action(
                    285737079,
                    context.resources.getString(miheadset_notification_Disconnect),
                    PendingIntent.getBroadcast(context, 0, intent, 201326592)
                )
                // 循环切换降噪模式
                val ancCycleIntent = Intent(HyperOriGAction.ACTION_CYCLE_ANC)
                ancCycleIntent.setIdentifier("BTHeadset$address")
                val moduleContext = context.createPackageContext(
                    "com.redwind.hyperorig", Context.CONTEXT_IGNORE_SECURITY
                )
                val headsetIcon = Icon.createWithBitmap(
                    BitmapFactory.decodeResource(moduleContext.resources, R.drawable.img_box_mini
                    )
                )
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent("com.redwind.hyperorig.action.show_pods_ui").apply {
                        setClassName("com.redwind.hyperorig", "com.redwind.hyperorig.PopupActivity")
                        putExtra("android.bluetooth.device.extra.DEVICE", bluetoothDevice)
                        putExtra("bluetoothaddress", bluetoothDevice.address)
                        putExtra("device_name", alias)
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val focusExtras = FocusNotification.buildV3 {
                    val logo = createPicture("key_headset", headsetIcon)
                    enableFloat = true
                    ticker = alias ?: ""
                    updatable = true

                    iconTextInfo {
                        animIconInfo{
                            type = 0
                            src = logo
                        }
                        title = alias ?: ""
                        content = contentText
                    }

                    island {
                        islandProperty = 1
                        bigIslandArea {
                            imageTextInfoLeft {
                                type = 1
                                picInfo {
                                    type = 1
                                    pic = logo
                                }
                            }
                            imageTextInfoRight {
                                type = 2
                                textInfo {
                                    title = alias ?: ""
                                    content = contentText
                                }
                            }
                        }
                    }

                    textButton {
                        addActionInfo {
                            val ancLabel = when (localAncMode) {
                                3 -> moduleContext.getString(R.string.anc_notif_nc)
                                4 -> moduleContext.getString(R.string.anc_notif_deep)
                                5 -> moduleContext.getString(R.string.anc_notif_experiment)
                                2 -> moduleContext.getString(R.string.anc_notif_transparency)
                                6 -> moduleContext.getString(R.string.anc_notif_wind)
                                else -> moduleContext.getString(R.string.anc_notif_off)
                            }
                            val ancAction = Notification.Action.Builder(
                                Icon.createWithResource(context, android.R.drawable.ic_lock_silent_mode),
                                ancLabel,
                                PendingIntent.getBroadcast(context, 1, ancCycleIntent, 201326592)
                            ).build()
                            action = createAction("key_anc_cycle", ancAction)
                            actionTitle = ancLabel
                        }
                        addActionInfo {
                            val disconnectLabel = moduleContext.getString(R.string.notification_btn_disconnect)
                            val disconnectIntent = Intent("com.android.bluetooth.headset.notification").apply {
                                putExtra("btData", bundle)
                                putExtra("disconnect", "1")
                                setIdentifier("BTHeadset$address")
                            }
                            val disconnectAction = Notification.Action.Builder(
                                Icon.createWithResource(context, android.R.drawable.ic_delete),
                                disconnectLabel,
                                PendingIntent.getBroadcast(context, 2, disconnectIntent, 201326592)
                            ).build()
                            action = createAction("key_disconnect", disconnectAction)
                            actionTitle = disconnectLabel
                        }
                    }
                }
                // AOD 息屏显示
                if (focusExtras != null) {
                    val aodParts = mutableListOf<String>()
                    if (batteryParams.left?.isConnected == true)
                        aodParts.add("L ${batteryParams.left!!.battery}%")
                    if (batteryParams.right?.isConnected == true)
                        aodParts.add("R ${batteryParams.right!!.battery}%")
                    val aodTitle = aodParts.joinToString(" | ")
                    try {
                        val json = org.json.JSONObject(focusExtras.getString("miui.focus.param") ?: "{}")
                        val pv2 = json.optJSONObject("param_v2") ?: org.json.JSONObject()
                        pv2.put("aodTitle", aodTitle)
                        pv2.put("aodPic", "key_headset")
                        json.put("param_v2", pv2)
                        focusExtras.putString("miui.focus.param", json.toString())
                    } catch (_: Exception) {}
                }
                notificationManager.notifyAsUser(
                    "BTHeadset$address",
                    10003,
                    Notification.Builder(context, "BTHeadset$address")
                        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                        .setWhen(0L)
                        .setTicker(alias)
                        .setDefaults(-1)
                        .setContentTitle(alias)
                        .setContentText(contentText)
                        .setContentIntent(pendingIntent)
                        .setDeleteIntent(deleteIntent(context, bluetoothDevice))
                        .setColor(context.getColor(system_notification_accent_color))
                        .addAction(disconnectAction)
                        .apply { focusExtras?.let { addExtras(it) } }
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .build(),
                    SystemApisUtils.getUserAllUserHandle()
                )
            } catch (e: Exception) {
                Log.e("HyperOriG", "Failed to create Pod Notification", e)
            }
        }

        fun cancelNotification(bluetoothDevice: BluetoothDevice, context: Context) {
            try {
                val address = bluetoothDevice.address
                if (address.isNotEmpty()) {
                    val notificationManager = context.getSystemService("notification") as NotificationManager
                    notificationManager.cancelAsUser("BTHeadset$address", 10003, SystemApisUtils.getUserAllUserHandle())
                }
            } catch (e: Exception) {
                Log.e("HyperOriG", "Failed to cancel Pod Notification!", e)
            }
        }


        hookConstructorAfter(findConstructorByParamCount("com.android.bluetooth.ble.app.MiuiBluetoothNotification", 2)) {
            val context = getObjectField(instance, "mContext") as? Context
            android.util.Log.d("HyperOriG-Island", "MiuiBluetoothNotification constructor hooked, context=$context")
            if (context == null) return@hookConstructorAfter

                    val broadcastReceiver = object : BroadcastReceiver() {
                        override fun onReceive(p0: Context?, p1: Intent?) {
                            android.util.Log.d("HyperOriG-Island", "onReceive action=${p1?.action}")
                            if (p1?.action == "com.redwind.hyperorig.action.sendstrongtoast") {
                                val batteryParams = p1.getParcelableExtra("batteryParams", BatteryParams::class.java)!!
                                android.util.Log.d("HyperOriG-Island", "showBatteryIsland called left=${batteryParams.left?.battery} right=${batteryParams.right?.battery}")
                                FocusIslandUtil.showBatteryIsland(context, batteryParams)
                            } else if (p1?.action == "com.redwind.hyperorig.action.updatepodsnotification") {
                                val batteryParams = p1.getParcelableExtra<BatteryParams>("batteryParams", BatteryParams::class.java)
                                val device = p1.getParcelableExtra("device", BluetoothDevice::class.java)
                                if (device != null) lastConnectedDevice = device
                                if (batteryParams != null) lastBatteryParams = batteryParams
                                createPodsNotification(device, context, batteryParams!!)
                            } else if (p1?.action == "com.redwind.hyperorig.action.cancelpodsnotification") {
                                val device = p1.getParcelableExtra("device", BluetoothDevice::class.java) as BluetoothDevice
                                cancelNotification(device, context)
                            } else if (p1?.action == HyperOriGAction.ACTION_PODS_ANC_CHANGED) {
                                localAncMode = p1.getIntExtra("status", 1)
                                // 同步更新通知按钮文字
                                if (lastConnectedDevice != null && lastBatteryParams != null) {
                                    createPodsNotification(lastConnectedDevice, context, lastBatteryParams!!)
                                }
                            } else if (p1?.action == HyperOriGAction.ACTION_ADAPTIVE_MODE_CHANGED) {
                                val adaptiveEnabled = p1.getBooleanExtra("enabled", true)
                                if (!adaptiveEnabled && localAncMode == 4) {
                                    localAncMode = 2
                                }
                            } else if (p1?.action == HyperOriGAction.ACTION_CYCLE_ANC) {
                                localAncMode = when (localAncMode) {
                                    1 -> 3   // OFF → 降噪
                                    3 -> 2   // 降噪 → 通透
                                    4 -> 2   // 深度降噪 → 通透
                                    5 -> 2   // 试验性降噪 → 通透
                                    2 -> 1   // 通透 → OFF
                                    6 -> 3   // 抗风噪 → 降噪（视为OFF进入循环）
                                    else -> 1
                                }
                                // 发送降噪指令
                                Intent(HyperOriGAction.ACTION_ANC_SELECT).apply {
                                    putExtra("status", localAncMode)
                                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                                    p0?.sendBroadcast(this)
                                }
                                // 重新创建通知以更新按钮文字
                                if (lastConnectedDevice != null && lastBatteryParams != null) {
                                    createPodsNotification(lastConnectedDevice, context, lastBatteryParams!!)
                                }
                            }
                        }
                    }

                    val intentFilter = IntentFilter("com.redwind.hyperorig.action.sendstrongtoast")
                    intentFilter.addAction("com.redwind.hyperorig.action.updatepodsnotification")
                    intentFilter.addAction("com.redwind.hyperorig.action.cancelpodsnotification")
                    intentFilter.addAction(HyperOriGAction.ACTION_CYCLE_ANC)
                    intentFilter.addAction(HyperOriGAction.ACTION_PODS_ANC_CHANGED)
                    intentFilter.addAction(HyperOriGAction.ACTION_ADAPTIVE_MODE_CHANGED)
                    context.registerReceiver(broadcastReceiver, intentFilter,
                        Context.RECEIVER_EXPORTED)
        }
    }
}

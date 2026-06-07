package com.redwind.hyperorig.hook

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent

/**
 * Hook MxBluetoothService.switchToHeadsetActivity in com.xiaomi.bluetooth:
 * 拦截互联控制窗"更多设置"按钮，OriG 设备跳转到 HyperOriG 而不是 MiuiHeadsetActivity。
 */
@SuppressLint("MissingPermission")
object MoreSettingsRedirectHook : HookContext() {
    private const val TAG = "HyperOriG-MoreSettings"

    override fun onHook() {
        runCatching {
            hookBefore(findMethod("com.xiaomi.mxbluetoothsdk.service.MxBluetoothService", "switchToHeadsetActivity", BluetoothDevice::class.java)) {
                try {
                    val device = args[0] as? BluetoothDevice ?: return@hookBefore
                    val name = runCatching { device.name ?: device.alias }.getOrNull().orEmpty()
                    val address = runCatching { device.address }.getOrNull().orEmpty()
                    val isOriG = name.contains("YUANDAO", ignoreCase = true) ||
                        name.contains("OriG", ignoreCase = true) ||
                        name.contains("NiceHCK", ignoreCase = true)
                    android.util.Log.d(TAG, "switchToHeadsetActivity device=$address name=$name isOriG=$isOriG")
                    if (!isOriG) return@hookBefore
                    val ctx = runCatching { getObjectField(instance, "mContext") as? Context }.getOrNull()
                    if (ctx == null) {
                        android.util.Log.w(TAG, "context is null")
                        return@hookBefore
                    }
                    ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage("com.redwind.hyperorig")?.apply {
                        putExtra("open_detail", true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    })
                    this.result = null
                    android.util.Log.d(TAG, "→ HyperOriG address=$address")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "hook error", e)
                }
            }
            android.util.Log.d(TAG, "switchToHeadsetActivity hook installed")
        }.onFailure { android.util.Log.w(TAG, "hook skipped", it) }
    }
}

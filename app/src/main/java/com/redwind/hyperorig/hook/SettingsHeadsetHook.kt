package com.redwind.hyperorig.hook

/**
 * Hook Settings 进程中的 HeadsetIDConstants.checkSupport：
 * 让 OriG 设备不被识别为小米耳机，阻止 MiuiHeadsetActivity 打开。
 */
object SettingsHeadsetHook : HookContext() {
    private const val TAG = "HyperOriG-Settings"

    override fun onHook() {
        runCatching {
            val method = findMethod("com.android.settings.bluetooth.HeadsetIDConstants", "checkSupport", String::class.java)
            module.hook(method).intercept { chain ->
                val original = chain.proceed()
                val support = chain.args.getOrNull(0) as? String
                val fakeId = fakeDeviceId()
                if (support != null && (support.startsWith(fakeId) || support.contains(fakeId))) {
                    android.util.Log.d(TAG, "checkSupport blocked fakeId=$fakeId support=$support")
                    false
                } else {
                    original
                }
            }
            android.util.Log.d(TAG, "checkSupport hook installed")
        }.onFailure { android.util.Log.w(TAG, "checkSupport hook skipped", it) }
    }
}

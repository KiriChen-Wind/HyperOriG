package com.redwind.hyperorig.hook

object SystemUIPluginHook : HookContext() {
    private var pluginLoaderClassLoader: ClassLoader? = null

    override fun onHook() {
        // Hook PluginInstance.loadPlugin to get the plugin classloader on Android U+
        hookAfter(findMethod("com.android.systemui.shared.plugins.PluginInstance", "loadPlugin")) {
            val pkgName = callMethod(instance, "getPackage") as? String
            if (pkgName == "miui.systemui.plugin") {
                val factory = getObjectField(instance, "mPluginFactory")
                val clsLoader = callMethod(
                    getObjectField(factory, "mClassLoaderFactory"),
                    "get"
                ) as ClassLoader
                if (pluginLoaderClassLoader != clsLoader) {
                    Log.i("HyperOriG", "[loadPlugin] initPluginHook")
                    pluginLoaderClassLoader = clsLoader
                    initDeviceCardHook()
                }
            }
        }
    }

    private fun initDeviceCardHook() {
        val classLoader = pluginLoaderClassLoader ?: return
        val deviceCardHook = DeviceCardHook
        deviceCardHook.module = module
        deviceCardHook.appClassLoader = classLoader
        deviceCardHook.packageName = packageName
        deviceCardHook.prefs = prefs
        deviceCardHook.onHook()
    }
}

package io.drsr.hotspotadb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.util.Log
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor

/**
 * Hooks in the system_server (android scope) that keep Wireless Debugging alive while a Wi-Fi
 * hotspot is active.
 *
 * Two interception points:
 *
 * 1. AdbDebuggingHandler.getCurrentWifiApInfo()
 *    Returns the AP info used to verify the trusted network.  When no station Wi-Fi is
 *    connected but a hotspot is active the method returns null, which would disable wireless
 *    debugging.  We synthesise an AdbConnectionInfo so the framework accepts the hotspot network.
 *
 * 2. BroadcastReceiver.onReceive() for WIFI_STATE_CHANGED / NETWORK_STATE_CHANGED
 *    ADB Wi-Fi monitoring reacts to these broadcasts by tearing down the connection when the
 *    device is no longer a Wi-Fi client.  We suppress those events while hotspot is active.
 *
 * Android 16 compatibility strategy
 * - AdbConnectionInfo: try top-level com.android.server.adb.AdbConnectionInfo first (Android 16),
 *   then nested AdbDebuggingManager$AdbConnectionInfo (Android 15).
 * - Receiver class: try named candidates first (Android 16 refactoring), then anonymous inner
 *   class scan (Android 15), then Settings.Global.putInt fallback as last resort.
 */
object FrameworkHook {

    // Stable synthetic BSSID.  ADB uses BSSID as part of the trusted-network fingerprint.
    // A fixed value prevents trust from being reset every time the hotspot is re-enabled
    // (Android randomises the real hotspot MAC on each enable cycle).
    private const val SYNTHETIC_BSSID = "02:00:00:00:00:00"

    // Resolved once at install time; null means no suitable constructor was found.
    private var connectionInfoCtor: Constructor<*>? = null

    fun install(classLoader: ClassLoader, module: XposedModule) {
        hookGetCurrentWifiApInfo(classLoader, module)
        hookNetworkMonitorOrFallback(classLoader, module)
    }

    // ---- getCurrentWifiApInfo ----

    private fun hookGetCurrentWifiApInfo(classLoader: ClassLoader, module: XposedModule) {
        val handlerClass = tryFindClass(
            "com.android.server.adb.AdbDebuggingManager\$AdbDebuggingHandler",
            classLoader,
        ) ?: run {
            module.log(Log.WARN, TAG, "AdbDebuggingHandler not found; getCurrentWifiApInfo hook skipped")
            return
        }

        val method = try {
            handlerClass.getDeclaredMethod("getCurrentWifiApInfo").also { it.isAccessible = true }
        } catch (e: NoSuchMethodException) {
            module.log(Log.WARN, TAG, "getCurrentWifiApInfo not found in AdbDebuggingHandler: $e")
            return
        }

        // Resolve AdbConnectionInfo constructor once; log which branch was selected.
        connectionInfoCtor = resolveConnectionInfoCtor(classLoader, module)

        // Deoptimise to prevent the JIT from inlining callers (e.g. handleMessage) and
        // skipping the hook.
        module.deoptimize(method)

        module.hook(method).intercept { chain ->
            val result = chain.proceed()
            if (result != null) return@intercept result

            val context = getContext(chain.getThisObject()) ?: return@intercept null
            if (!HotspotHelper.isHotspotActive(context)) return@intercept null

            val ctor = connectionInfoCtor ?: run {
                module.log(Log.WARN, TAG, "AdbConnectionInfo ctor not resolved; cannot synthesise AP info")
                return@intercept null
            }

            val ssid = getHotspotSsid(context)
            try {
                val info = ctor.newInstance(SYNTHETIC_BSSID, ssid)
                module.log(Log.INFO, TAG, "getCurrentWifiApInfo → synthetic (bssid=$SYNTHETIC_BSSID ssid=$ssid)")
                info
            } catch (e: Exception) {
                module.log(Log.ERROR, TAG, "failed to create AdbConnectionInfo: $e")
                null
            }
        }
        module.log(Log.INFO, TAG, "hooked AdbDebuggingHandler.getCurrentWifiApInfo")
    }

    /**
     * Resolve AdbConnectionInfo(String bssid, String ssid) constructor.
     *
     * Candidate order:
     *  1. com.android.server.adb.AdbConnectionInfo          (Android 16, top-level class)
     *  2. com.android.server.adb.AdbDebuggingManager$AdbConnectionInfo  (Android 15, nested)
     */
    private fun resolveConnectionInfoCtor(classLoader: ClassLoader, module: XposedModule): Constructor<*>? {
        val candidates = listOf(
            "com.android.server.adb.AdbConnectionInfo",
            "com.android.server.adb.AdbDebuggingManager\$AdbConnectionInfo",
        )
        for (name in candidates) {
            val clazz = tryFindClass(name, classLoader) ?: continue
            return try {
                clazz.getDeclaredConstructor(String::class.java, String::class.java)
                    .also {
                        it.isAccessible = true
                        module.log(Log.INFO, TAG, "resolved AdbConnectionInfo as $name")
                    }
            } catch (e: NoSuchMethodException) {
                module.log(Log.DEBUG, TAG, "no (String,String) ctor in $name: $e")
                continue
            }
        }
        module.log(Log.WARN, TAG, "AdbConnectionInfo constructor not found in any candidate class")
        return null
    }

    // ---- BroadcastReceiver / network monitor hooks ----

    private fun hookNetworkMonitorOrFallback(classLoader: ClassLoader, module: XposedModule) {
        // Android 16 may introduce dedicated top-level or named inner classes for ADB Wi-Fi
        // monitoring.  Try named candidates before falling back to the anonymous inner class
        // scan used on Android 15.
        val namedCandidates = listOf(
            "com.android.server.adb.AdbDebuggingManager\$AdbBroadcastReceiver",
            "com.android.server.adb.AdbBroadcastReceiver",
            "com.android.server.adb.AdbDebuggingManager\$AdbNetworkMonitor",
            "com.android.server.adb.AdbNetworkMonitor",
            "com.android.server.adb.AdbWifiNetworkMonitor",
        )
        for (name in namedCandidates) {
            val clazz = tryFindClass(name, classLoader) ?: continue
            if (!BroadcastReceiver::class.java.isAssignableFrom(clazz)) continue
            if (hookOnReceive(clazz, module, "named receiver $name")) return
        }

        // Fall back: anonymous inner classes of AdbDebuggingHandler (Android 15 path).
        val baseName = "com.android.server.adb.AdbDebuggingManager\$AdbDebuggingHandler"
        for (i in 1..15) {
            val clazz = tryFindClass("$baseName\$$i", classLoader) ?: continue
            if (!BroadcastReceiver::class.java.isAssignableFrom(clazz)) continue
            if (hookOnReceive(clazz, module, "anonymous inner class $baseName\$$i (Android 15 path)")) return
        }

        // Last resort: intercept Settings.Global.putInt to block adb_wifi_enabled=0.
        module.log(Log.INFO, TAG, "no ADB BroadcastReceiver found; activating Settings.Global.putInt fallback")
        hookSettingsGlobalFallback(classLoader, module)
    }

    private fun hookOnReceive(clazz: Class<*>, module: XposedModule, label: String): Boolean {
        return try {
            val onReceive = clazz.getDeclaredMethod("onReceive", Context::class.java, Intent::class.java)
                .also { it.isAccessible = true }

            module.hook(onReceive).intercept { chain ->
                val context = chain.getArg(0) as? Context ?: return@intercept chain.proceed()
                val intent = chain.getArg(1) as? Intent ?: return@intercept chain.proceed()
                val action = intent.action ?: return@intercept chain.proceed()

                if ((action == WifiManager.WIFI_STATE_CHANGED_ACTION ||
                            action == WifiManager.NETWORK_STATE_CHANGED_ACTION) &&
                    HotspotHelper.isHotspotActive(context)
                ) {
                    module.log(Log.INFO, TAG, "suppressed $action via $label (hotspot active)")
                    null // void method — suppresses the call by not invoking proceed()
                } else {
                    chain.proceed()
                }
            }
            module.log(Log.INFO, TAG, "hooked BroadcastReceiver.onReceive via $label")
            true
        } catch (e: Exception) {
            module.log(Log.DEBUG, TAG, "failed to hook onReceive in ${clazz.name}: $e")
            false
        }
    }

    /**
     * Fallback only — active when no ADB BroadcastReceiver hook was installed.
     * Intercepts Settings.Global.putInt to prevent ADB_WIFI_ENABLED being set to 0 while
     * the hotspot is active.
     */
    private fun hookSettingsGlobalFallback(classLoader: ClassLoader, module: XposedModule) {
        try {
            val settingsGlobal = Class.forName("android.provider.Settings\$Global", false, classLoader)
            val putInt = settingsGlobal.getDeclaredMethod(
                "putInt",
                android.content.ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
            ).also { it.isAccessible = true }

            module.hook(putInt).intercept { chain ->
                val key = chain.getArg(1) as? String
                val value = chain.getArg(2) as? Int
                if (key == "adb_wifi_enabled" && value == 0) {
                    val resolver = chain.getArg(0) as? android.content.ContentResolver
                    val context = resolver?.let { getContextFromResolver(it) }
                    if (context != null && HotspotHelper.isHotspotActive(context)) {
                        module.log(Log.INFO, TAG, "blocked ADB_WIFI_ENABLED=0 via Settings.Global fallback (hotspot active)")
                        return@intercept false
                    }
                }
                chain.proceed()
            }
            module.log(Log.INFO, TAG, "installed Settings.Global.putInt fallback hook")
        } catch (e: Exception) {
            module.log(Log.ERROR, TAG, "failed to install Settings.Global.putInt fallback: $e")
        }
    }

    // ---- Reflection helpers ----

    /**
     * Get the Context from an AdbDebuggingHandler instance.
     *
     * On Android 15: AdbDebuggingHandler is an inner class of AdbDebuggingManager.
     *   handler.this$0 → AdbDebuggingManager instance → .mContext
     * On Android 16: if the class was made top-level, try searching for a Context-typed field
     * directly on the handler, or for a field whose type is AdbDebuggingManager.
     */
    private fun getContext(handler: Any?): Context? {
        handler ?: return null
        return try {
            // Android 15: inner class reference
            val outer = getFieldValue(handler, "this\$0")
                ?: findFieldByTypeName(handler, "com.android.server.adb.AdbDebuggingManager")
                ?: handler // handler might be top-level and have mContext directly

            (getFieldValue(outer, "mContext") as? Context)
                ?: (getFieldValue(handler, "mContext") as? Context)
        } catch (e: Exception) {
            Log.w(TAG, "HotspotAdb: failed to get context from handler: $e")
            null
        }
    }

    private fun getContextFromResolver(resolver: android.content.ContentResolver): Context? {
        return try {
            resolver.javaClass.getMethod("getContext").invoke(resolver) as? Context
        } catch (_: Exception) {
            null
        }
    }

    private fun getHotspotSsid(context: Context): String {
        return try {
            val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val config = wm.javaClass.getMethod("getSoftApConfiguration").invoke(wm)
            val wifiSsid = config.javaClass.getMethod("getWifiSsid").invoke(config)
            wifiSsid?.toString() ?: "HotspotAP"
        } catch (_: Throwable) {
            "HotspotAP"
        }
    }

    private fun tryFindClass(name: String, classLoader: ClassLoader): Class<*>? {
        return try {
            Class.forName(name, false, classLoader)
        } catch (_: ClassNotFoundException) {
            null
        }
    }

    private fun getFieldValue(obj: Any, name: String): Any? {
        var cls: Class<*>? = obj.javaClass
        while (cls != null && cls != Any::class.java) {
            try {
                val field = cls.getDeclaredField(name)
                field.isAccessible = true
                return field.get(obj)
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        return null
    }

    private fun findFieldByTypeName(obj: Any, typeName: String): Any? {
        var cls: Class<*>? = obj.javaClass
        while (cls != null && cls != Any::class.java) {
            for (field in cls.declaredFields) {
                if (field.type.name == typeName) {
                    field.isAccessible = true
                    return field.get(obj)
                }
            }
            cls = cls.superclass
        }
        return null
    }

    private const val TAG = HotspotAdbModule.TAG
}

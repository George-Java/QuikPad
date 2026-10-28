package com.yg.liquidink

import android.app.ActivityManager
import android.os.Build
import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var documentAssets: DocumentAssetBridge? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (documentAssets?.onActivityResult(requestCode, resultCode, data) == true) return
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        documentAssets = DocumentAssetBridge(this,
            MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "quikpad/documents"))
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "liquid_ink/diagnostics")
            .setMethodCallHandler { call, result ->
                if (call.method != "exitHistory") {
                    result.notImplemented()
                    return@setMethodCallHandler
                }
                try {
                    val history = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                        manager.getHistoricalProcessExitReasons(packageName, 0, 8).map {
                            mapOf(
                                "timestamp" to it.timestamp,
                                "reason" to it.reason,
                                "status" to it.status,
                                "pssKb" to it.pss,
                                "rssKb" to it.rss,
                            )
                        }
                    } else emptyList()
                    result.success(mapOf(
                        "androidSdk" to Build.VERSION.SDK_INT,
                        "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                        "exits" to history,
                    ))
                } catch (error: Exception) {
                    result.error("EXIT_HISTORY", error.javaClass.simpleName, null)
                }
            }
    }
}

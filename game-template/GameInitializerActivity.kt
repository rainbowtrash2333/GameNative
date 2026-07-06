package com.yourapp.gameinit

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Game APK 首次安装时的初始化 Activity。
 *
 * 功能:
 *   1. 将 assets/game/ 中的游戏文件解压到 filesDir/game/
 *   2. 生成 game_config.json (游戏启动配置)
 *   3. 完成后自动退出 (finish())
 *
 * 此 Activity 不在桌面显示图标, 由 Runtime APK 在
 * 检测到新 APK 安装后通过 PackageManager 启动。
 *
 * 用户安装 Game APK 后首次打开时触发, 也可由 Runtime APK 的
 * GameInstallWatcher 在收到 PACKAGE_ADDED 广播后远程触发。
 */
class GameInitializerActivity : ComponentActivity() {

    companion object {
        private const val TAG = "GameInitializer"
        const val CONFIG_FILE = "game_config.json"
        const val GAME_ASSET_DIR = "game"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val gameDir = File(filesDir, GAME_ASSET_DIR)

                // 首次运行: 解压游戏文件
                if (!gameDir.exists() || gameDir.listFiles()?.isEmpty() == true) {
                    gameDir.mkdirs()
                    copyAssetFolder(GAME_ASSET_DIR, gameDir.absolutePath)
                    Log.i(TAG, "Game files extracted to $gameDir")
                }

                // 生成 game_config.json
                val configFile = File(filesDir, CONFIG_FILE)
                if (!configFile.exists()) {
                    val config = GameConfigGenerator.generate(this@GameInitializerActivity)
                    configFile.writeText(config)
                    Log.i(TAG, "Config generated: $configFile")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
                // 写入错误标记供 Runtime 检测
                File(filesDir, "init_error").writeText(e.message ?: "Unknown error")
            } finally {
                finish()
            }
        }
    }

    /**
     * 递归复制 assets 目录到目标路径。
     */
    private fun copyAssetFolder(assetPath: String, destPath: String) {
        val assets = assets.list(assetPath) ?: return
        for (name in assets) {
            val subPath = if (assetPath.isEmpty()) name else "$assetPath/$name"
            val destFile = File(destPath, name)

            // 判断是文件还是目录: 尝试以文件方式打开
            if (tryOpenAsFile(subPath)) {
                assets.open(subPath).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                destFile.mkdirs()
                copyAssetFolder(subPath, destFile.absolutePath)
            }
        }
    }

    private fun tryOpenAsFile(assetPath: String): Boolean {
        return try {
            assets.open(assetPath).close()
            true
        } catch (e: Exception) {
            false
        }
    }
}

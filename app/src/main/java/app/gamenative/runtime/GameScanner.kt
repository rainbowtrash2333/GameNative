package app.gamenative.runtime

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

/**
 * 扫描设备上通过 sharedUserId 关联的游戏 APK。
 *
 * 通信机制:
 *   Runtime APK 和 Game APK 声明相同的 android:sharedUserId。
 *   同一 UID 下, Runtime 可以直接读取 Game APK 的 /data/data/<pkg>/files/ 目录。
 *
 * 扫描流程:
 *   1. getInstalledPackages() 获取所有已安装应用
 *   2. 过滤 sharedUserId == SHARED_USER_ID 且非自身
 *   3. 读取每个 Game APK 的 files/game_config.json
 *   4. 返回 InstalledGame 列表
 */
class GameScanner(private val context: Context) {

    companion object {
        /** Runtime 和所有 Game APK 共享的用户 ID */
        const val SHARED_USER_ID = "app.gamenative"
    }

    /**
     * 扫描已安装的游戏。
     * @return 已安装游戏列表 (可能为空)
     */
    fun scanInstalledGames(): List<InstalledGame> {
        val pm = context.packageManager
        val runtimePkg = context.packageName

        return pm.getInstalledPackages(PackageManager.GET_META_DATA)
            .filter { pkg ->
                // 匹配 sharedUserId 且排除 Runtime 自身
                pkg.sharedUserId != null &&
                    pkg.sharedUserId == SHARED_USER_ID &&
                    pkg.packageName != runtimePkg
            }
            .mapNotNull { pkg ->
                val filesDir = File(pkg.applicationInfo.dataDir, "files")
                val configFile = File(filesDir, "game_config.json")
                if (!configFile.exists()) return@mapNotNull null

                try {
                    val config = GameConfigParser.parse(configFile)
                    InstalledGame(
                        gameId = config.gameId,
                        gameName = config.gameName,
                        gameVersion = config.version,
                        packageName = pkg.packageName,
                        config = config,
                        dataDir = filesDir,
                        estimatedSize = config.estimatedSize,
                    )
                } catch (e: Exception) {
                    // game_config.json 格式错误, 跳过此游戏
                    null
                }
            }
    }
}

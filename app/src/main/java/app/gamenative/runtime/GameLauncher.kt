package app.gamenative.runtime

import android.content.Context
import android.content.Intent
import java.io.File

/**
 * 游戏启动器: 桥接 Runtime UI 到 GameNative 现有引擎。
 *
 * 策略 (方案一: 不修改引擎代码):
 *   直接使用 GameNative 现有的 Intent 启动机制。
 *   GameNative 的 app.gamenative.MainActivity 会处理所有复杂的
 *   XEnvironment 组件初始化和 Wine 启动流程。
 *
 *   我们的工作:
 *     1. 确保游戏文件在 Game APK 的 data 目录中可访问 (sharedUserId)
 *     2. 构造启动 Intent, 传递游戏路径和配置
 *     3. 交给 GameNative 现有代码执行
 */
class GameLauncher(private val context: Context) {

    /**
     * 通过 Intent 启动游戏。
     * targetActivity: app.gamenative.MainActivity (GameNative 原有入口)
     *
     * 后续可扩展为直接对接 com.winlator.* 引擎 API,
     * 绕过 app.gamenative 的 UI 层, 减少对 GameNative 现有代码的依赖。
     */
    fun launch(game: InstalledGame) {
        val config = game.config
        val gameExePath = File(game.dataDir, "game/${config.launchFile}").absolutePath

        val intent = Intent(context, Class.forName("app.gamenative.MainActivity")).apply {
            action = "app.gamenative.LAUNCH_GAME"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("runtime_game_id", game.gameId)
            putExtra("runtime_game_name", game.gameName)
            putExtra("runtime_game_exe_path", gameExePath)
            putExtra("runtime_game_config", """
                {
                    "wineVersion": "${config.containerConfig.wineVersion}",
                    "emulator": "${config.containerConfig.emulator}",
                    "dxvkVersion": "${config.containerConfig.dxvkVersion}",
                    "renderer": "${config.containerConfig.renderer}",
                    "resolution": "${config.containerConfig.resolution}"
                }
            """.trimIndent())
        }

        context.startActivity(intent)
    }
}

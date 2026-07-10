package app.gamenative.runtime

import android.content.Context
import android.content.Intent
import app.gamenative.MainActivity
import kotlin.math.abs
import timber.log.Timber
import java.io.File

/**
 * 启动已导入的游戏。
 *
 * 构建一个与 [app.gamenative.utils.IntentLaunchManager] 兼容的 Intent，
 * 启动 [MainActivity] 进行游戏运行。
 *
 * Intent extras 必须与 IntentLaunchManager 的预期匹配:
 *   - "app_id" (Int)          ← 游戏数字 ID
 *   - "game_source" (String)   ← 游戏来源, 如 "CUSTOM_GAME"
 *   - "container_config" (JSON, 可选) — 暂不传, 沿用容器默认配置
 *
 * 额外的 runtime_* extras 作为附加信息携带, IntentLaunchManager 忽略未知 key。
 */
class GameLauncher(private val context: Context) {

    companion object {
        private const val TAG = "GameLauncher"
        private const val EXTRA_APP_ID = "app_id"
        private const val EXTRA_GAME_SOURCE = "game_source"
        private const val ACTION_LAUNCH_GAME = "app.gamenative.LAUNCH_GAME"
    }

    /**
     * 启动游戏。
     * @param game 待启动的游戏实例
     * @throws IllegalStateException 如果可执行文件不存在
     * @throws IllegalArgumentException 如果 gameId 不是有效正整数
     */
    fun launch(game: InstalledGame) {
        Timber.tag(TAG).i("launch(): gameId=%s, gameName=%s", game.gameId, game.gameName)

        val config = game.config
        val gameExe = File(game.dataDir, config.launchFile)
        if (!gameExe.exists()) {
            val msg = "Game executable not found: ${gameExe.absolutePath}"
            Timber.tag(TAG).e(msg)
            throw IllegalStateException(msg)
        }
        Timber.tag(TAG).i("Executable verified: %s (size=%d)", gameExe.absolutePath, gameExe.length())

        val numericId = abs(game.gameId.hashCode()).let { if (it == 0) 1 else it }
        Timber.tag(TAG).d("Parsed gameId: %s -> %d", game.gameId, numericId)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_LAUNCH_GAME
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_APP_ID, numericId)
            putExtra(EXTRA_GAME_SOURCE, "CUSTOM_GAME")
            putExtra("runtime_game_id", game.gameId)
            putExtra("runtime_game_name", game.gameName)
            putExtra("runtime_game_exe_path", gameExe.absolutePath)
            putExtra("runtime_game_dir", game.dataDir.absolutePath)
        }

        Timber.tag(TAG).i("Starting MainActivity: action=%s, appId=%d, gameName=%s",
            ACTION_LAUNCH_GAME, numericId, game.gameName)

        try {
            context.startActivity(intent)
            Timber.tag(TAG).i("MainActivity started for '%s' (id=%d)", game.gameName, numericId)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to start MainActivity for '%s'", game.gameName)
            throw e
        }
    }
}

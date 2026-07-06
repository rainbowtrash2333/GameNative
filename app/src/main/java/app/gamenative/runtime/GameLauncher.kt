package app.gamenative.runtime

import android.content.Context
import android.content.Intent
import java.io.File

class GameLauncher(private val context: Context) {

    fun launch(game: InstalledGame) {
        val config = game.config
        val gameExe = File(game.dataDir, config.launchFile)
        if (!gameExe.exists()) {
            throw IllegalStateException("Game executable not found: ${gameExe.absolutePath}")
        }

        val intent = Intent(context, Class.forName("app.gamenative.MainActivity")).apply {
            action = "app.gamenative.LAUNCH_GAME"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("runtime_game_id", game.gameId)
            putExtra("runtime_game_name", game.gameName)
            putExtra("runtime_game_exe_path", gameExe.absolutePath)
            putExtra("runtime_game_dir", game.dataDir.absolutePath)
            putExtra("runtime_game_config", buildConfigJson(config))
        }

        context.startActivity(intent)
    }

    private fun buildConfigJson(config: GameConfig): String = buildString {
        val cc = config.containerConfig
        appendLine("{")
        appendLine("  \"wineVersion\": \"${cc.wineVersion}\",")
        appendLine("  \"emulator\": \"${cc.emulator}\",")
        appendLine("  \"dxvkVersion\": \"${cc.dxvkVersion}\",")
        appendLine("  \"vkd3dVersion\": \"${cc.vkd3dVersion}\",")
        appendLine("  \"renderer\": \"${cc.renderer}\",")
        appendLine("  \"resolution\": \"${cc.resolution}\",")
        appendLine("  \"envVars\": {")
        config.envVars.entries.forEachIndexed { i, (k, v) ->
            val comma = if (i < config.envVars.size - 1) "," else ""
            appendLine("    \"$k\": \"$v\"$comma")
        }
        appendLine("  },")
        appendLine("  \"launchArgs\": \"${config.launchArgs}\"")
        append("}")
    }
}

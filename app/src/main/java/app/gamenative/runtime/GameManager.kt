package app.gamenative.runtime

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipFile

/**
 * 本地游戏管理器: 扫描 + 导入 ZIP 游戏包。
 *
 * 单 APK 方案: 用户下载 game.zip(含 game_config.json + 游戏文件),
 * 通过 SAF 文件选择器导入, 管理器解压到 filesDir/games/{gameId}/。
 */
class GameManager(private val context: Context) {

    companion object {
        const val GAMES_DIR = "games"
        const val CONFIG_FILE = "game_config.json"
    }

    private val gamesBaseDir: File
        get() = File(context.filesDir, GAMES_DIR)

    private val json = Json { ignoreUnknownKeys = true }

    /** 扫描本地已安装的游戏 */
    fun scanInstalledGames(): List<InstalledGame> {
        val dir = gamesBaseDir
        if (!dir.exists()) return emptyList()

        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { gameDir ->
                val configFile = File(gameDir, CONFIG_FILE)
                if (!configFile.exists()) return@mapNotNull null
                try {
                    val config = json.decodeFromString<GameConfig>(configFile.readText())
                    InstalledGame(
                        gameId = config.gameId,
                        gameName = config.gameName,
                        gameVersion = config.version,
                        packageName = "",
                        config = config,
                        dataDir = gameDir,
                        estimatedSize = formatSize(calculateDirSize(gameDir)),
                    )
                } catch (e: Exception) { null }
            }
            ?: emptyList()
    }

    /**
     * 导入游戏 ZIP 文件。
     * @param zipPath  ZIP 文件路径 (SAF 复制到缓存后的路径)
     * @return 导入结果 (成功/失败 + 错误消息)
     */
    fun importGame(zipPath: String): ImportResult {
        val zipFile = File(zipPath)
        if (!zipFile.exists()) return ImportResult.Error("ZIP file not found: $zipPath")

        return try {
            // 1. 验证 ZIP 内包含 game_config.json
            val configJson = readEntryFromZip(zipFile, CONFIG_FILE)
                ?: return ImportResult.Error("ZIP 文件缺少 ${CONFIG_FILE}，请确认格式")

            val config = json.decodeFromString<GameConfig>(configJson)

            // 2. 解压到 filesDir/games/{gameId}/
            val targetDir = File(gamesBaseDir, config.gameId)
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            targetDir.mkdirs()

            extractZip(zipFile, targetDir)

            // 3. 清理临时 ZIP 副本
            zipFile.delete()

            ImportResult.Success(config)
        } catch (e: Exception) {
            ImportResult.Error("导入失败: ${e.message}")
        }
    }

    /** 删除游戏 */
    fun deleteGame(gameId: String) {
        val gameDir = File(gamesBaseDir, gameId)
        if (gameDir.exists()) gameDir.deleteRecursively()
    }

    // --- 内部 ---

    private fun readEntryFromZip(zipFile: File, entryName: String): String? {
        return try {
            ZipFile(zipFile).use { zip ->
                val entry = zip.getEntry(entryName) ?: return null
                zip.getInputStream(entry).bufferedReader().readText()
            }
        } catch (e: Exception) { null }
    }

    private fun extractZip(zipFile: File, targetDir: File) {
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val dest = File(targetDir, entry.name)

                if (entry.isDirectory) {
                    dest.mkdirs()
                } else {
                    dest.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        dest.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    dest.setExecutable(true, false) // PE/ELF 可能需要执行权限
                }
            }
        }
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
        return size
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
        }
    }
}

sealed class ImportResult {
    data class Success(val config: GameConfig) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

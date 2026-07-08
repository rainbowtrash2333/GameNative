package app.gamenative.runtime

import android.content.Context
import app.gamenative.PrefManager
import app.gamenative.utils.FileUtils
import app.gamenative.utils.GameMetadata
import app.gamenative.utils.GameMetadataManager
import kotlin.math.abs
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile

/**
 * 本地游戏管理器: 扫描 + 导入 ZIP 游戏包。
 *
 * 用户通过 SAF 选择 game.zip (内含 game_config.json + 游戏文件),
 * 管理器解压到 filesDir/games/{gameId}/。
 */
class GameManager(private val context: Context) {

    companion object {
        private const val TAG = "GameManager"
        const val GAMES_DIR = "games"
        const val CONFIG_FILE = "game_config.json"
    }

    private val gamesBaseDir: File
        get() = File(context.filesDir, GAMES_DIR)

    private val json = Json { ignoreUnknownKeys = true }

    fun scanInstalledGames(): List<InstalledGame> {
        val dir = gamesBaseDir
        if (!dir.exists()) {
            Timber.tag(TAG).i("scanInstalledGames: games dir not found (%s), returning empty", dir.path)
            return emptyList()
        }

        val games = dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { gameDir ->
                val configFile = File(gameDir, CONFIG_FILE)
                if (!configFile.exists()) {
                    Timber.tag(TAG).w("scanInstalledGames: skipping %s (no %s)", gameDir.name, CONFIG_FILE)
                    return@mapNotNull null
                }
                try {
                    val config = json.decodeFromString<GameConfig>(configFile.readText())
                    val sizeBytes = FileUtils.calculateDirectorySize(gameDir)
                    InstalledGame(
                        gameId = config.gameId,
                        gameName = config.gameName,
                        gameVersion = config.version,
                        packageName = "",
                        config = config,
                        dataDir = gameDir,
                        estimatedSize = formatSize(sizeBytes),
                    ).also {
                        Timber.tag(TAG).d("scanInstalledGames: found '%s' (id=%s, size=%s)",
                            config.gameName, config.gameId, it.estimatedSize)
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "scanInstalledGames: failed to parse %s", configFile.path)
                    null
                }
            }
            ?: emptyList()

        Timber.tag(TAG).i("scanInstalledGames: found %d game(s)", games.size)
        return games
    }

    /**
     * 导入游戏 ZIP 文件。
     * @param zipPath ZIP 文件路径 (SAF 复制到缓存后的路径)
     * @return 导入结果
     */
    fun importGame(zipPath: String): ImportResult {
        val zipFile = File(zipPath)
        if (!zipFile.exists()) {
            Timber.tag(TAG).w("importGame: ZIP not found at %s", zipPath)
            return ImportResult.Error("ZIP file not found: $zipPath")
        }
        Timber.tag(TAG).i("importGame: starting import from %s (size=%d)", zipPath, zipFile.length())

        return try {
            val configJson = readEntryFromZip(zipFile, CONFIG_FILE)
                ?: return ImportResult.Error("ZIP 文件缺少 ${CONFIG_FILE}，请确认格式").also {
                    Timber.tag(TAG).w("importGame: ZIP missing %s", CONFIG_FILE)
                }

            val config = json.decodeFromString<GameConfig>(configJson)
            Timber.tag(TAG).i("importGame: parsed config: gameId=%s, gameName=%s", config.gameId, config.gameName)

            val targetDir = File(gamesBaseDir, config.gameId)
            if (targetDir.exists()) {
                Timber.tag(TAG).w("importGame: target dir %s exists, removing", targetDir.path)
                targetDir.deleteRecursively()
            }
            targetDir.mkdirs()
            Timber.tag(TAG).d("importGame: extracting to %s", targetDir.path)

            extractZip(zipFile, targetDir)
            zipFile.delete()

            // 注册到上游 CustomGameScanner 系统, 使得 resolveGameAppId 能找到此游戏
            registerWithCustomScanner(targetDir, config)

            Timber.tag(TAG).i("importGame: success for '%s' (id=%s)", config.gameName, config.gameId)
            ImportResult.Success(config)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "importGame: failed for %s", zipPath)
            ImportResult.Error("导入失败: ${e.message}")
        }
    }

    /** 删除游戏 */
    fun deleteGame(gameId: String) {
        val gameDir = File(gamesBaseDir, gameId)
        if (gameDir.exists()) {
            unregisterFromCustomScanner(gameDir, gameId)
            gameDir.deleteRecursively()
            Timber.tag(TAG).i("deleteGame: deleted %s", gameId)
        } else {
            Timber.tag(TAG).w("deleteGame: game dir not found for %s", gameId)
        }
    }

    /**
     * 导入成功后, 将游戏注册到上游的 CustomGameScanner 系统中,
     * 使得 resolveGameAppId(GameSource.CUSTOM_GAME) 能识别此游戏。
     *
     * 写入 .gamenative 文件 (包含稳定的 hashCode ID) +
     * 将目录添加到 PrefManager.customGameManualFolders。
     */
    private fun registerWithCustomScanner(gameDir: File, config: GameConfig) {
        val stableId = abs(config.gameId.hashCode()).let { if (it == 0) 1 else it }
        Timber.tag(TAG).d("registerWithCustomScanner: gameId=%s -> stableId=%d", config.gameId, stableId)

        GameMetadataManager.write(gameDir, GameMetadata(appId = stableId))

        val folders = PrefManager.customGameManualFolders.toMutableSet()
        if (folders.add(gameDir.absolutePath)) {
            PrefManager.customGameManualFolders = folders
            Timber.tag(TAG).i("Registered %s in CustomGameScanner (stableId=%d)", config.gameName, stableId)
        } else {
            Timber.tag(TAG).d("%s already registered in CustomGameScanner", config.gameName)
        }
    }

    /** 删除时从 CustomGameScanner 取消注册 */
    private fun unregisterFromCustomScanner(gameDir: File, gameId: String) {
        val folders = PrefManager.customGameManualFolders.toMutableSet()
        if (folders.remove(gameDir.absolutePath)) {
            PrefManager.customGameManualFolders = folders
            Timber.tag(TAG).i("Unregistered %s from CustomGameScanner", gameId)
        }
    }

    // --- 内部 ---

    private fun readEntryFromZip(zipFile: File, entryName: String): String? {
        return try {
            ZipFile(zipFile).use { zip ->
                val entry = zip.getEntry(entryName) ?: return null
                zip.getInputStream(entry).bufferedReader().readText()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "readEntryFromZip: failed reading '%s' from %s", entryName, zipFile.name)
            null
        }
    }

    private fun extractZip(zipFile: File, targetDir: File) {
        var extractedCount = 0
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
                    dest.setExecutable(true, false)
                    extractedCount++
                }
            }
        }
        Timber.tag(TAG).d("extractZip: extracted %d file(s) to %s", extractedCount, targetDir.path)
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
            bytes < 1024L * 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
            else -> "${"%.1f".format(bytes.toDouble() / (1024L * 1024L * 1024L))} GB"
        }
    }
}

sealed class ImportResult {
    data class Success(val config: GameConfig) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

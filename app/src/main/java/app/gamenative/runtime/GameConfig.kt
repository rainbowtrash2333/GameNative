package app.gamenative.runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 游戏配置，从 Game APK 的 game_config.json 反序列化。
 * Game APK 安装后将其写入 `/data/data/<pkg>/files/game_config.json`，
 * Runtime APK (通过 sharedUserId) 读取此文件。
 */
@Serializable
data class GameConfig(
    val gameId: String,
    val gameName: String,
    val version: String = "1.0.0",
    val packageName: String,
    val launchFile: String,
    val launchArgs: String = "",
    val containerConfig: ContainerConfig = ContainerConfig(),
    val envVars: Map<String, String> = emptyMap(),
    val estimatedSize: String = "0",
    val screenOrientation: String = "landscape",
)

@Serializable
data class ContainerConfig(
    val wineVersion: String = "proton-10.0-4",
    val emulator: String = "box64",
    val dxvkVersion: String = "2.7.1",
    val vkd3dVersion: String = "3.0.1",
    val renderer: String = "virgl",
    val resolution: String = "1280x720",
)

/**
 * 已安装游戏的运行时表示。
 * 由 GameManager 扫描生成，供 UI 展示和 GameLauncher 使用。
 */
data class InstalledGame(
    val gameId: String,
    val gameName: String,
    val gameVersion: String,
    val packageName: String,
    val config: GameConfig,
    val dataDir: File,
    val estimatedSize: String,
)

object GameConfigParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(configFile: File): GameConfig {
        val text = configFile.readText()
        return json.decodeFromString<GameConfig>(text)
    }
}

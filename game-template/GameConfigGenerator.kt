package com.yourapp.gameinit

import android.content.Context

/**
 * 根据 Game APK 的 AndroidManifest meta-data 生成 game_config.json。
 *
 * 游戏开发商只需在 AndroidManifest.xml 中设置 <meta-data> 标签,
 * 无需手写 JSON。如果 assets/game/ 中已存在 game_config.json,
 * 则直接使用, 不覆盖。
 */
object GameConfigGenerator {

    fun generate(context: Context): String {
        val pm = context.packageManager
        val pkg = context.packageName
        val info = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
        val meta = info.metaData ?: error("No meta-data found in AndroidManifest")

        val gameId = meta.getString("game_id") ?: pkg.substringAfterLast(".")
        val gameName = meta.getString("game_name") ?: gameId
        val launchFile = meta.getString("game_launch_file") ?: "game.exe"

        // 容器配置 (可通过 meta-data 覆盖, 否则使用默认值)
        val wineVersion = meta.getString("game_wine_version") ?: "proton-10.0-4"
        val emulator = meta.getString("game_emulator") ?: "box64"
        val dxvkVersion = meta.getString("game_dxvk_version") ?: "2.7.1"
        val vkd3dVersion = meta.getString("game_vkd3d_version") ?: "3.0.1"
        val renderer = meta.getString("game_renderer") ?: "virgl"
        val resolution = meta.getString("game_resolution") ?: "1280x720"

        return """
{
  "gameId": "$gameId",
  "gameName": "$gameName",
  "version": "${pkg.substringAfterLast(".")}",
  "packageName": "$pkg",
  "launchFile": "$launchFile",
  "launchArgs": "",
  "containerConfig": {
    "wineVersion": "$wineVersion",
    "emulator": "$emulator",
    "dxvkVersion": "$dxvkVersion",
    "vkd3dVersion": "$vkd3dVersion",
    "renderer": "$renderer",
    "resolution": "$resolution"
  },
  "envVars": {
    "DXVK_HUD": "0",
    "WINEDLLOVERRIDES": "d3d9=n;d3d11=n;dxgi=n",
    "WINEDEBUG": "-all"
  },
  "estimatedSize": "0",
  "screenOrientation": "landscape"
}
        """.trimIndent()
    }
}

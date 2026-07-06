package app.gamenative.runtime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

/**
 * Runtime APK 的入口 Activity。
 *
 * 与原 GameNative 的 MainActivity 不同:
 *   - 不使用 Hilt DI (减少依赖)
 *   - 不初始化 Steam/Epic/GOG 服务
 *   - 只显示游戏列表 UI
 *   - 通过 sharedUserId 发现已安装的游戏 APK
 *
 * AndroidManifest.xml 中将此 Activity 注册为 LAUNCHER。
 */
class RuntimeMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen()
                }
            }
        }
    }
}

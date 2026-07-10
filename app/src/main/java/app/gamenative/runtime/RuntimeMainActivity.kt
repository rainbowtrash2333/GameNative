package app.gamenative.runtime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import timber.log.Timber

/**
 * Runtime 入口 Activity。
 *
 * 与原 GameNative 的 MainActivity 不同:
 *   - 不使用 Hilt DI
 *   - 不初始化 Steam/Epic/GOG 服务
 *   - 只显示游戏列表 UI
 *   - 通过 sharedUserId 发现已安装的游戏 APK
 */
class RuntimeMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag("RuntimeMainActivity").i("onCreate")
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

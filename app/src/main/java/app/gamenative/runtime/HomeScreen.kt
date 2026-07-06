package app.gamenative.runtime

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val gameManager = remember { GameManager(context) }

    var games by remember { mutableStateOf(gameManager.scanInstalledGames()) }
    var isImporting by remember { mutableStateOf(false) }

    // SAF 文件选择器: 选择 ZIP 文件
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null || isImporting) return@rememberLauncherForActivityResult

        isImporting = true
        scope.launch(Dispatchers.IO) {
            try {
                val tempZip = File(context.cacheDir, "import_${System.currentTimeMillis()}.zip")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempZip.outputStream().use { output -> input.copyTo(output) }
                } ?: run {
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("无法读取文件")
                        isImporting = false
                    }
                    return@launch
                }

                val result = gameManager.importGame(tempZip.absolutePath)

                withContext(Dispatchers.Main) {
                    when (result) {
                        is ImportResult.Success -> {
                            snackbarHostState.showSnackbar("${result.config.gameName} 导入成功")
                            games = gameManager.scanInstalledGames()
                        }
                        is ImportResult.Error -> {
                            snackbarHostState.showSnackbar(result.message)
                        }
                    }
                    isImporting = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar("导入失败: ${e.message}")
                    isImporting = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GameNative") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        when {
            isImporting -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("正在导入游戏...")
                    }
                }
            }
            games.isEmpty() -> {
                EmptyState(
                    onImportClick = {
                        importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            else -> {
                GameList(
                    games = games,
                    contentPadding = paddingValues,
                    onPlayClick = { game ->
                        val launcher = GameLauncher(context)
                        Thread { launcher.launch(game) }.start()
                    },
                    onDeleteClick = { game ->
                        gameManager.deleteGame(game.gameId)
                        games = gameManager.scanInstalledGames()
                    },
                    onImportClick = {
                        importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onImportClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.VideogameAsset,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "还没有游戏",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "导入游戏 ZIP 包即可开玩",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onImportClick,
                modifier = Modifier.width(200.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("导入游戏")
            }
        }
    }
}

@Composable
private fun GameList(
    games: List<InstalledGame>,
    contentPadding: PaddingValues,
    onPlayClick: (InstalledGame) -> Unit,
    onDeleteClick: (InstalledGame) -> Unit,
    onImportClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "我的游戏 (${games.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onImportClick) {
                    Icon(Icons.Default.Add, contentDescription = "导入游戏")
                }
            }
        }

        items(games, key = { it.gameId }) { game ->
            GameCard(
                game = game,
                onPlayClick = { onPlayClick(game) },
                onDeleteClick = { onDeleteClick(game) },
            )
        }
    }
}

@Composable
private fun GameCard(
    game: InstalledGame,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.VideogameAsset,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.gameName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "v${game.gameVersion}  ·  ${game.estimatedSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除游戏",
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            IconButton(onClick = onPlayClick) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "开始游戏",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

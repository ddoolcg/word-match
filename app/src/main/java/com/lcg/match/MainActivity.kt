package com.lcg.match

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lcg.match.ui.theme.SpeechTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hosts the word evaluation flow and coordinates factor loading and speech matching.
 *
 * @author lei.chuguang Email:475825657@qq.com
 * @since 2026/9/4
 */
class MainActivity : ComponentActivity() {
    private val words = listOf("hello", "world", "who is this", "who are you")
    private var currentIndex by mutableIntStateOf(-1)
    private var statusMessage by mutableStateOf("正在加载测评数据...")
    private var isEvaluating by mutableStateOf(false)
    private var loaded by mutableStateOf(false)

    private var core: Core? = null
    private var loadJob: Job? = null

    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startEvaluation()
        } else {
            statusMessage = "需要麦克风权限才能开始测评"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpeechTheme {
                EvaluationScreen(
                    statusMessage,
                    words,
                    currentIndex,
                    loaded,
                    isEvaluating,
                    ::onEvaluationAction
                )
            }
        }
        load()
    }

    private fun load() {
        if (core == null) {
            loaded = false
            statusMessage = "正在加载测评数据..."
            loadJob = lifecycleScope.launch {
                try {
                    core = withContext(Dispatchers.IO) {
                        Core.Builder(this@MainActivity, words).build()
                    }
                    statusMessage = "准备完成，点击开始后依次朗读单词"
                } catch (_: CancellationException) {
                } catch (_: Exception) {
                    statusMessage = "测评数据加载失败，点击下方按钮重试"
                } finally {
                    loaded = true
                }
            }
        } else {
            statusMessage = "准备完成，点击开始后依次朗读单词"
            loaded = true
        }
    }

    override fun onDestroy() {
        loadJob?.cancel()
        core?.release()
        core = null
        super.onDestroy()
    }

    private fun onEvaluationAction() {
        if (isEvaluating) {
            stopEvaluation()
            return
        }
        if (core == null) {
            Toast.makeText(this, "模型加载失败，正在重试", Toast.LENGTH_SHORT).show()
            load()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startEvaluation()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startEvaluation() {
        if (words.isEmpty() || core == null) {
            isEvaluating = false
            return
        }
        isEvaluating = true
        currentIndex = 0
        statusMessage = "请朗读：${words[currentIndex]}"
        core!!.setMatchIndex(currentIndex)
        core!!.start { index, match ->
            if (index == currentIndex && match == 1) {
                if (++currentIndex < words.size) {
                    statusMessage = "请朗读：${words[currentIndex]}"
                    core!!.setMatchIndex(currentIndex)
                } else {
                    stopEvaluation()
                    statusMessage = "测评完成，全部单词已通过"
                }
            }
        }
    }

    private fun stopEvaluation() {
        core?.stop()
        currentIndex = -1
        isEvaluating = false
        statusMessage = "测评已停止"
    }
}

@Composable
private fun EvaluationScreen(
    statusMessage: String,
    words: List<String>,
    currentIndex: Int,
    canStart: Boolean,
    isEvaluating: Boolean,
    onAction: () -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "单词测评",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${currentIndex + 1} / ${words.size}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!canStart) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(text = statusMessage, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(words, key = { index, word -> "$index-$word" }) { index, word ->
                    WordRow(
                        index = index,
                        word = word,
                        isCurrent = index == currentIndex,
                        isCompleted = index < currentIndex,
                    )
                    if (index < words.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onAction,
                enabled = canStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = when {
                        isEvaluating -> "停止测评"
                        canStart -> "开始测评"
                        else -> "加载中..."
                    }
                )
            }
        }
    }
}

@Composable
private fun WordRow(index: Int, word: String, isCurrent: Boolean, isCompleted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isCompleted -> MaterialTheme.colorScheme.primary
                        isCurrent -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isCompleted) "✓" else "${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = if (isCompleted) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = word,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            text = when {
                isCompleted -> "已通过"
                isCurrent -> "请朗读"
                else -> "待测"
            },
            style = MaterialTheme.typography.labelLarge,
            color = when {
                isCompleted -> MaterialTheme.colorScheme.primary
                isCurrent -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

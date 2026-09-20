# match

`match` 是一个 Android 单词测评库。它从麦克风读取 PCM 音频，使用内置的原生识别器判断当前音频是否匹配指定单词，并把结果回调到
Android 主线程。

库的公开入口是 `com.lcg.match.Core`。`Core.Builder` 负责准备语音模型和单词对应的 `factor`，`Core`
负责录音、匹配和生命周期管理。

## 能力与限制

- `minSdk` 为 23，Java 编译版本为 11。
- 当前 AAR 只包含 `arm64-v8a` 原生库。`x86`、`x86_64` 或 `armeabi-v7a` 设备无法加载原生识别器。
- 默认采样率为 `16000 Hz`，匹配缓冲区约为 `200 ms`。
- 模型 ZIP 和 factor 服务默认从网络获取，首次初始化必须在后台线程执行。
- 模型缓存在 `context.cacheDir/model`，下载完成后通过 `model.txt` 标记。清除应用缓存会触发重新下载。

## 集成

### 作为工程模块依赖

在应用模块的 `build.gradle` 中添加依赖：

```groovy
dependencies {
    implementation 'io.github.ddoolcg:match:1.2'
}
```

## 权限与网络

应用需要声明以下权限。库的 manifest 已声明这些权限，宿主应用仍必须在 Android 6.0（API 23）及以上运行时申请麦克风权限：

```xml

<uses-permission android:name="android.permission.RECORD_AUDIO" /><uses-permission
android:name="android.permission.INTERNET" />
```

建议使用 `ActivityResultContracts.RequestPermission` 申请 `RECORD_AUDIO`，只有授权后才调用
`Core.start()`。

## 快速接入（Kotlin）

下面的流程与 `app` 模块中的 `MainActivity` 一致：先在 `Dispatchers.IO` 中初始化，再申请麦克风权限，最后按顺序切换待匹配单词。

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lcg.match.Core
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Demonstrates the match module lifecycle.
 *
 * @author lei.chuguang
 * @date 2026/9/11
 */
class MatchActivity : ComponentActivity() {
    private val words = listOf("hello", "world")
    private var core: Core? = null
    private var loadJob: Job? = null
    private var currentIndex = -1

    private val requestRecordAudio = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startMatching()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadJob = lifecycleScope.launch {
            try {
                core = withContext(Dispatchers.IO) {
                    Core.Builder(this@MatchActivity, words).apply {
                        // Optional: the default is 16,000 Hz.
                        setSampleRate(16_000)
                    }.build()
                }
                // Enable the start button or update the UI here.
            } catch (error: Exception) {
                // Model download, factor conversion, or recorder initialization failed.
                // Show a retry action instead of calling startMatching().
            }
        }
    }

    fun onStartClick() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startMatching()
        } else {
            requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startMatching() {
        val matcher = core ?: return
        if (words.isEmpty()) return

        currentIndex = 0
        matcher.setMatchIndex(currentIndex)
        matcher.start { index, result ->
            // The callback runs on the main thread.
            if (index != currentIndex) return@start

            when (result) {
                -1 -> Unit // No valid speech detected in this audio buffer.
                0 -> Unit  // Speech detected, but it does not match the word.
                1 -> {
                    if (++currentIndex < words.size) {
                        updatePrompt(words[currentIndex])
                        matcher.setMatchIndex(currentIndex)
                    } else {
                        matcher.stop()
                        updateFinished()
                    }
                }
            }
        }
    }

    fun onStopClick() {
        core?.stop()
        currentIndex = -1
    }

    override fun onDestroy() {
        loadJob?.cancel()
        core?.release()
        core = null
        super.onDestroy()
    }

    private fun updatePrompt(word: String) = Unit
    private fun updateFinished() = Unit
}
```

`Core.start()` 可以重复调用，但实例已经运行时不会创建新线程。切换目标单词时，先更新 `setMatchIndex()`
，再等待下一个回调返回 `1`。示例中的 `matcher.stop()` 会停止录音；组件销毁时还必须调用 `release()`
释放录音器、模型和原生识别器。

## API 参考

### `Core.Builder`

| 方法                          | 默认值                                                      | 说明                                                                |
|-----------------------------|----------------------------------------------------------|-------------------------------------------------------------------|
| `Builder(context, words)`   | 无                                                        | `words` 是待识别单词列表，不能为空。                                            |
| `setSampleRate(sampleRate)` | `16000`                                                  | 设置麦克风和语音模型采样率，单位 Hz。                                              |
| `setModelUrl(url)`          | `https://gitee.com/leicg/plus/raw/master/repo/model.zip` | 设置语音模型 ZIP 地址。                                                    |
| `setFactorUrl(url)`         | `https://ddoolcg-macth.ms.show/factor`                   | 设置单词到 factor 的转换服务地址。                                             |
| `build()`                   | 无                                                        | 下载或复用模型、调用 factor 服务并创建 `Core`。该方法执行网络和文件 I/O，可能抛出 `IOException`。 |

`build()` 会向 factor 服务发送一个 JSON 数组，例如：

```http
POST /factor
Content-Type: application/json; charset=utf-8

["hello", "world"]
```

服务必须返回 JSON 字符串数组，并保持与请求相同的顺序，例如：

```json
[
  "factor-for-hello",
  "factor-for-world"
]
```

### `Core`

| 方法                     | 说明                                            |
|------------------------|-----------------------------------------------|
| `setMatchIndex(index)` | 设置当前匹配的 factor 下标，从 `0` 开始。下标必须对应传入的 `words`。 |
| `start(listener)`      | 启动录音和匹配。匹配线程在后台运行，结果回调在主线程执行。                 |
| `pause()`              | 暂停读取和匹配。                                      |
| `resume()`             | 恢复读取和匹配。                                      |
| `stop()`               | 停止录音并等待匹配线程结束；没有运行中的线程时无操作。                   |
| `release()`            | 停止并释放全部资源。调用后不能继续使用当前实例。                      |

监听器签名为 `OnMatchListener.result(index, match)`：

| `match` | 含义                     |
|---------|------------------------|
| `-1`    | 当前音频缓冲区没有识别到有效语音。      |
| `0`     | 识别到了语音，但没有匹配当前 factor。 |
| `1`     | 匹配成功。                  |

监听器会对每个约 200 ms 的音频缓冲区回调一次，不只在成功时回调。通常只在 `match == 1` 时推进业务状态。

### 底层类型

`Model`、`Recognizer` 和 `Lib` 也位于公开包中，但它们绕过了 `Core.Builder` 的模型下载、factor 转换和
Android 录音流程。除非需要自行提供 PCM 数据或直接调用 JNA 原生接口，否则应优先使用 `Core`。

## 生命周期建议

推荐的资源关系如下：

```text
onCreate  -> Builder.build()（后台线程）
授权成功  -> setMatchIndex(0) -> start(listener)
切换单词  -> setMatchIndex(nextIndex)
停止      -> stop()
onDestroy -> stop/release()
```

`build()`、模型下载和 factor 请求不要放在主线程。`release()` 只能在不再使用该实例时调用；如果实例处于
`pause()` 状态，销毁前先调用 `resume()` 再 `stop()`，然后调用 `release()`。

## 常见问题

### 初始化失败

检查设备是否可以访问模型地址和 factor 地址，并查看 `IOException` 的具体原因。`build()`
同时依赖两个网络资源，任一请求失败都会初始化失败。失败后可以重新调用 `Builder.build()` 重试。

### `UnsatisfiedLinkError` 或识别器创建失败

确认运行设备是 `arm64-v8a`。当前模块只打包 `match/src/main/jniLibs/arm64-v8a/libmatch.so`，模拟器若为
`x86_64` 将无法加载。

### 没有回调成功

确认已经获得 `RECORD_AUDIO` 运行时权限，且 `setMatchIndex()` 使用的是有效下标。还要确认 factor
服务返回的数组顺序与 `words` 一致，并让用户朗读与当前单词对应的语音。

### 修改模型或 factor 地址

```kotlin
val matcher = withContext(Dispatchers.IO) {
    Core.Builder(context, words).apply {
        setModelUrl("https://example.com/model.zip")
        setFactorUrl("https://example.com/factor")
    }.build()
}
```

模型地址必须返回可解压的 ZIP 文件；factor 地址必须接受 JSON 数组并返回 JSON 字符串数组。生产环境建议使用
HTTPS，并为网络失败提供重试入口。

## 相关源码

- [Core.java](match/src/main/java/com/lcg/match/Core.java)：录音、匹配、回调和 Builder。
- [Recognizer.java](match/src/main/java/com/lcg/match/Recognizer.java)：JNA 原生识别器封装。
- [ModelDownload.java](match/src/main/java/com/lcg/match/utils/ModelDownload.java)：模型下载、缓存和解压。
- [Factor.java](match/src/main/java/com/lcg/match/utils/Factor.java)：单词到 factor 的 HTTP 转换。
- [app/MainActivity.kt](app/src/main/java/com/lcg/match/MainActivity.kt)：完整的 Compose 接入示例。

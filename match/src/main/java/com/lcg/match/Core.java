package com.lcg.match;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.lcg.match.utils.Factor;
import com.lcg.match.utils.ModelDownload;

import org.json.JSONArray;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 采集麦克风音频，并将音频与已配置的 factor 进行匹配。
 *
 * <p>音频在工作线程中读取，匹配回调会投递到主线程，调用方可以在
 * {@link OnMatchListener} 中直接更新 Android 界面。</p>
 *
 * @author lei.chuguang Email:475825657@qq.com
 * @since 2026/9/9
 */
public class Core {
    /**
     * 供识别器使用的语音模型。
     */
    private final Model model;
    /**
     * 与 Builder 中传入单词对应的 factor 列表。
     */
    private final List<String> factors;
    /**
     * 对每个音频缓冲区执行匹配的原生识别器。
     */
    private final Recognizer recognizer;
    /**
     * 每个麦克风音频缓冲区的时长，单位为秒。
     */
    private final static float BUFFER_SIZE_SECONDS = 0.2f;
    /**
     * 每次从录音器读取的 PCM 采样点数量。
     */
    private final int bufferSize;
    /**
     * 单声道、16 位 PCM 麦克风录音器。
     */
    private final AudioRecord recorder;
    /**
     * 当前执行匹配的线程；停止匹配后为 {@code null}。
     */
    private MyThread thread;
    /**
     * 当前选中的匹配 factor 下标。
     */
    private int index;
    private final Object lock = new Object();
    private volatile boolean paused = false;

    /**
     * 使用已下载的语音模型创建匹配器。
     *
     * @param path       语音模型路径
     * @param sampleRate 麦克风采样率，单位为 Hz
     * @param factors    识别器支持的 factor 列表
     * @throws IOException 模型或麦克风录音器初始化失败时抛出
     */
    @SuppressLint("MissingPermission")
    Core(String path, int sampleRate, List<String> factors) throws IOException {
        this.model = new Model(path);
        this.factors = factors;
        if (factors == null || factors.isEmpty()) {
            this.recognizer = new Recognizer(model, sampleRate);
        } else {
            JSONArray jsonArray = new JSONArray();
            for (String factor : factors) {
                jsonArray.put(factor);
            }
            this.recognizer = new Recognizer(model, sampleRate, jsonArray.toString());
        }
        bufferSize = Math.round(sampleRate * BUFFER_SIZE_SECONDS);
        recorder = new AudioRecord(
                AudioSource.VOICE_RECOGNITION, sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2);
        if (recorder.getState() == AudioRecord.STATE_UNINITIALIZED) {
            recorder.release();
            throw new IOException("打开麦克风失败");
        }
    }

    /**
     * 设置后续音频匹配所使用的 factor 下标。
     *
     * @param index factor 列表中的从零开始下标
     */
    public void setMatchIndex(int index) {
        this.index = index;
    }

    /**
     * 启动麦克风采集和音频匹配。
     *
     * <p>如果匹配已经启动，再次调用此方法不会创建新的线程。</p>
     *
     * @param listener 匹配成功时接收通知的监听器，回调在主线程执行
     */
    public void start(OnMatchListener listener) {
        if (thread != null)
            return;
        thread = new MyThread(listener);
        thread.start();
    }

    /**
     * 唤醒
     */
    public void resume() {
        synchronized (lock) {
            paused = false;
            lock.notify();
        }
    }

    /**
     * 暂停
     */
    public void pause() {
        synchronized (lock) {
            paused = true;
        }
    }

    /**
     * 停止麦克风采集，并等待匹配线程结束。
     * 如果当前没有执行匹配，则此方法不执行任何操作。
     */
    public void stop() {
        if (thread == null)
            return;

        try {
            thread.interrupt();
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        thread = null;
    }

    /**
     * 停止匹配并释放录音器、语音模型和原生识别器资源。
     * 此方法返回后不能继续使用当前实例。
     */
    public void release() {
        stop();
        try {
            recorder.release();
        } catch (Exception ignored) {
        }
        model.close();
        recognizer.close();
    }

    /**
     * 在后台线程中阻塞读取麦克风数据并执行原生匹配。
     *
     * @author lei.chuguang Email:475825657@qq.com
     * @since 2026/9/9
     */
    private final class MyThread extends Thread {
        /**
         * 接收匹配成功通知的监听器。
         */
        private final OnMatchListener listener;
        /**
         * 将匹配结果投递到 Android 主线程。
         */
        private final Handler handler = new Handler(Looper.getMainLooper());
        /**
         * 在中断线程前设置，用于取消尚未执行的回调。
         */
        private volatile boolean stopped = false;

        /**
         * 创建匹配工作线程。
         *
         * @param listener 匹配成功时调用的监听器
         */
        MyThread(OnMatchListener listener) {
            this.listener = listener;
        }

        /**
         * 先标记线程停止，再中断可能阻塞的录音读取操作。
         */
        public void interrupt() {
            stopped = true;
            super.interrupt();
        }

        /**
         * 持续读取麦克风缓冲区，直到线程被中断。
         */
        @Override
        public void run() {
            recorder.startRecording();
            if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED) {
                recorder.stop();
            }
            short[] buffer = new short[bufferSize];
            while (!interrupted()) {
                synchronized (lock) {
                    while (paused) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            SystemClock.sleep(10);
                        }
                    }
                }
                //
                if (!stopped) {
                    int n = recorder.read(buffer, 0, bufferSize);
                    if (n >= 0 && !stopped) {
                        int i = index;
                        String factor = factors.get(i);
                        int match = recognizer.match(factor, buffer, n);
                        handler.post(() -> {
                            if (!stopped && index == i)
                                listener.result(i, match);
                        });
                    }
                }
            }
            recorder.stop();
        }
    }

    /**
     * 在主线程接收匹配成功通知。
     *
     * @author lei.chuguang Email:475825657@qq.com
     * @since 2026/9/9
     */
    public interface OnMatchListener {
        /**
         * 选中的 factor 与当前音频匹配成功时调用。
         *
         * @param index 匹配成功的 factor 下标
         * @param match 识别结果，值为 {@code -1} 未识别到有效音频，值为 {@code 0} 表示匹配失败，值为 {@code 1} 表示匹配成功
         */
        void result(int index, int match);
    }

    /**
     * 用于配置并创建 {@link Core} 实例的构建器。
     *
     * <p>{@link #build()} 会从应用缓存目录下载或复用语音模型，并将配置的
     * 单词转换为识别器使用的 factor。</p>
     *
     * @author lei.chuguang Email:475825657@qq.com
     * @since 2026/9/9
     */
    public static class Builder {
        /**
         * 用于获取应用缓存目录的 Android 上下文。
         */
        private final Context context;
        /**
         * 待识别并转换为 factor 的单词列表。
         */
        private final List<String> words;
        /**
         * 麦克风和语音模型使用的采样率，单位为 Hz。
         */
        private int sampleRate = 16000;
        /**
         * 语音模型 ZIP 文件下载地址。
         */
        private String modelUrl = "https://gitee.com/leicg/plus/raw/master/repo/model.zip";
        /**
         * 将单词转换为识别器 factor 的服务地址。
         */
        private String factorUrl = "https://ddoolcg-macth.ms.show/factor";

        /**
         * 创建一个包含待识别单词的构建器。
         *
         * @param context 用于访问缓存目录的 Android 上下文
         * @param words   待识别的单词列表
         */
        public Builder(Context context, List<String> words) {
            this.words = words;
            this.context = context;
        }

        /**
         * 设置麦克风和语音模型的采样率。
         *
         * @param sampleRate 采样率，单位为 Hz
         */
        public Builder setSampleRate(int sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }

        /**
         * 设置语音模型 ZIP 文件下载地址。
         *
         * @param url 语音模型下载地址
         */
        public Builder setModelUrl(String url) {
            this.modelUrl = url;
            return this;
        }

        /**
         * 设置单词转换为 factor 的服务地址。
         *
         * @param url factor 转换服务地址
         */
        public Builder setFactorUrl(String url) {
            this.factorUrl = url;
            return this;
        }

        /**
         * 下载所需资源并创建语音匹配器。
         *
         * @return 已初始化的语音匹配器
         * @throws IOException 单词列表为空或资源初始化失败时抛出
         */
        public Core build() throws IOException {
            if (words == null || words.isEmpty()) throw new IOException("words is empty");
            String s = context.getCacheDir().getPath() + File.separator;
            String path = ModelDownload.load(s, modelUrl);
            List<String> list = Factor.transform(factorUrl, words);
            return new Core(path, sampleRate, list);
        }
    }
}

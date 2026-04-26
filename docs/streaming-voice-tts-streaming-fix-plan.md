# 语音流式输出（TTS byte[] chunk 推送）纠错与重构方案

> 关联提交：`d34b9ea feat(voice): add backend voice ASR/TTS streaming pipeline`
> 关联架构：`docs/streaming-voice-architecture.md`
> 状态：方案已确认（仅文档，未改代码），待按本方案实施
> 决策点：双向流式（LLM 边出文本边送 TTS）+ 默认 `MP3_22050HZ_MONO_256KBPS` + 旧测试先 `@Disabled`

---

## 0. 阅读指引（先看这一节，再决定要不要往下读）

- §1：当前 bug 的**根因诊断**——一句话：用错了 SDK 类。
- §2：目标与不动的边界（**不破坏前端契约**）。
- §3：方案选型对比与最终选择（双向流）。
- §4：**逐文件改动清单**（每个改动都标了原因 + 关键代码片段，足够照抄落地）。
- §5：状态机与异常路径（cancel / interrupt / error 怎么走）。
- §6：实施步骤（按顺序做，避免半路 broken）。
- §7：联调与验证清单（手测 + 服务端日志关键字）。
- §8：风险、回滚、后续优化。
- §9：常见踩坑 FAQ。

---

## 1. 根因诊断

### 1.1 现象

前端通过 WebSocket 拿到的语音二进制流，**只在 LLM 文本完成后、TTS 整段合成完毕、才一次性收到一大块** `BinaryMessage`，并没有像设计预期那样**逐 chunk 推送**。

### 1.2 代码层定位

`AliyunTtsProvider.synthesizeStream(...)` 当前用的是：

```java
import com.alibaba.dashscope.audio.tts.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.tts.SpeechSynthesizer;   // ← 老类，专给 Sambert 系列
...
synthesizer.streamCall(param).subscribe(
    result -> emitAudioChunk(...),
    error -> ...,
    () -> {
        // 关键的 fallback：检测到没有任何 frame，从 synthesizer.getAudioData() 一次性吐全量
        if (skipped) skipped = !emitBufferedAudioData(...);
        listener.onCompleted(skipped);
    }
);
```

**这个老类（`com.alibaba.dashscope.audio.tts.SpeechSynthesizer`）对 cosyvoice 模型来说，`streamCall(...)` 实际上不会逐 frame emit `getAudioFrame()`；只会在最后 `onComplete` 时通过 `getAudioData()` 拿到完整音频。** 所以你看到的现象就是 fallback 分支`emitBufferedAudioData()` 一次性把全量送给前端——表面上"只收到一次"。

### 1.3 SDK 层的本质区别

阿里灵积 Java SDK 中有**两套** TTS 类，命名一致但包不同：

| 包路径                                                            | 类名                  | 适用模型                                                  | 是否真流式（chunk 推送）                            |
| ----------------------------------------------------------------- | --------------------- | --------------------------------------------------------- | --------------------------------------------------- |
| `com.alibaba.dashscope.audio.tts.SpeechSynthesizer`               | 老 SpeechSynthesizer  | Sambert 系列                                              | ❌ 对 cosyvoice 不真正流式                          |
| `com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer`             | 新 SpeechSynthesizer  | **cosyvoice-v1 / v2 / v3-flash / v3-plus / v3.5-...**     | ✅ 通过 WebSocket，逐 frame `result.getAudioFrame()` |
| `com.alibaba.dashscope.audio.http_tts.HttpSpeechSynthesizer`      | HTTP 版               | cosyvoice-v3 系列（HTTP 接口）                            | ✅ 但是是 HTTP/SSE，非 WebSocket                    |

我们项目当前 `voice.tts.model = cosyvoice-v1`，**但用的是上表第一行的老类**，所以根本不可能流式。

### 1.4 阿里官方推荐的 cosyvoice 流式调用形态

```java
import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;          // 结果对象（共用）
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;   // ← 注意是 ttsv2 下的枚举
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;

ResultCallback<SpeechSynthesisResult> callback = new ResultCallback<>() {
    @Override public void onEvent(SpeechSynthesisResult result) {
        if (result.getAudioFrame() != null) {
            // 这里才是真·流式 chunk
        }
    }
    @Override public void onComplete() { /* 合成结束 */ }
    @Override public void onError(Exception e) { /* 错误 */ }
};

SpeechSynthesisParam param = SpeechSynthesisParam.builder()
        .apiKey(apiKey)
        .model("cosyvoice-v1")
        .voice("longxiaochun")
        .format(SpeechSynthesisAudioFormat.MP3_22050HZ_MONO_256KBPS)
        .build();

SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, callback);

// 形态 A：单向流（一次给全量文本，逐 chunk 拿音频）
synthesizer.call("今天天气怎么样？");          // 非阻塞，立即返回 null

// 形态 B：双向流（多段文本，边给边出音频）—— 我们采用这个
for (String delta : llmDeltas) {
    synthesizer.streamingCall(delta);
}
synthesizer.streamingComplete();              // 阻塞等 onComplete/onError

// 完事后释放底层 WebSocket
synthesizer.getDuplexApi().close(1000, "bye");
```

> 关键点：用完**必须**调用 `synthesizer.getDuplexApi().close(1000, "bye")` 释放 WebSocket，否则会泄漏长连接。**每次新合成都要 new 一个 `SpeechSynthesizer`，不能复用。**

---

## 2. 目标与边界

### 2.1 必须达成

1. **TTS 端真正按 chunk 流式推送**：从 cosyvoice 拿到的每一个 `audioFrame` 都立即写到 WebSocket 二进制帧，前端 MSE/MediaSource 能逐块播放，**首包延迟显著下降**。
2. **打通 LLM → TTS 双向流**：LLM 文本 delta 一边到达，一边喂给 TTS（`streamingCall`），不再等 `onTextCompleted` 才整段合成。这是用户在第 1 个问题选择的 `duplex` 方案。
3. **保持现有事件契约不变**（前端不改）：
   - WebSocket JSON 事件：`session_state` / `turn_state` / `asr_text` / `tts_state` / `error` —— 字段、类型、时序保持原样。
   - WebSocket 二进制帧：仍是裸 MP3 字节。
   - SSE 文本事件：`turn_state` / `text_delta` / `text_end` / `error` 不变。
4. **失败/取消/打断的状态机维持原语义**（`completed` / `interrupted` / `failed`），并保证 WebSocket 资源（DashScope duplex API）正确释放。

### 2.2 不动的部分

- ASR 部分（`AliyunAsrProvider`、`paraformer-realtime-v1`）暂不动。本次只修 TTS。
- WebSocket 握手 / 鉴权 / 心跳 / session 注册逻辑不动。
- `VoiceTextStreamService`（SSE 文本通道）不动。
- 前端契约不动。

### 2.3 可调的配置

- `jc-ai-agent.voice.tts.output-format`：扩展为允许直接写阿里枚举名（如 `MP3_22050HZ_MONO_256KBPS`），同时兼容 `mp3 / pcm / wav` 简写。
- `jc-ai-agent.voice.tts-audio-mime-type`：保持现状 `audio/mpeg`，PCM 模式时由代码自动覆盖为 `audio/pcm`（详见 §4.6）。

---

## 3. 方案选型

### 3.1 三种可选

| 方案                                     | 首包延迟         | 改动量 | 复杂度 | 备注                                            |
| ---------------------------------------- | ---------------- | ------ | ------ | ----------------------------------------------- |
| A. 仅切换到 ttsv2 单向流（`call`）       | 较高（等全文本） | 小     | 低     | 只修 Provider，业务流不改                       |
| B. 切换到 ttsv2 双向流（`streamingCall`）| **最低**         | 中     | 中     | Provider + Orchestrator 都改，本次采用          |
| C. 用 Flowable `streamingCallAsFlowable` | 最低             | 中     | 中     | 等价于 B，但需引入 RxJava 风格代码风格不统一    |

**最终选择：B**（与第 1 个问题的回答一致）。理由：
- 现有架构里 LLM 已经是流式的（`streamResult.contentStream().subscribe(...)`），完全可以一边收 delta 一边打到 TTS。
- 不依赖 RxJava 在业务层暴露，回调接口更直观、可测试。
- cosyvoice 的双向流支持"未完整句子的服务端缓冲 + 自动断句合成"，无需我们自己做断句。
- 只要 `streamingComplete()` 调到，服务端会把缓冲里残余的不完整句子也合成掉，不会丢字。

### 3.2 时序图（最终方案 B）

```
Client                Server (Orchestrator)            DashScope ttsv2.SpeechSynthesizer
  |                      |                                   |
  |--turn.start--------->|                                   |
  |                      |--openTtsSession (new + connect)-->|
  |<-turn_state(input)---|                                   |
  |  audio chunks ...    |--ASR partial / final-->LLM(stream)
  |                      |                                   |
  |                      |  LLM delta #1: "今天"              |
  |                      |--streamingCall("今天")------------>|
  |                      |  LLM delta #2: "天气"              |
  |                      |--streamingCall("天气")------------>|
  |                      |    ... 期间 onEvent 触发，audio frame 持续回调
  |<-tts_state(start)----|<--onEvent(first audioFrame)-------|
  |<-binary chunk--------|<--onEvent(audioFrame)-------------|
  |<-binary chunk--------|<--onEvent(audioFrame)-------------|
  |                      |                                   |
  |                      |  LLM 流结束 onCompleted            |
  |                      |--streamingComplete()-(阻塞)------>|
  |                      |<--剩余 onEvent + onComplete-------|
  |<-binary chunk--------|                                   |
  |<-tts_state(end)------|                                   |
  |<-turn_state(ended)---|--getDuplexApi().close()---------->|
```

---

## 4. 逐文件改动清单

### 4.1 `pom.xml` ——（**可选**）升级 dashscope-sdk-java 版本

当前 `2.19.1` 已包含 `audio.ttsv2.SpeechSynthesizer`（该包从 2.18.x 起就有），**功能上不需要升**。
但如果未来想要 SSML 支持，建议升到 `>= 2.20.3`；想要 Opus 格式，需要 `>= 2.21.0`。

**本次默认不升**。如果联调时发现 cosyvoice-v1 在 2.19.1 上 `getDuplexApi()` 行为有 bug，再考虑升 `2.21.0`。

```xml
<!-- 建议但非必须 -->
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>dashscope-sdk-java</artifactId>
    <version>2.21.0</version>  <!-- 原 2.19.1 -->
    <exclusions>
        <exclusion>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

---

### 4.2 `voice/config/VoiceProperties.java` —— 扩展 TTS 配置

新增 `outputFormat` 的解析能力（允许直接配 ttsv2 枚举名）。

```java
@Data
public static class TtsProperties {
    private String provider = "aliyun";
    private String baseUrl;
    private String apiKey;
    private String voice = "longxiaochun";
    private String model = "cosyvoice-v1";
    /**
     * 接受两种写法：
     *  1) 简写：mp3 / pcm / wav         —— 走默认采样率（mp3 → 22050, pcm/wav → 22050 16bit）
     *  2) 全量枚举：MP3_22050HZ_MONO_256KBPS / PCM_22050HZ_MONO_16BIT 等（区分大小写不敏感）
     */
    private String outputFormat = "mp3";

    /** 流式输出的速率（可选）。默认 1.0。范围 0.5~2.0。 */
    private Float speechRate;
    /** 音调（可选）。默认 1.0。范围 0.5~2.0。 */
    private Float pitchRate;
    /** 音量（可选）。默认 50。范围 0~100。 */
    private Integer volume;
}
```

---

### 4.3 `voice/provider/tts/TtsProvider.java` —— **接口要扩展**

为支持双向流（边出文本边送 TTS），接口需要增加 `appendText / complete` 两个方法。
不再是"一次性给全量文本"。

```java
public interface TtsProvider {

    /**
     * 创建一个 TTS 流会话，但**不立即提交文本**。
     * 调用方稍后通过 {@link TtsSynthesis#appendText(String)} 增量送文本，
     * 通过 {@link TtsSynthesis#complete()} 标记文本流结束。
     *
     * 监听器在底层产生 audioFrame 时被回调。
     */
    TtsSynthesis openStream(VoiceTurnContext turnContext, TtsListener listener);

    interface TtsSynthesis {
        /** 增量提交一段文本；底层会自动断句缓冲。可多次调用。线程安全。 */
        void appendText(String textDelta);

        /** 标记文本流结束。该方法会触发底层把剩余缓冲合成完毕。线程安全、幂等。 */
        void complete();

        /** 主动取消（中断打断时用）。会立刻丢弃后续音频帧并释放底层连接。线程安全、幂等。 */
        void cancel();

        /** 释放底层 WebSocket 资源，等价于 cancel + 清理。线程安全、幂等。 */
        void close();
    }

    interface TtsListener {
        void onStart();
        void onAudioChunk(byte[] audioChunk);
        void onCompleted(boolean skipped);
        void onError(Throwable error);
    }
}
```

> 兼容性说明：原接口 `synthesizeStream(turnContext, text, listener)` **保留**为 default 方法（可选），实现里就是 `openStream` + `appendText(text)` + `complete()`，方便老代码逐步迁移。本次直接全部走 `openStream`。

---

### 4.4 `voice/provider/tts/AliyunTtsProvider.java` —— **核心重写**

完整伪代码如下，可以直接据此实现：

```java
package fun.javierchen.jcaiagentbackend.voice.provider.tts;

import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;
import fun.javierchen.jcaiagentbackend.voice.config.VoiceProperties;
import fun.javierchen.jcaiagentbackend.voice.session.VoiceTurnContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class AliyunTtsProvider implements TtsProvider {

    private final VoiceProperties voiceProperties;

    @Value("${spring.ai.dashscope.api-key:}")
    private String defaultDashScopeApiKey;

    @Override
    public TtsSynthesis openStream(VoiceTurnContext turnContext, TtsListener listener) {
        String apiKey = StringUtils.firstNonBlank(
                voiceProperties.getTts().getApiKey(),
                defaultDashScopeApiKey
        );
        if (StringUtils.isBlank(apiKey)) {
            log.info("Skipping TTS streaming because provider credentials are not configured: turnId={}",
                     turnContext.getTurnId());
            listener.onCompleted(true);
            return NoopTtsSynthesis.INSTANCE;
        }

        SpeechSynthesisAudioFormat format = resolveAudioFormat();

        SpeechSynthesisParam.SpeechSynthesisParamBuilder<?, ?> builder = SpeechSynthesisParam.builder()
                .apiKey(apiKey)
                .model(StringUtils.defaultIfBlank(voiceProperties.getTts().getModel(), "cosyvoice-v1"))
                .format(format);
        String voice = StringUtils.trimToNull(voiceProperties.getTts().getVoice());
        if (voice != null) builder.voice(voice);
        if (voiceProperties.getTts().getSpeechRate() != null) builder.speechRate(voiceProperties.getTts().getSpeechRate());
        if (voiceProperties.getTts().getPitchRate() != null)  builder.pitchRate(voiceProperties.getTts().getPitchRate());
        if (voiceProperties.getTts().getVolume() != null)     builder.volume(voiceProperties.getTts().getVolume());

        SpeechSynthesisParam param = builder.build();

        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicInteger emittedChunks = new AtomicInteger(0);

        ResultCallback<SpeechSynthesisResult> callback = new ResultCallback<>() {
            @Override
            public void onEvent(SpeechSynthesisResult result) {
                if (closed.get() || result == null || result.getAudioFrame() == null) return;
                ByteBuffer buf = result.getAudioFrame().asReadOnlyBuffer();
                if (!buf.hasRemaining()) return;
                byte[] chunk = new byte[buf.remaining()];
                buf.get(chunk);
                if (started.compareAndSet(false, true)) listener.onStart();
                emittedChunks.incrementAndGet();
                listener.onAudioChunk(chunk);
            }

            @Override
            public void onComplete() {
                if (closed.get()) return;
                listener.onCompleted(emittedChunks.get() == 0);
                completed.set(true);
            }

            @Override
            public void onError(Exception e) {
                if (closed.get()) return;
                listener.onError(e);
            }
        };

        SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, callback);
        log.info("Started Aliyun TTS bidirectional stream: turnId={}, model={}, voice={}, format={}",
                turnContext.getTurnId(), param.getModel(), voice, format.getValue());
        return new BidiTtsSynthesis(synthesizer, listener, closed, completed, started, emittedChunks);
    }

    private SpeechSynthesisAudioFormat resolveAudioFormat() {
        String configured = StringUtils.upperCase(StringUtils.trimToEmpty(voiceProperties.getTts().getOutputFormat()));
        if (configured.isEmpty() || "MP3".equals(configured)) {
            return SpeechSynthesisAudioFormat.MP3_22050HZ_MONO_256KBPS;  // 默认
        }
        if ("PCM".equals(configured))  return SpeechSynthesisAudioFormat.PCM_22050HZ_MONO_16BIT;
        if ("WAV".equals(configured))  return SpeechSynthesisAudioFormat.WAV_22050HZ_MONO_16BIT;
        // 全量枚举名（如 MP3_24000HZ_MONO_256KBPS）
        try {
            return SpeechSynthesisAudioFormat.valueOf(configured);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown TTS outputFormat '{}', falling back to MP3_22050HZ_MONO_256KBPS",
                     configured);
            return SpeechSynthesisAudioFormat.MP3_22050HZ_MONO_256KBPS;
        }
    }

    /** 双向流 handle。 */
    private static final class BidiTtsSynthesis implements TtsSynthesis {
        private final SpeechSynthesizer synthesizer;
        private final TtsListener listener;
        private final AtomicBoolean closed;
        private final AtomicBoolean completed;
        private final AtomicBoolean started;
        private final AtomicInteger emittedChunks;

        BidiTtsSynthesis(SpeechSynthesizer synthesizer, TtsListener listener,
                         AtomicBoolean closed, AtomicBoolean completed,
                         AtomicBoolean started, AtomicInteger emittedChunks) {
            this.synthesizer = synthesizer;
            this.listener = listener;
            this.closed = closed;
            this.completed = completed;
            this.started = started;
            this.emittedChunks = emittedChunks;
        }

        @Override
        public synchronized void appendText(String textDelta) {
            if (closed.get() || StringUtils.isEmpty(textDelta)) return;
            try {
                synthesizer.streamingCall(textDelta);
            } catch (Exception e) {
                if (!closed.get()) listener.onError(e);
            }
        }

        @Override
        public synchronized void complete() {
            if (closed.get() || completed.get()) return;
            try {
                synthesizer.streamingComplete();   // 阻塞，触发剩余 onEvent + onComplete
            } catch (Exception e) {
                if (!closed.get()) listener.onError(e);
            } finally {
                releaseDuplex();
            }
        }

        @Override public void cancel() { close(); }

        @Override
        public synchronized void close() {
            if (!closed.compareAndSet(false, true)) return;
            // 没回调过 onCompleted 就补一次，让上层走完状态机
            if (!completed.get()) listener.onCompleted(emittedChunks.get() == 0);
            releaseDuplex();
        }

        private void releaseDuplex() {
            try {
                if (synthesizer.getDuplexApi() != null) {
                    synthesizer.getDuplexApi().close(1000, "bye");
                }
            } catch (Exception ignore) { /* 已关闭等错误吞掉 */ }
        }
    }

    private static final class NoopTtsSynthesis implements TtsSynthesis {
        static final NoopTtsSynthesis INSTANCE = new NoopTtsSynthesis();
        @Override public void appendText(String textDelta) { /* no-op */ }
        @Override public void complete()                   { /* no-op */ }
        @Override public void cancel()                     { /* no-op */ }
        @Override public void close()                      { /* no-op */ }
    }
}
```

---

### 4.5 `voice/service/VoiceTurnOrchestrator.java` —— **流程改造**

旧流程（一次性）：

```
LLM stream → onTextDelta → 累加到 assistantBuffer → onTextCompleted → 整段送 TTS
```

新流程（双向流）：

```
processing 阶段：
  - LLM 一来 delta → 同步 publish 到 SSE（不变）
  - 同时 ttsSynthesis.appendText(delta)   ← 新增
  - 第一次出 delta 时 publishTurnState(output) + tts_state(start)（保持原语义）

LLM onCompleted：
  - ttsSynthesis.complete()                ← 触发剩余合成 + 阻塞等 onComplete
  - 持久化 assistant message（保持现有逻辑）

TTS onCompleted（来自 listener）：
  - tts_state(end) + completeTurn()（保持原状态机）

interrupt / fail：
  - cleanupTurnResources() 里 ttsSynthesis.cancel() 或 close()
```

关键变更点（伪代码 diff）：

```java
// 1) startTurn 之后、LLM 还没开始之前，提前打开 TTS 通道
public VoiceTurnContext startTurn(VoiceSessionContext sessionContext, VoiceTurnStartCommand command) {
    ... // 原逻辑不变
    return turnContext;
}

// 2) commitTranscript -> executeChatTurn：在 subscribe 之前 openStream
private void executeChatTurn(VoiceSessionContext sessionContext, VoiceTurnContext turnContext) {
    var chatSession = ...
    studyFriendChatService.appendUserMessage(...)
    boolean effectiveWebSearchEnabled = ...
    var streamResult = studyFriend.doChatWithRAGStream(...);

    // ★ 提前打开 TTS bidi 流
    TtsProvider.TtsSynthesis tts = ttsProvider.openStream(turnContext, buildTtsListener(sessionContext, turnContext));
    turnContext.setTtsSynthesis(tts);

    Disposable disposable = streamResult.contentStream().subscribe(
            chunk -> onTextDelta(sessionContext, turnContext, chunk),
            error -> {
                tts.cancel();
                failTurn(sessionContext, turnContext, "VOICE_TEXT_STREAM_FAILED", safeMessage(error), false);
            },
            () -> onTextCompleted(sessionContext, turnContext, streamResult.webSearchUsed(), streamResult.sources())
    );
    turnContext.setLlmSubscription(disposable);
}

// 3) onTextDelta：除了 publish 到 SSE，还要 appendText 到 TTS
private void onTextDelta(VoiceSessionContext sessionContext, VoiceTurnContext turnContext, String chunk) {
    if (turnContext.isEnded() || StringUtils.isBlank(chunk)) return;
    if (!turnContext.isOutputStarted()) {
        turnContext.setOutputStarted(true);
        publishTurnState(sessionContext, turnContext, TurnState.output, null);
    }
    turnContext.appendAssistantText(chunk);
    voiceTextStreamService.publish(turnContext.getTurnId(), "text_delta", new TextDeltaPayload(chunk));

    // ★ 同时把 delta 喂给 TTS
    TtsProvider.TtsSynthesis tts = turnContext.getTtsSynthesis();
    if (tts != null) tts.appendText(chunk);
}

// 4) onTextCompleted：不再 startTts，而是 tts.complete()
private void onTextCompleted(VoiceSessionContext sessionContext, VoiceTurnContext turnContext,
                             Boolean webSearchUsed, List<StudyFriendSource> sources) {
    if (turnContext.isEnded()) return;
    turnContext.setTextCompleted(true);
    studyFriendChatService.appendAssistantMessage(...);  // 不变
    voiceTextStreamService.publish(turnContext.getTurnId(), "text_end", new TextEndPayload("completed"));

    // ★ 通知 TTS：文本流结束，把缓冲合成掉
    TtsProvider.TtsSynthesis tts = turnContext.getTtsSynthesis();
    if (tts != null) tts.complete();
    // 不再调 startTts(...)，TTS listener 自己负责发 tts_state(end) + completeTurn()
}

// 5) buildTtsListener：抽出来的 TTS 监听器（原 startTts 里那段，原样保留语义）
private TtsProvider.TtsListener buildTtsListener(VoiceSessionContext sessionContext, VoiceTurnContext turnContext) {
    return new TtsProvider.TtsListener() {
        @Override public void onStart() {
            if (!turnContext.isOutputStarted()) {
                turnContext.setOutputStarted(true);
                publishTurnState(sessionContext, turnContext, TurnState.output, null);
            }
            voiceSessionRegistry.sendEvent(sessionContext,
                    envelope(sessionContext, turnContext.getTurnId(), "tts_state",
                            new TtsStatePayload("start", voiceProperties.getTtsAudioMimeType(), false)));
        }
        @Override public void onAudioChunk(byte[] audioChunk) {
            voiceSessionRegistry.sendBinary(sessionContext, audioChunk);
        }
        @Override public void onCompleted(boolean skipped) {
            turnContext.setAudioCompleted(true);
            voiceSessionRegistry.sendEvent(sessionContext,
                    envelope(sessionContext, turnContext.getTurnId(), "tts_state",
                            new TtsStatePayload("end", voiceProperties.getTtsAudioMimeType(), skipped)));
            completeTurn(sessionContext, turnContext);
        }
        @Override public void onError(Throwable error) {
            failTurn(sessionContext, turnContext, "VOICE_TTS_FAILED", safeMessage(error), false);
        }
    };
}

// 6) cleanupTurnResources 里把 tts.close()/cancel() 行为对齐
private void cleanupTurnResources(VoiceTurnContext turnContext) {
    Disposable llm = turnContext.getLlmSubscription();
    if (llm != null) llm.dispose();
    if (turnContext.getAsrSession() != null) { turnContext.getAsrSession().close(); turnContext.setAsrSession(null); }
    if (turnContext.getTtsSynthesis() != null) {
        turnContext.getTtsSynthesis().cancel();   // 中断/失败：不再等 streamingComplete
        turnContext.setTtsSynthesis(null);
    }
}
```

> ⚠️ 注意：`tts.complete()` 是阻塞的（`streamingComplete` 内部会 latch 等 onComplete）。它**当前发生在 LLM Reactor 的 onComplete 回调线程上**——这个线程不是 Web 容器请求线程，可以阻塞，但建议：
> - 选项 1（简单）：直接阻塞，让 turn 真完成后才走 `completeTurn`。
> - 选项 2（更稳）：把 `tts.complete()` 丢到一个 `voiceTtsExecutor` 单线程池里调用，避免占用 Reactor 调度线程。
>
> 默认走选项 1，如果联调发现影响其它 turn，再切选项 2。

---

### 4.6 `voice/config/VoiceProperties.java` 中 `ttsAudioMimeType` 自动联动（可选，推荐）

如果 `outputFormat` 是 `PCM_*`，前端 MSE 不能直接播 PCM，应改成 `audio/pcm`。建议在 `VoiceProperties` 里加一个派生 getter（不入库）：

```java
public String resolveTtsAudioMimeType() {
    String fmt = StringUtils.upperCase(StringUtils.trimToEmpty(tts.getOutputFormat()));
    if (fmt.startsWith("PCM"))  return "audio/pcm";
    if (fmt.startsWith("WAV"))  return "audio/wav";
    return StringUtils.defaultIfBlank(ttsAudioMimeType, "audio/mpeg");
}
```

并把 Orchestrator 里所有 `voiceProperties.getTtsAudioMimeType()` 调用换成 `voiceProperties.resolveTtsAudioMimeType()`。

> 本次默认仍用 `mp3 → audio/mpeg`，但加上这个 helper 是为了未来切 PCM 时不用再到处改。

---

### 4.7 `application.yml` —— 仅注释更新（默认值不变）

```yaml
jc-ai-agent:
  voice:
    tts:
      # 简写: mp3 / pcm / wav  或者直接写 ttsv2 枚举名 (例如 MP3_22050HZ_MONO_256KBPS)
      output-format: ${JC_VOICE_TTS_OUTPUT_FORMAT:mp3}
      # speech-rate: 1.0
      # pitch-rate: 1.0
      # volume: 50
```

---

### 4.8 测试 —— 旧测试**先 `@Disabled`**

按你的选择，先把单元测试撂下。具体做法：

- `src/test/java/.../voice/provider/tts/AliyunTtsProviderTest.java`
  - 整个类加 `@Disabled("Pending rewrite for ttsv2 SpeechSynthesizer; tracked in TTS streaming fix")`
  - 不删除源码，方便后续改写参考

- `src/test/java/.../voice/provider/VoiceProviderLiveApiTest.java`
  - 这个是真打 DashScope 的活联调测试，**保留**（里面用的就是 `audio.tts.SpeechSynthesizer.call(...)` 同步全量，不影响）。
  - 后续可以加一个新的 `AliyunTtsLiveStreamTest`，用 `audio.ttsv2.SpeechSynthesizer` 真打一次，断言 `audioFrame` 至少触发 N 次（>1 即证明真流式）。

---

## 5. 状态机与异常路径

### 5.1 正常路径（completed）

| 触发                       | TtsSynthesis 状态           | 事件                                   |
| -------------------------- | --------------------------- | -------------------------------------- |
| `executeChatTurn` 入口     | `openStream` → 已建立 WS    | -                                      |
| 第一个 LLM delta           | `appendText(delta)`         | `turn_state(output)` + `tts_state(start)`（首个 audio frame 到时） |
| 后续 LLM deltas            | 多次 `appendText(delta)`    | `text_delta`（SSE）+ binary frames（WS） |
| LLM onCompleted            | `complete()`（阻塞）        | `text_end(completed)`                  |
| TTS onComplete 回调        | listener.onCompleted(false) | `tts_state(end)` + `turn_state(ended/completed)` |
| `cleanupTurnResources`     | -（已 close）               | -                                      |

### 5.2 用户打断（interrupt）

| 触发                       | TtsSynthesis 状态           | 事件                                   |
| -------------------------- | --------------------------- | -------------------------------------- |
| `turn.interrupt` / 新 turn | `cancel()`                  | listener.onCompleted(skipped) 已被吞掉 |
| `interruptTurn`            | `cleanupTurnResources` close | `text_end(interrupted)` + `turn_state(ended/interrupted)` |

> **关键**：`cancel` 必须立刻不再发 binary frame，避免前端收到 interrupt 之后还在响。`BidiTtsSynthesis.cancel()` 会把 `closed` 置 true，listener 回调里的 `closed.get()` check 会忽略后续 `onEvent`。

### 5.3 LLM 失败 / TTS 失败 / 上游网络挂了

| 触发                  | TtsSynthesis 状态           | 事件                                   |
| --------------------- | --------------------------- | -------------------------------------- |
| LLM subscribe error   | `cancel()`                  | `failTurn(VOICE_TEXT_STREAM_FAILED)`    |
| TTS callback onError  | listener.onError(throwable) | `failTurn(VOICE_TTS_FAILED)`           |
| WS 断开（client 关闭）| `handleSocketClosed` → `failTurn` → `cancel()` | `error` 事件已不可达，仅日志             |

### 5.4 死锁/资源泄漏防护

- 每次 turn 必须保证：**`new SpeechSynthesizer` 之后，无论走哪条路径都要走到 `getDuplexApi().close()`**。这件事在 `BidiTtsSynthesis.releaseDuplex()` 里集中处理，由 `complete()` 和 `close()` 都会触发。
- `cleanupTurnResources` 调用 `cancel()`（而非 `close()`），但 `cancel()` 实现里委托给 `close()`，最终保证 `releaseDuplex` 一定被调到。
- `close()` 是幂等的（用 `closed.compareAndSet`）。

---

## 6. 实施步骤（按顺序做）

> 强制顺序，避免半路 broken。

1. **阅读本文档 + 复核**
   - 确认 §3.2 的时序图你没有疑问。
   - 如果对 §4.6 派生 mime 类型有不同意见，先 push back。

2. **改 `VoiceProperties.java`**（§4.2 + §4.6）
   - 加 `speechRate / pitchRate / volume / resolveTtsAudioMimeType()`。
   - 编译过即可。

3. **扩展 `TtsProvider.java` 接口**（§4.3）
   - 加 `openStream(...)` 和新的 `TtsSynthesis` 方法签名。
   - 暂时让旧方法 `synthesizeStream(...)` 标 `@Deprecated`，default 实现为 `openStream + appendText + complete`。

4. **重写 `AliyunTtsProvider.java`**（§4.4）
   - 改导入到 `audio.ttsv2.SpeechSynthesizer / SpeechSynthesisParam / SpeechSynthesisAudioFormat`。
   - `SpeechSynthesisResult` 仍来自 `audio.tts`（共用类）。
   - 实现 `BidiTtsSynthesis` + `NoopTtsSynthesis`。
   - **本步结束后**先编译，确认无 import 错。

5. **改 `VoiceTurnOrchestrator.java`**（§4.5）
   - 把"在 LLM 之前 openStream"、"onTextDelta 里 appendText"、"onTextCompleted 里 complete"、"cleanup 里 cancel" 全部接上。
   - 把原来的 `startTts(...)` 方法删除或改名为 `buildTtsListener(...)`。

6. **`@Disabled` 旧 TTS 单测**（§4.8）
   - 仅加注解，源码留作参考。

7. **本地编译 + 启动**
   - `./mvnw compile` 通过。
   - `./mvnw spring-boot:run`（或 IDE 启动）正常。
   - 启动日志里应没有 `audio.tts.SpeechSynthesizer` 残留引用。

8. **联调验证**（§7）

9. **回归 SSE 文本流**（保证文本通道不变）。

---

## 7. 联调与验证清单

### 7.1 后端日志关键词

启用 DEBUG/INFO 后，应该在一次 turn 中至少看到：

```
INFO  Started Aliyun TTS bidirectional stream: turnId=xxx, model=cosyvoice-v1, voice=longxiaochun, format=mp3-22050hz-mono-256kbps
DEBUG (建议自己加) Streaming TTS chunk emitted: turnId=xxx, chunkBytes=N
INFO  (上层 publish) tts_state start
INFO  (上层 publish) tts_state end
```

如果**没看到多条 chunk emitted（或者只看到 1 条几十 KB 的）**，说明仍未真正流式。

### 7.2 抓包/前端验证

1. 浏览器 DevTools → Network → WS → 选 voice 连接 → Messages tab。
2. 一次提问期间，应看到 **多条 binary（紫色）帧**，每条几 KB ~ 几十 KB，按时间分散。
3. 如果只看到 **1 条体积巨大的 binary 帧（几百 KB 到 1 MB+）**，说明仍是末尾整吐 ⇒ 修复未生效。

### 7.3 首包延迟对比

- 修复前：用户停止说话 ➜ 听到第一声 ≈ `LLM 全文耗时 + TTS 全段合成耗时`
- 修复后（双向流）：用户停止说话 ➜ 听到第一声 ≈ `LLM 首 token 耗时 + TTS 首包延迟（≈ 200ms ~ 800ms）`

### 7.4 异常场景

| 场景                       | 期望表现                                                       |
| -------------------------- | -------------------------------------------------------------- |
| 用户中途按"停止"           | 立即停止播放，前端不再收到 binary，收到 `turn_state(ended/interrupted)` |
| 网络断开                   | 服务端日志一条 socket closed，turn 标 `failed`，无脏 WS 残留 |
| API key 缺失               | `tts_state(end, skipped=true)` + `turn_state(ended/completed)`，前端只展示文本 |
| LLM 报错                   | `error(VOICE_TEXT_STREAM_FAILED)` + `turn_state(ended/failed)`，TTS 已 cancel |
| cosyvoice 服务端 23s 超时   | 极短 LLM 流不会触发；长流由我们控制 delta 间隔，一般无影响    |

### 7.5 资源不泄漏

连续做 N（建议 ≥ 30）次 turn 之后，观察：
- `netstat -an | findstr ESTABLISHED | findstr dashscope` 不应增长。
- JVM 线程数（VisualVM / `jcmd <pid> Thread.print`）不应单调上涨。

---

## 8. 风险、回滚与后续优化

### 8.1 风险

1. **DashScope 端 23s 静默超时**：双向流要求两次 `streamingCall` 之间不能超过 23s。我们的 LLM delta 频率远高于这个，正常不会触发。但要防御：如果 LLM 卡住超过 20s 没出 token，应主动 `tts.complete()` 或 `cancel()`。
2. **mp3 vs pcm**：前端 MSE 在 Chrome/Edge 上对 mp3 流式 append 较稳，但极少数版本对 22050Hz mp3 有 codec 支持差异。如果联调发现某些设备播不出，切 24000Hz：把 `JC_VOICE_TTS_OUTPUT_FORMAT` 设为 `MP3_24000HZ_MONO_256KBPS`。
3. **API key 不带 cosyvoice 权限**：会在第一次 `streamingCall` 时抛 `ApiException`。处理路径走 `onError → failTurn(VOICE_TTS_FAILED)`，前端会拿到 error。
4. **阻塞 `streamingComplete` 占用 Reactor 线程**：如 §4.5 末尾所说，必要时改成异步执行器。

### 8.2 回滚

如果实施后联调失败需要紧急回滚：
- **Git 单 commit 策略**：把上面 §4.2 ~ §4.8 的改动放进**同一个 commit**（如 `feat(voice): switch TTS to ttsv2 bidirectional streaming`），出问题直接 `git revert` 回到改前版本。
- 不要把"重写 Provider"和"接口签名变更"分开提交，否则中间态编译不过。

### 8.3 后续优化（不在本次范围）

1. **句子级早播**：在 `appendText` 之外做一层"按句号断句先送一段"的策略，让首句更早到（cosyvoice 自己已断句，效果不一定有但可压测）。
2. **TTS 池化**：长会话可以保持一个 SpeechSynthesizer 池，复用 WebSocket（要确认 SDK 是否支持复用）。
3. **PCM + 服务端 mp3 实时编码**：如果想要更可控的码率/分块大小，可以让 TTS 返回 PCM，自己用 LAME / ffmpeg 边切边编码。本次不做。
4. **观测指标**：加 `firstPackageDelay` / `chunkCount` / `totalBytes` 指标到 Micrometer，便于压测。
5. **重新启用并改写单元测试**：mockConstruction `audio.ttsv2.SpeechSynthesizer`，用 ArgumentCaptor 抓 `ResultCallback`，手动驱动 `onEvent / onComplete` 验证 listener 行为。

---

## 9. 常见踩坑 FAQ

**Q1：`audio.ttsv2.SpeechSynthesizer` 和 `audio.tts.SpeechSynthesizer` 同名，IDE 自动 import 怎么避坑？**
A：建议在文件头**显式 import 全限定类名**，并在团队 IDE 配置里把 `audio.tts.SpeechSynthesizer` 加到 import 黑名单。code review 时格外注意 import 段。

**Q2：`SpeechSynthesisResult` 应该用哪个？**
A：用 `com.alibaba.dashscope.audio.tts.SpeechSynthesisResult`。这个是**两个版本共用的**结果类，**不要**找 `audio.ttsv2.SpeechSynthesisResult`，那个不存在。

**Q3：每次都 `new SpeechSynthesizer` 不是很贵吗？**
A：对，但官方文档明确说"每次 call 必须 re-init"。背后是因为内部维护了一段 WebSocket task 状态，复用会污染。本项目并发本就不高，可接受。

**Q4：`streamingCall` 和 `call` 能混用吗？**
A：不能。**一个 SpeechSynthesizer 实例只能选一种调用形态**。我们全程用 `streamingCall + streamingComplete`。

**Q5：用户在 LLM 还在出 token 时按下"打断"，TTS 怎么走？**
A：`interruptTurn → cleanupTurnResources → tts.cancel() → close → releaseDuplex`。WebSocket 立即关闭，剩余在路上的 audioFrame 会被 `closed.get()` 守卫挡掉，不再写入 client WS。

**Q6：为什么 §4.5 要在 `executeChatTurn` 入口就 `openStream`，而不是在第一个 delta 时再 open？**
A：因为 cosyvoice 的 WebSocket 握手 + task-started 也要时间（首包延迟里很大一部分就是这个）。**提前几十~几百毫秒开始建连**，能让首个 audio frame 出现在屏幕第一个文字之后非常短。如果担心"LLM 出错时 TTS 白连了"，可以保留这种行为（`tts.cancel()` 释放就是了，成本很低）。

**Q7：MP3 流式 append 到 MSE 时浏览器会"咔哒"一声拼接缝吗？**
A：不会，DashScope 输出的是连续的 frame，每帧都是合法 MP3 frame。MSE 的 `SourceBuffer.appendBuffer(chunk)` 能无缝拼接。但前端需要在 `MediaSource.readyState === 'open'` 之后才 append。

**Q8：日志里偶尔能看到 `socket closed unexpectedly`/`broken pipe`，需要处理吗？**
A：客户端正常关闭也会触发这类异常，已经在 `VoiceWebSocketHandler.isExpectedTransportClose` 里被识别为良性关闭，无需处理。新加的 TTS provider 不应该把这种当真错误抛出来——`releaseDuplex()` 里 `try/catch` 已吞掉。

---

## 10. 验收标准（Definition of Done）

- [ ] `AliyunTtsProvider` 使用 `com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer`，并通过 `streamingCall + streamingComplete` 实现真双向流。
- [ ] `VoiceTurnOrchestrator` 在 LLM delta 到达时即刻 `appendText`，文本完结时 `complete()`。
- [ ] 一次 turn 期间，前端 WS 收到 **多条** binary 帧（≥3 条，单条 < 64KB），首帧出现在最后一条之前数百毫秒。
- [ ] `tts_state(start)` 早于第一条 binary，`tts_state(end)` 晚于最后一条 binary。
- [ ] `interruptTurn` 后 200ms 内不再有 binary 推送。
- [ ] 连续 30 次 turn 之后无 WebSocket / 线程泄漏。
- [ ] 旧 `AliyunTtsProviderTest` 已 `@Disabled`，不影响 `mvn test`。
- [ ] 本文档作为单一信息源被引用在 `docs/streaming-voice-architecture.md` 的 "Recommended next implementation step" 章节末尾。

---

## 11. 给后续接手者（自然语言摘要）

> 一句话：**老 SpeechSynthesizer 是死的，cosyvoice 必须用 `audio.ttsv2.SpeechSynthesizer`。把这个类按双向流模式接到 LLM 的 `onTextDelta` 上，LLM 出一个字符就喂一个字符给 TTS，TTS 的 `onEvent(SpeechSynthesisResult)` 里每来一帧 audio 就立刻 `sendBinary` 给 WebSocket。完事记得 `getDuplexApi().close()`。**

实施顺序：先改 `VoiceProperties` 和 `TtsProvider` 接口，再重写 `AliyunTtsProvider`，再改 `VoiceTurnOrchestrator`，最后 `@Disabled` 旧测试，启动联调。完成后用 §7.2 的浏览器抓包做最直观的验证：**多条小 binary** 才算通过。

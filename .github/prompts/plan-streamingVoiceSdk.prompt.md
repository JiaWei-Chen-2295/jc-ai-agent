Build a reusable real-time voice interaction module with a Java backend and a framework-agnostic TypeScript frontend SDK.

## Goal
Create a standalone, pluggable voice streaming solution that can be integrated into different frontend projects later.

The architecture must use:
- SSE for real-time text streaming
- WebSocket for binary audio streaming and microphone uplink
- MediaSource Extensions (MSE) in the frontend SDK for continuous audio playback
- Java for backend implementation inside the current Spring Boot project
- TypeScript for the frontend SDK, structured so it can later be extracted into an npm package

## Confirmed Constraints
- Backend project is an existing Spring Boot 3.4 + Java 21 application
- Existing chat stack already uses Spring AI and SSE via SseEmitter
- Existing auth is session-based, not JWT
- Existing chat flow should be reused rather than building a completely separate AI pipeline
- Voice input should use Aliyun ASR
- TTS should use an Aliyun-compatible streaming API or similar China-region provider
- Audio output format should be MP3 for broad browser MSE compatibility
- WebSocket should be persistent across turns
- SSE should be per-turn for text output
- User microphone audio should be sent over the same persistent WebSocket used for audio downlink and voice session signaling
- Existing Vercel AI SDK capabilities can be referenced for transport and streaming abstractions, but they do not provide a drop-in replacement for this Java backend + Aliyun ASR/TTS + SSE text + WS audio + MSE playback architecture

## High-Level Architecture
Design the system as two coordinated but independently resilient streams:

1. Text stream:
- For each conversation turn, open one SSE request
- Reuse the existing Spring AI streaming infrastructure to emit text deltas
- Treat SSE as the canonical text channel for UI rendering
- Emit metadata events such as turn_started, text_delta, text_complete, tool_event, error, turn_complete

2. Audio stream:
- Maintain one authenticated persistent WebSocket per client voice session
- Use this socket for:
  - microphone audio uplink
  - backend control messages
  - TTS audio binary downlink
  - optional timing and state events
- Audio should be streamed as MP3 binary chunks for MSE playback

3. Coordination model:
- Do not hard-couple audio playback timing to SSE token timing
- Synchronize at the turn level, not token level
- Every turn must have a unique turnId
- SSE events and WS messages must both include turnId
- Frontend should render text as soon as SSE arrives
- Frontend should play audio as soon as enough MP3 data is buffered
- Backend may start TTS from buffered sentence fragments or from finalized model output depending on implementation phase

4. Recommended delivery phases:
- Phase 1: text-in or voice-in to full LLM text, then stream TTS audio for the final answer
- Phase 2: incremental sentence-level TTS while LLM text SSE is still streaming
- Phase 3: tighter interruption, barge-in, and partial-turn cancellation support

## Backend Design Requirements
Implement a modular voice layer on top of the existing backend rather than scattering logic through controllers.

Create a voice module with clear separation of responsibilities:
- voice/controller
- voice/ws
- voice/service
- voice/session
- voice/provider/asr
- voice/provider/tts
- voice/model
- voice/config

Suggested backend components for V1:

1. VoiceSessionRegistry
Responsibilities:
- maintain persistent WS session state per logged-in user
- map userId/sessionId to active voice session
- store connection state, active turnId, selected codec, timestamps, and cleanup hooks
- support reconnect and stale-session eviction

2. VoiceWebSocketHandler
Responsibilities:
- authenticate session using existing login/session model
- accept JSON control frames and binary microphone frames
- route microphone chunks to ASR service
- send binary MP3 chunks from TTS pipeline back to client
- send JSON events such as session_ready, turn_started, asr_partial, asr_final, tts_started, tts_completed, turn_interrupted, error

3. VoiceTurnOrchestrator
Responsibilities:
- own lifecycle of one conversation turn
- receive ASR final transcript
- invoke existing StudyFriend chat pipeline
- open SSE-compatible text stream production path
- trigger TTS generation from final or segmented text
- propagate cancellation and completion to both SSE and WS

4. VoiceTextStreamService
Responsibilities:
- adapt existing StudyFriend streaming result into normalized turn-scoped events
- preserve compatibility with current SSE controller style
- emit per-turn SSE events with chatId, turnId, sequence, type, payload

5. AsrProvider interface
Responsibilities:
- abstract Aliyun ASR specifics
- methods for startSession, sendAudioChunk, stopSession, close
- callbacks for partial transcript, final transcript, provider errors

6. TtsProvider interface
Responsibilities:
- abstract Aliyun TTS specifics
- methods for synthesizeStream, cancel, close
- callback or reactive stream for binary audio chunks and synthesis state events

7. AliyunAsrProvider
Responsibilities:
- manage provider-specific WS connection to Aliyun ASR
- convert frontend microphone format into provider-required encoding/rate
- surface partial and final transcript events
- handle token refresh, provider timeouts, reconnect policy

8. AliyunTtsProvider
Responsibilities:
- manage streaming TTS connection
- request MP3 output
- forward binary chunks incrementally to frontend WS
- emit synthesis completed and failure events

## Cleanup Strategy
Do not require a standalone VoiceCleanupService in V1.

Use ownership-based cleanup first:
- VoiceTurnOrchestrator handles turn-scoped cleanup
- VoiceSessionRegistry handles session removal and stale-session eviction
- AsrProvider and TtsProvider implementations own provider connection teardown
- VoiceWebSocketHandler owns WebSocket lifecycle cleanup

Only introduce a dedicated VoiceCleanupService later if cleanup logic becomes cross-cutting, duplicated across multiple failure paths, or requires a strict centralized teardown order.

## Backend API Surface
Define these integration points:

1. Persistent WebSocket endpoint
Example:
- /api/ws/voice

Responsibilities:
- establish long-lived voice session
- handle control JSON and binary microphone frames
- return binary MP3 audio chunks and JSON events

2. Per-turn SSE endpoint
Example:
- GET /api/voice/turn/{turnId}/text/stream
or
- GET /api/ai_friend/voice/text/sse

Responsibilities:
- stream text deltas for a single turn
- reuse existing session auth
- emit normalized event names

3. Optional REST endpoints
Examples:
- POST /api/voice/session/start
- POST /api/voice/turn/start
- POST /api/voice/turn/stop

These can simplify client orchestration if needed, but avoid overcomplicating the first version if WS + SSE are enough.

## Backend Event Contract
Define a deliberately small event surface.

State simplification rule:
- keep exactly one business state machine for a turn
- treat WebSocket and SSE as transport channels, not competing business state machines
- derive UI sub-states from events instead of persisting many parallel enums

Recommended business state model:
- idle
- input
- processing
- output
- ended

Recommended end reasons:
- completed
- interrupted
- failed

How to interpret the simplified states:
- idle: no active turn
- input: user audio is being captured or ASR is still producing final text
- processing: ASR final text has been accepted and backend is generating the answer
- output: at least one of SSE text output or WS audio output has started
- ended: the turn is finished, with endReason indicating completed, interrupted, or failed

Transport state model should stay minimal and local:

1. WebSocket session state:
- disconnected
- connecting
- ready
- closing

2. SSE stream state:
- idle
- opening
- streaming
- closed

Do not create extra persisted states such as listening, recognizing, thinking, speaking, text_streaming, audio_streaming, reconnecting, and completed on every layer. If needed for UI, compute them from the simplified state plus transport facts.

Recommended SSE events:
- turn_state
- text_delta
- text_end
- error

Recommended WebSocket JSON events:
- session_state
- asr_text
- tts_state
- error

WebSocket binary frames:
- raw MP3 chunk bytes only

Event payload guidance:
- turn_state carries state plus optional endReason
- asr_text carries mode=partial|final instead of separate event names
- tts_state carries phase=start|end instead of separate event names
- session_state carries state=connecting|ready|closing|disconnected

Every JSON event should include:
- sessionId
- turnId when a turn exists
- timestamp
- type
- payload

## Frontend SDK Design
Create a standalone TypeScript SDK in a new directory in this repo, structured for future npm extraction.

Suggested package structure:
- streaming-voice-sdk/
  - src/
    - index.ts
    - manager/StreamingVoiceManager.ts
    - transport/SseTextStream.ts
    - transport/VoiceWebSocket.ts
    - playback/MediaSourcePlayer.ts
    - capture/MicrophoneCapture.ts
    - types/events.ts
    - types/options.ts
    - utils/backoff.ts
    - utils/emitter.ts
    - utils/codecs.ts
  - package.json
  - tsconfig.json
  - README.md

## Frontend Core Class
Implement StreamingVoiceManager as the main public API.

Public API should include at least:
- start(): Promise<void>
- stop(): Promise<void>
- startTurn(options): Promise<{ turnId: string }>
- stopTurn(turnId?): void
- sendAudioChunk(chunk: ArrayBuffer): void
- interrupt(): void
- destroy(): Promise<void>
- onText(listener)
- onError(listener)
- onStateChange(listener)
- onAudioState(listener)
- onAsrPartial(listener)
- onAsrFinal(listener)

Constructor options should include:
- wsUrl
- sseFactory or sseUrlBuilder
- auth headers or credentials mode
- mimeCodec defaulting to audio/mpeg
- mediaElement
- reconnect policy
- buffer policy
- debug flag

## MSE Playback Requirements
Implement MediaSource-based playback for MP3 streaming.

MediaSourcePlayer responsibilities:
- create MediaSource and SourceBuffer lazily
- verify browser codec support using MediaSource.isTypeSupported()
- append binary chunks sequentially
- maintain an internal append queue because SourceBuffer only accepts one append at a time
- listen for sourceopen before creating SourceBuffer
- listen for updateend to drain the queue and perform safe eviction
- handle quota errors by removing old buffered ranges
- recover from underflow by nudging playback when enough buffered data exists
- support reset between turns without leaking object URLs or listeners

Critical behavior to document in comments:
- sourceopen is the earliest safe point to create the SourceBuffer
- while sourceBuffer.updating is true, new chunks must be queued instead of appended immediately
- updateend is the safe trigger to append the next queued chunk and optionally prune old buffered media
- if QuotaExceededError occurs, remove older buffered ranges before retrying append

Recommended buffering rules:
- keep a bounded append queue
- keep only a rolling playback window, for example 20 to 40 seconds behind currentTime
- do not call endOfStream on every turn unless the session is actually ending; prefer turn-level resets that preserve player health when practical

## Async and State Management Strategy
Think carefully about state ownership.

1. Single source of truth:
- VoiceTurnOrchestrator is the owner of business turn state
- WebSocketHandler owns only WS connection state
- SseTextStream owns only SSE stream state
- MediaSourcePlayer owns only playback buffer state, not business turn state

2. Minimal state machines:
- do not model separate backend and frontend business states with different enums
- use one shared TurnState enum across backend events and frontend SDK
- use transport-local states only for connection handling, not business progression

3. Recommended state transition graph:
- idle -> input
- input -> processing
- processing -> output
- output -> ended
- input -> ended when interrupted or failed early
- processing -> ended when interrupted or failed
- output -> ended when interrupted or failed

4. Derived UI hints instead of extra states:
- show "listening" when TurnState=input and microphone capture is active
- show "recognizing" when TurnState=input and latest ASR event is partial
- show "thinking" when TurnState=processing
- show "speaking" when TurnState=output and audio has started
- show "streaming text" when TurnState=output and SSE is streaming

5. UI anti-jitter rules with KISS:
- event edges create or end a UI mode, but short-lived events must not immediately flip the visible label
- prefer sustained signals over raw event bursts when rendering status text
- keep one visible primary status for the whole turn instead of rendering separate WS and SSE statuses
- once the UI enters a higher-priority visible mode, keep it until a clear exit condition is met
- do not add timers unless they solve a real flicker problem that cannot be solved by priority and exit rules alone

Priority rules are mandatory:
- output beats processing
- processing beats input
- input beats idle
- ended beats everything

Event to sustained signal mapping:
- asr partial events should set a sustained signal such as hasPartialRecognition=true for the current turn, rather than briefly flashing the UI on every partial
- first accepted LLM work should set a sustained signal such as hasProcessingStarted=true
- first text_delta or first audio chunk should set a sustained signal such as hasOutputStarted=true
- turn end should set a sustained signal such as endReason=completed|interrupted|failed and clear all in-turn transient signals

Recommended visible UI mapping:
- ended + completed => completed
- ended + interrupted => interrupted
- ended + failed => failed
- output => speaking
- processing => thinking
- input + hasPartialRecognition => recognizing
- input => listening
- otherwise => idle

This preserves stable labels while keeping the underlying state model small.

6. Ordering rules:
- Text and audio are allowed to arrive at different speeds
- Never assume first text chunk and first audio chunk arrive in lockstep
- UI should tolerate text-first, audio-first, or temporary stalls in either stream
- Ignore stale events whose turnId does not match the active turn

7. Reconnection policy:
- WebSocket reconnect should use exponential backoff
- On reconnect, the SDK should emit a recoverable state event
- SSE should not silently auto-reconnect to an old turn; the manager should decide whether to reopen or mark the turn failed

8. Reconnection simplification rules:
- only WebSocket supports reconnect as a first-class behavior
- SSE is per-turn and should be recreated by turn logic, not by a generic endless reconnect loop
- after WS reconnect, do not resume old audio blindly; require explicit turn resync or mark the turn ended

9. Clear lifecycle end conditions are mandatory:
- a turn enters ended/completed only after both text output and audio output are known to be finished, or after the active phase explicitly declares that no further output will occur
- a turn enters ended/interrupted immediately when the user interrupts and all later stale events for that turn must be ignored
- a turn enters ended/failed immediately on unrecoverable provider, transport, or orchestration failure
- once a turn is ended, no later event may move it back to input, processing, or output
- the UI should clear sustained in-turn signals only when the turn transitions to ended or when a new turn replaces it

10. Cleanup rules:
- close EventSource
- close WebSocket
- stop microphone tracks
- revoke MediaSource object URLs if used
- remove all DOM and transport listeners
- clear append queues and timers
- detach callbacks from destroyed instances

11. Cleanup ownership rules:
- prefer cleanup at the component that created the resource
- keep turn cleanup separate from session cleanup
- make stop and destroy paths idempotent
- introduce a centralized cleanup service only after repeated cross-component duplication is observed

12. Anti-complexity rules:
- avoid separate state enums for backend DTOs, frontend manager, SSE stream, and WS stream unless the semantics truly differ
- prefer a small number of generic event types with phase fields over many highly specific event names
- do not persist transient UI wording as state; persist only facts that affect control flow
- prefer priority rules and explicit exit conditions over debounce-heavy UI logic

## Edge Cases To Handle
Backend and SDK must handle:
- WebSocket reconnect after transient network loss
- SSE disconnect before turn completion
- stale turn events arriving after interruption
- MSE codec unsupported in browser
- SourceBuffer quota pressure
- append attempts while updating
- audio underflow if chunks arrive too slowly
- user interrupt during ASR, LLM generation, or TTS playback
- provider-side timeout or token expiration
- duplicate completion events
- cleanup after browser tab visibility changes or page navigation

## Recommended Implementation Sequence
1. Add backend voice module skeleton and interfaces
2. Add Spring WebSocket configuration and authenticated voice handler
3. Implement turn registry and orchestrator
4. Reuse existing StudyFriend streaming pipeline for SSE text output
5. Implement provider abstraction for Aliyun ASR and Aliyun TTS
6. Implement a basic end-to-end flow where final transcript triggers LLM and final text triggers streaming TTS
7. Create framework-agnostic TS SDK package
8. Implement MediaSourcePlayer with robust queueing and cleanup
9. Implement persistent VoiceWebSocket transport and per-turn SseTextStream transport
10. Implement StreamingVoiceManager orchestration and typed event surface
11. Add a minimal demo integration page or example usage
12. Add reconnection, interruption, and quota-handling hardening

## Acceptance Criteria
- A frontend app can establish one persistent WS voice session
- A user can stream microphone audio to backend over WS
- Backend can obtain ASR final transcript
- Backend can invoke the existing Spring AI chat flow for that turn
- Frontend receives text deltas over SSE in real time
- Frontend receives MP3 binary chunks over WS in real time
- Frontend plays audio continuously via MSE without per-chunk gaps
- SDK exposes framework-agnostic lifecycle APIs
- Stop and destroy paths clean up all transports, listeners, and buffers
- The code structure is suitable for extracting the TS SDK as an npm package later

## Deliverables
Produce:
1. A high-level architecture overview describing why SSE and WS are separated and how turnId-based coordination works
2. Java backend module design and initial implementation skeleton compatible with the current Spring Boot project
3. TypeScript SDK centered on StreamingVoiceManager
4. A robust MediaSourcePlayer implementation with comments around sourceopen and updateend handling
5. Example usage showing how a frontend host app would initialize the SDK and subscribe to text and error events
6. Notes on browser codec support and fallback strategy when MSE MP3 is unavailable

## Important Engineering Notes
- Prefer modular interfaces over vendor-specific coupling
- Keep provider implementations replaceable
- Reuse the project’s existing chat services and auth/session model
- Keep the frontend package browser-native and framework-agnostic
- Favor explicit lifecycle management over hidden background behavior
- Do not bind the SDK to Vue or React abstractions
- Keep the first version production-oriented but phaseable
- Do not adopt Vercel AI SDK as a hard dependency for this design; only borrow proven ideas such as transport abstraction, event contracts, and streaming state handling where useful
- Avoid premature service extraction; if cleanup logic remains local and simple, keep it inside the owning orchestrator, registry, handler, or provider

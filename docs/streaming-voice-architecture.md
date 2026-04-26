# Streaming Voice Architecture

## Overview

This repository now has an initial voice module that keeps text and audio as two coordinated but separately resilient transports.

- SSE is the canonical text channel for a single turn.
- WebSocket is the persistent session channel for microphone uplink, control events, and MP3 audio downlink.
- `turnId` is the coordination key shared across both transports.

The backend reuses the existing `StudyFriend` streaming pipeline instead of introducing a second chat stack. A voice turn becomes:

1. create or select an active voice WebSocket session
2. start a turn with `chatId` and optional transcript
3. stream text deltas over SSE from the existing Spring AI flow
4. synthesize final-turn TTS over the WebSocket audio channel
5. close the turn when both text output and audio output are known to be finished, or when the turn is interrupted or fails

## Why SSE and WebSocket are separate

They solve different transport problems.

- SSE gives simple ordered text streaming with native browser support and matches the project's existing `SseEmitter` pattern.
- WebSocket supports low-overhead binary audio chunks and persistent bidirectional signaling.
- Keeping them separate avoids binding audio timing to token timing. The UI renders text as soon as SSE arrives and plays audio as soon as MSE has enough MP3 buffered data.

## Backend shape

The backend voice module lives under `fun.javierchen.jcaiagentbackend.voice` and is split by responsibility:

- `voice/controller`: HTTP endpoints for turn start/stop and per-turn text SSE
- `voice/ws`: authenticated persistent WebSocket handler
- `voice/service`: turn orchestration and text stream fanout
- `voice/session`: WebSocket session registry and turn registry
- `voice/provider/asr`: ASR abstraction and Aliyun provider skeleton
- `voice/provider/tts`: TTS abstraction and Aliyun-compatible provider skeleton
- `voice/model`: shared event envelopes, payloads, and state enums
- `voice/config`: Spring properties, handshake interceptor, and WebSocket config

## Current phase

The implementation is aligned to Phase 1.

- Text generation is fully wired through the existing StudyFriend stream.
- TTS transport and provider boundaries are wired, but the provider implementation is still a production skeleton that currently completes without streaming provider audio when no provider configuration is present.
- ASR boundaries are wired for persistent microphone uplink, but the provider implementation is still a skeleton. The path is already usable for transcript-first turns through `turn.start` or `POST /api/voice/turn/start`.

This means the architecture, transport contracts, and state model are reusable now, while Aliyun ASR/TTS provider-specific networking can be completed without changing the public event surface.

## Event contract

### SSE events

- `turn_state`
- `text_delta`
- `text_end`
- `error`

Each SSE event carries:

- `sessionId`
- `chatId`
- `turnId`
- `sequence`
- `timestamp`
- `type`
- `payload`

### WebSocket JSON events

- `session_state`
- `turn_state`
- `asr_text`
- `tts_state`
- `error`

Each WebSocket JSON event carries:

- `sessionId`
- `turnId` when present
- `timestamp`
- `type`
- `payload`

### WebSocket binary frames

- raw MP3 chunk bytes only

## State model

The business turn state stays intentionally small:

- `idle`
- `input`
- `processing`
- `output`
- `ended`

End reasons:

- `completed`
- `interrupted`
- `failed`

Transport state is local and minimal:

- WebSocket session: `disconnected`, `connecting`, `ready`, `closing`
- SSE stream: managed per turn, not globally persisted

## Current endpoints

- WebSocket: `/api/ws/voice`
- SSE: `/api/voice/turn/{turnId}/text/stream`
- REST helper: `POST /api/voice/turn/start`
- REST helper: `POST /api/voice/turn/stop`

## Recommended next implementation step

The implementation checklist and concrete change plan now lives in `docs/streaming-voice-tts-streaming-fix-plan.md`.

Complete the Aliyun provider adapters behind the already-stable interfaces:

1. implement provider WebSocket streaming in `AliyunAsrProvider`
2. implement provider streaming MP3 downlink in `AliyunTtsProvider`
3. add sentence-level partial TTS generation after the final-turn path is stable
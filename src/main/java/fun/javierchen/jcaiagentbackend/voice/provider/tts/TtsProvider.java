package fun.javierchen.jcaiagentbackend.voice.provider.tts;

import fun.javierchen.jcaiagentbackend.voice.session.VoiceTurnContext;

public interface TtsProvider {

    TtsSynthesis synthesizeStream(VoiceTurnContext turnContext, String text, TtsListener listener);

    interface TtsSynthesis {
        void cancel();

        void close();
    }

    interface TtsListener {
        void onStart();

        void onAudioChunk(byte[] audioChunk);

        void onCompleted(boolean skipped);

        void onError(Throwable error);
    }
}
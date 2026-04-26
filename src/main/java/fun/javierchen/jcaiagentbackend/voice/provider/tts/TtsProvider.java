package fun.javierchen.jcaiagentbackend.voice.provider.tts;

import fun.javierchen.jcaiagentbackend.voice.session.VoiceTurnContext;

public interface TtsProvider {

    TtsSynthesis openStream(VoiceTurnContext turnContext, TtsListener listener);

    @Deprecated
    default TtsSynthesis synthesizeStream(VoiceTurnContext turnContext, String text, TtsListener listener) {
        TtsSynthesis synthesis = openStream(turnContext, listener);
        synthesis.appendText(text);
        synthesis.complete();
        return synthesis;
    }

    interface TtsSynthesis {
        void appendText(String textDelta);

        void complete();

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
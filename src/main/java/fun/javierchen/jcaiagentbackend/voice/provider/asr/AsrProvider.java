package fun.javierchen.jcaiagentbackend.voice.provider.asr;

import fun.javierchen.jcaiagentbackend.voice.session.VoiceTurnContext;

public interface AsrProvider {

    AsrSession startSession(VoiceTurnContext turnContext, AsrListener listener);

    interface AsrSession {
        void sendAudioChunk(byte[] audioChunk);

        void stop();

        void close();
    }

    interface AsrListener {
        void onPartial(String text);

        void onFinal(String text);

        void onError(Throwable error);
    }
}
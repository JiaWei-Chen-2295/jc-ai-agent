package fun.javierchen.jcaiagentbackend.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * 针对业务的特制 Advisor
 */
@Slf4j
public class AgentLoggerAdvisor implements CallAdvisor, StreamAdvisor {

    public String getName() {
        return this.getClass().getSimpleName();
    }

    public int getOrder() {
        return 0;
    }

    private ChatClientRequest before(ChatClientRequest request) {
        log.info("AI Requests{}", request);
        return request;
    }

    private void observeAfter(ChatClientResponse chatClientResponse) {
        assert chatClientResponse.chatResponse() != null;
        log.info("AI Responses{}", chatClientResponse.chatResponse().getResult().getOutput().getText());
    }

    public String toString() {
        return AgentLoggerAdvisor.class.getSimpleName();
    }

    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
        chatClientRequest = this.before(chatClientRequest);
        ChatClientResponse chatClientResponse = chain.nextCall(chatClientRequest);
        this.observeAfter(chatClientResponse);
        return chatClientResponse;
    }

    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
        chatClientRequest = this.before(chatClientRequest);
        Flux<ChatClientResponse> chatClientResponses = chain.nextStream(chatClientRequest);
        return new ChatClientMessageAggregator().aggregateChatClientResponse(chatClientResponses, this::observeAfter);
    }
}

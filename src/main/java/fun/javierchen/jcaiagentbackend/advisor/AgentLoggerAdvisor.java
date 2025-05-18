package fun.javierchen.jcaiagentbackend.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.model.MessageAggregator;
import reactor.core.publisher.Flux;

/**
 * 针对业务的特制 Advisor
 */
@Slf4j
public class AgentLoggerAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

    public String getName() {
        return this.getClass().getSimpleName();
    }

    public int getOrder() {
        return 0;
    }

    private AdvisedRequest before(AdvisedRequest request) {
        log.info("AI Requests{}", request.userText());
        return request;
    }

    private void observeAfter(AdvisedResponse advisedResponse) {
        assert advisedResponse.response() != null;
        log.info("AI Responses{}", advisedResponse.response().getResult().getOutput().getText());
    }

    public String toString() {
        return AgentLoggerAdvisor.class.getSimpleName();
    }

    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        advisedRequest = this.before(advisedRequest);
        AdvisedResponse advisedResponse = chain.nextAroundCall(advisedRequest);
        this.observeAfter(advisedResponse);
        return advisedResponse;
    }

    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        advisedRequest = this.before(advisedRequest);
        Flux<AdvisedResponse> advisedResponses = chain.nextAroundStream(advisedRequest);
        // 通过消息聚合器将流式的消息聚合
        return (new MessageAggregator()).aggregateAdvisedResponse(advisedResponses, this::observeAfter);
    }
}

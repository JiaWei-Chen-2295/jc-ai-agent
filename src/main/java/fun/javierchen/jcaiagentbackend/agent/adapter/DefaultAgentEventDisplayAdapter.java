package fun.javierchen.jcaiagentbackend.agent.adapter;

import fun.javierchen.jcaiagentbackend.agent.display.DisplayEvent;
import fun.javierchen.jcaiagentbackend.agent.display.DisplayFormats;
import fun.javierchen.jcaiagentbackend.agent.display.DisplayStages;
import fun.javierchen.jcaiagentbackend.agent.event.AgentEvent;
import fun.javierchen.jcaiagentbackend.agent.event.AgentEventStage;
import fun.javierchen.jcaiagentbackend.agent.event.AgentEventTypes;
import fun.javierchen.jcaiagentbackend.agent.event.DocumentSearchResultItem;
import fun.javierchen.jcaiagentbackend.agent.event.DocumentSearchResultPayload;
import fun.javierchen.jcaiagentbackend.agent.event.DocumentSearchStartPayload;
import fun.javierchen.jcaiagentbackend.agent.event.OutputDeltaPayload;
import fun.javierchen.jcaiagentbackend.agent.event.ThinkingProgressPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 默认语义 -> 展示映射实现
 */
@Component
//@ConditionalOnMissingBean(AgentEventDisplayAdapter.class)
public class DefaultAgentEventDisplayAdapter implements AgentEventDisplayAdapter {

    @Override
    // 按事件 type 分发，stage 仅用于展示
    public List<DisplayEvent> adapt(AgentEvent<?> event) {
        if (event == null || event.type() == null) {
            return Collections.emptyList();
        }
        String type = event.type();
        if (AgentEventTypes.DOCUMENT_SEARCH_START.equals(type)) {
            DocumentSearchStartPayload payload = castPayload(event.payload(), DocumentSearchStartPayload.class);
            String content = buildSearchStartContent(payload);
            return Collections.singletonList(DisplayEvent.of(
                    resolveStage(event.stage(), DisplayStages.SEARCHING),
                    DisplayFormats.STATUS,
                    content,
                    false
            ));
        }
        if (AgentEventTypes.DOCUMENT_SEARCH_RESULT.equals(type)) {
            DocumentSearchResultPayload payload = castPayload(event.payload(), DocumentSearchResultPayload.class);
            String content = buildSearchResultContent(payload);
            return Collections.singletonList(DisplayEvent.of(
                    resolveStage(event.stage(), DisplayStages.SEARCHING),
                    DisplayFormats.MARKDOWN,
                    content,
                    false
            ));
        }
        if (AgentEventTypes.THINKING_START.equals(type)) {
            return Collections.singletonList(DisplayEvent.of(
                    resolveStage(event.stage(), DisplayStages.THINKING),
                    DisplayFormats.STATUS,
                    "Thinking...",
                    false
            ));
        }
        if (AgentEventTypes.THINKING_PROGRESS.equals(type)) {
            ThinkingProgressPayload payload = castPayload(event.payload(), ThinkingProgressPayload.class);
            String message = payload == null ? null : payload.getMessage();
            if (message == null || message.isEmpty()) {
                return Collections.emptyList();
            }
            return Collections.singletonList(DisplayEvent.of(
                    resolveStage(event.stage(), DisplayStages.THINKING),
                    DisplayFormats.TEXT,
                    message,
                    false
            ));
        }
        if (AgentEventTypes.OUTPUT_START.equals(type)) {
            return Collections.singletonList(DisplayEvent.of(
                    resolveStage(event.stage(), DisplayStages.OUTPUT),
                    DisplayFormats.STATUS,
                    "Generating output...",
                    false
            ));
        }
        if (AgentEventTypes.OUTPUT_DELTA.equals(type)) {
            OutputDeltaPayload payload = castPayload(event.payload(), OutputDeltaPayload.class);
            String delta = payload == null ? null : payload.getDelta();
            return Collections.singletonList(DisplayEvent.of(
                    resolveStage(event.stage(), DisplayStages.OUTPUT),
                    DisplayFormats.TEXT,
                    delta,
                    true
            ));
        }
        if (AgentEventTypes.OUTPUT_COMPLETE.equals(type)) {
            return Collections.singletonList(DisplayEvent.of(
                    resolveStage(event.stage(), DisplayStages.OUTPUT),
                    DisplayFormats.STATUS,
                    "Output complete.",
                    false
            ));
        }
        return Collections.emptyList();
    }

    private String resolveStage(AgentEventStage stage, String fallback) {
        if (stage == null) {
            return fallback;
        }
        switch (stage) {
            case SEARCHING:
                return DisplayStages.SEARCHING;
            case THINKING:
                return DisplayStages.THINKING;
            case OUTPUT:
                return DisplayStages.OUTPUT;
            default:
                return fallback;
        }
    }

    private String buildSearchStartContent(DocumentSearchStartPayload payload) {
        if (payload == null) {
            return "Searching documents";
        }
        StringBuilder builder = new StringBuilder("Searching documents");
        String query = payload.getQuery();
        if (query != null && !query.isEmpty()) {
            builder.append(": ").append(query);
        }
        String source = payload.getSource();
        if (source != null && !source.isEmpty()) {
            builder.append(" (source=").append(source).append(')');
        }
        return builder.toString();
    }

    private String buildSearchResultContent(DocumentSearchResultPayload payload) {
        List<DocumentSearchResultItem> results = payload == null ? null : payload.getResults();
        if (results == null || results.isEmpty()) {
            return "Search results: (none)";
        }
        StringBuilder builder = new StringBuilder("Search results:\n");
        for (DocumentSearchResultItem item : results) {
            if (item == null) {
                continue;
            }
            builder.append("- ");
            String title = item.getTitle();
            if (title != null && !title.isEmpty()) {
                builder.append(title);
            } else {
                builder.append("Untitled");
            }
            Double score = item.getScore();
            if (score != null) {
                builder.append(" (score=").append(score).append(')');
            }
            String snippet = item.getSnippet();
            if (snippet != null && !snippet.isEmpty()) {
                builder.append(": ").append(snippet);
            }
            String sourceId = item.getSourceId();
            if (sourceId != null && !sourceId.isEmpty()) {
                builder.append(" [").append(sourceId).append(']');
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private <T> T castPayload(Object payload, Class<T> type) {
        if (type.isInstance(payload)) {
            return type.cast(payload);
        }
        return null;
    }
}

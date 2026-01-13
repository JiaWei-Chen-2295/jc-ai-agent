package fun.javierchen.jcaiagentbackend.rag.application.retrieval;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

public class LoveAppContextualQueryAugmenterFactory {

    public static ContextualQueryAugmenter create() {

        PromptTemplate emptyPromptTemplate = new PromptTemplate("""
                现在你的知识库无法回答这个问题，请你告诉用户：
                当前我无法回答这个问题，请联系管理员 JC 来解决问题
                """);

        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptyPromptTemplate)
                .build();
    }

}

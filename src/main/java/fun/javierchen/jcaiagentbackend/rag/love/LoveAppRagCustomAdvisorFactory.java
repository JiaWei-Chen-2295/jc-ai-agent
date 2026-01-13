package fun.javierchen.jcaiagentbackend.rag.love;

import fun.javierchen.jcaiagentbackend.rag.model.LoveAppMetaDataStatusEnum;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

public class LoveAppRagCustomAdvisorFactory {

    public static Advisor create(VectorStore store, LoveAppMetaDataStatusEnum status) {
        // 创建过滤器 过滤元信息的状态
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("status", status.getText()).build();

        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(store)
                // 过滤元信息的状态
                .filterExpression(expression)
                // 最多返回 3条数据
                .topK(3)
                // 指定相似度阈值
                .similarityThreshold(0.5)
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                // 指定检索增强-错误处理
                .queryAugmenter(LoveAppContextualQueryAugmenterFactory.create())
                .build();
    }
}

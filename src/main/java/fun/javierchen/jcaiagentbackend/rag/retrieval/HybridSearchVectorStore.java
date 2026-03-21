package fun.javierchen.jcaiagentbackend.rag.retrieval;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class HybridSearchVectorStore implements VectorStore {

    private final HybridRetriever hybridRetriever;
    private final VectorStore studyFriendPGvectorStore;

    @Override
    public void add(List<Document> documents) {
        studyFriendPGvectorStore.add(documents);
    }

    @Override
    public void delete(List<String> idList) {
        studyFriendPGvectorStore.delete(idList);
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        studyFriendPGvectorStore.delete(filterExpression);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        return hybridRetriever.search(request);
    }
}

package fun.javierchen.jcaiagentbackend.rag.retrieval;

import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RRFMerger {

    private RRFMerger() {
    }

    public static List<Document> merge(List<List<Document>> resultLists, int topK, int rrfK) {
        Map<String, Double> scoreMap = new HashMap<>();
        Map<String, Document> docMap = new HashMap<>();

        for (List<Document> results : resultLists) {
            for (int rank = 0; rank < results.size(); rank++) {
                Document document = results.get(rank);
                if (document == null || document.getId() == null) {
                    continue;
                }
                double score = 1.0 / (rrfK + rank + 1);
                scoreMap.merge(document.getId(), score, Double::sum);
                docMap.putIfAbsent(document.getId(), document);
            }
        }

        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    Document document = docMap.get(entry.getKey());
                    return Document.builder()
                            .id(document.getId())
                            .text(document.getText())
                            .metadata(document.getMetadata())
                            .score(entry.getValue())
                            .build();
                })
                .toList();
    }
}

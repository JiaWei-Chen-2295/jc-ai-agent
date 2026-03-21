package fun.javierchen.jcaiagentbackend.rag.retrieval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RRFMergerTest {

    @Test
    @DisplayName("相同文档在多路结果中重复出现时应提升排名")
    void shouldBoostDocumentsAppearingInMultipleLists() {
        Document docA = Document.builder().id("a").text("A").build();
        Document docB = Document.builder().id("b").text("B").build();
        Document docC = Document.builder().id("c").text("C").build();

        List<Document> merged = RRFMerger.merge(
                List.of(
                        List.of(docA, docB),
                        List.of(docC, docA)
                ),
                3,
                60
        );

        assertThat(merged).extracting(Document::getId)
                .containsExactly("a", "c", "b");
        assertThat(merged.getFirst().getScore()).isGreaterThan(merged.get(1).getScore());
    }
}

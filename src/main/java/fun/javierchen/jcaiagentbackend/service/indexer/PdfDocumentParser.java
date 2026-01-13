package fun.javierchen.jcaiagentbackend.service.indexer;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF-focused parser for RAG:
 * - extracts text page by page to keep page metadata
 * - splits with TokenTextSplitter for embedding-friendly chunks
 */
@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE) // 確保覆蓋通用解析器的 pdf 映射
public class PdfDocumentParser implements DocumentParser {

    private final TextSplitter textSplitter = TokenTextSplitter.builder()
            .withChunkSize(800)
            .withMinChunkSizeChars(350)
            .withMinChunkLengthToEmbed(5)
            .withMaxNumChunks(10000)
            .withKeepSeparator(true)
            .build();

    @Override
    public List<Document> parse(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("file not found: " + filePath);
        }

        try (PDDocument pdf = Loader.loadPDF(file)) {
            int pages = pdf.getNumberOfPages();
            if (pages == 0) {
                log.warn("pdf is empty: {}", filePath);
                return List.of();
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            List<Document> allChunks = new ArrayList<>();

            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = normalize(stripper.getText(pdf));
                if (pageText.isBlank()) {
                    continue;
                }

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", filePath);
                metadata.put("filename", file.getName());
                metadata.put("fileType", "pdf");
                metadata.put("page", page);

                Document pageDocument = new Document(pageText, metadata);
                List<Document> chunks = textSplitter.split(pageDocument);
                int currentPage = page;
                // split may copy metadata map; ensure page marker exists
                chunks.forEach(doc -> doc.getMetadata().put("page", currentPage));

                allChunks.addAll(chunks);
            }

            log.info("parsed pdf for RAG: file={}, pages={}, chunks={}", file.getName(), pages, allChunks.size());
            return allChunks;
        } catch (IOException e) {
            log.error("failed to parse pdf: {}", filePath, e);
            throw e;
        }
    }

    /**
     * Simple whitespace cleanup to avoid overly noisy text
     */
    private String normalize(String text) {
        String condensed = text.replaceAll("[ \\t]+", " ");
        condensed = condensed.replaceAll("\\n{3,}", "\n\n");
        return condensed.trim();
    }

    @Override
    public List<String> supportedTypes() {
        return List.of("pdf");
    }
}

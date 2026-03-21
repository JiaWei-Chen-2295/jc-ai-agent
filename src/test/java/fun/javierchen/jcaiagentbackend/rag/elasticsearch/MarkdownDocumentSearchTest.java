package fun.javierchen.jcaiagentbackend.rag.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.javierchen.jcaiagentbackend.rag.infrastructure.parser.TikaDocumentParser;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.jupiter.api.*;
import org.springframework.ai.document.Document;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Markdown 文档存入 ES 并检索测试
 * 使用现有的 TikaDocumentParser 进行分块
 */
class MarkdownDocumentSearchTest {

    private static ElasticsearchClient client;
    private static RestClient restClient;
    private static TikaDocumentParser documentParser;
    private static final String INDEX_NAME = "test_documents";
    private static final String ES_URI = "http://localhost:9200";
    private static final String ES_USERNAME = "elastic";
    private static final String ES_PASSWORD = "598puGN6";
    private static final String DOC_PATH = "D:\\a_project_with_yupi\\f-ai-agent\\jc-ai-agent-backend\\upload_file\\1\\072dea09-920e-40ec-87bb-2e204f3d50ac\\提问的智慧.md";

    @BeforeAll
    static void setup() throws IOException {
        // 创建 ES Client
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(ES_USERNAME, ES_PASSWORD));

        RestClientBuilder builder = RestClient.builder(HttpHost.create(ES_URI))
                .setHttpClientConfigCallback(httpClientBuilder ->
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));

        restClient = builder.build();
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper(new ObjectMapper()));
        client = new ElasticsearchClient(transport);

        // 使用现有的 TikaDocumentParser
        documentParser = new TikaDocumentParser();

        // 创建索引
        createIndex();
    }

    @AfterAll
    static void cleanup() throws IOException {
//        client.indices().delete(d -> d.index(INDEX_NAME));
        System.out.println("已删除索引: " + INDEX_NAME);
        restClient.close();
    }

    private static void createIndex() throws IOException {
        client.indices().create(c -> c
                .index(INDEX_NAME)
                .mappings(m -> m
                        .properties("content", p -> p.text(t -> t))
                        .properties("tenant_id", p -> p.long_(l -> l))
                        .properties("source", p -> p.keyword(k -> k))
                        .properties("filename", p -> p.keyword(k -> k))));
        System.out.println("已创建索引: " + INDEX_NAME);
    }

    @Test
    void testIndexAndSearchMarkdown() throws Exception {
        Long tenantId = 1L;
        String docId = "doc-001";

        // 使用 TikaDocumentParser 解析文档（自动分块）
        List<Document> chunks = documentParser.parse(DOC_PATH);
        System.out.println("文档分块数量: " + chunks.size());

        assertFalse(chunks.isEmpty(), "文档应该被成功解析");

        // 存入 ES
        int chunkIndex = 0;
        for (Document chunk : chunks) {
            String chunkId = docId + "-chunk-" + chunkIndex++;
            client.index(i -> i
                    .index(INDEX_NAME)
                    .id(chunkId)
                    .document(new DocumentChunk(
                            chunk.getText(),
                            tenantId,
                            (String) chunk.getMetadata().get("source"),
                            (String) chunk.getMetadata().get("filename")
                    ))
                    .refresh(Refresh.False)); // 批量刷新更高效
        }
        // 手动刷新
        client.indices().refresh(r -> r.index(INDEX_NAME));
        System.out.println("已存入 " + chunks.size() + " 个分块");

        // 搜索测试：查找包含 "黑客" 的内容
        SearchResponse<DocumentChunk> response = client.search(s -> s
                        .index(INDEX_NAME)
                        .query(q -> q
                                .bool(b -> b
                                        .must(m -> m.match(mt -> mt.field("content").query("黑客")))
                                        .filter(f -> f.term(t -> t.field("tenant_id").value(tenantId)))))
                        .size(5),
                DocumentChunk.class);

        List<Hit<DocumentChunk>> hits = response.hits().hits();
        System.out.println("搜索 '黑客' 结果: " + hits.size() + " 条");

        assertFalse(hits.isEmpty(), "应该找到包含'黑客'的分块");
        hits.forEach(h -> {
            System.out.println("  - 内容片段: " + h.source().content.substring(0, Math.min(100, h.source().content.length())) + "...");
        });
        System.out.println("搜索验证通过");
    }

    /** 文档分块实体 */
    record DocumentChunk(
            String content,
            @JsonProperty("tenant_id") Long tenantId,
            String source,
            String filename
    ) {}
}

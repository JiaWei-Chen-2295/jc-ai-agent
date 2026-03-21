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
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Elasticsearch 关键词检索测试
 */
class ElasticsearchConnectionTest {

    private static ElasticsearchClient client;
    private static RestClient restClient;
    private static final String INDEX_NAME = "test_study_friends";
    private static final String ES_URI = "http://space-8d4j1xjn.ap-guangzhou.qcloudes.com";
    private static final String ES_USERNAME = "elastic";
    private static final String ES_PASSWORD = "UY03A!679,mh=asM4+2@";

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

        // 创建索引
        createIndex();
    }

    @AfterAll
    static void cleanup() throws IOException {
        // 删除索引
        client.indices().delete(d -> d.index(INDEX_NAME));
        System.out.println("已删除索引: " + INDEX_NAME);
        restClient.close();
    }

    private static void createIndex() throws IOException {
        client.indices().create(c -> c
                .index(INDEX_NAME)
                .mappings(m -> m
                        .properties("content", p -> p.text(t -> t))
                        .properties("tenant_id", p -> p.long_(l -> l))));
        System.out.println("已创建索引: " + INDEX_NAME);
    }

    @Test
    void testKeywordSearch() throws IOException {
        // 写入测试数据
        client.index(i -> i.index(INDEX_NAME).id("1").document(
                new StudyFriendDoc("Java 编程语言", 1L)).refresh(Refresh.True));
        client.index(i -> i.index(INDEX_NAME).id("2").document(
                new StudyFriendDoc("Python 编程语言", 1L)).refresh(Refresh.True));
        client.index(i -> i.index(INDEX_NAME).id("3").document(
                new StudyFriendDoc("Java 微服务框架", 2L)).refresh(Refresh.True));

        // 关键词检索
        SearchResponse<StudyFriendDoc> response = client.search(s -> s
                        .index(INDEX_NAME)
                        .query(q -> q
                                .bool(b -> b
                                        .must(m -> m.match(mt -> mt.field("content").query("Java")))
                                        .filter(f -> f.term(t -> t.field("tenant_id").value(1L))))),
                StudyFriendDoc.class);

        List<Hit<StudyFriendDoc>> hits = response.hits().hits();
        System.out.println("搜索结果: " + hits.size() + " 条");
        hits.forEach(h -> System.out.println("  - " + h.source()));

        assertEquals(1, hits.size());
        assertTrue(hits.get(0).source().content.contains("Java"));
    }

    /** ES 文档实体 */
    record StudyFriendDoc(
            String content,
            @JsonProperty("tenant_id") Long tenantId
    ) {}
}

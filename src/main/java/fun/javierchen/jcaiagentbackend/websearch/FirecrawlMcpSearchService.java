package fun.javierchen.jcaiagentbackend.websearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.javierchen.jcaiagentbackend.app.StudyFriendSource;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class FirecrawlMcpSearchService {

    private static final String FIRECRAWL_SEARCH_TOOL = "firecrawl_search";
    private static final int MAX_ATTEMPTS = 2;
    private static final String FIRECRAWL_SEARCH_API_URL = "https://api.firecrawl.dev/v2/search";

    private final WebSearchProperties properties;
    private final ObjectMapper objectMapper;

    public FirecrawlMcpSearchService(WebSearchProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public FirecrawlSearchResult search(String query) {
        if (!StringUtils.hasText(query) || !properties.isConfigured()) {
            return FirecrawlSearchResult.empty();
        }
        FirecrawlSearchResult mcpResult = searchViaMcp(query);
        if (mcpResult.hasSources()) {
            return mcpResult;
        }
        FirecrawlSearchResult apiResult = searchViaRestApi(query);
        if (apiResult.hasSources()) {
            log.info("FireCrawl web search fell back to REST API successfully for query={}", query);
        }
        return apiResult;
    }

    private FirecrawlSearchResult searchViaMcp(String query) {
        String mcpUrl = properties.resolveFirecrawlMcpUrl();
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try (McpSyncClient client = createClient(mcpUrl)) {
                client.initialize();
                McpSchema.CallToolResult toolResult = client.callTool(new McpSchema.CallToolRequest(FIRECRAWL_SEARCH_TOOL, buildArguments(query)));
                List<SourceDocument> documents = extractSourceDocuments(toolResult);
                if (documents.isEmpty()) {
                    return FirecrawlSearchResult.empty();
                }
                List<StudyFriendSource> sources = documents.stream()
                        .map(document -> new StudyFriendSource(document.title(), document.url(), document.snippet()))
                        .toList();
                return new FirecrawlSearchResult(buildContext(query, documents), sources);
            } catch (Exception e) {
                lastException = e;
                log.warn("FireCrawl MCP search attempt {}/{} failed for query={} url={}", attempt, MAX_ATTEMPTS, query, mcpUrl, e);
            }
        }
        log.warn("FireCrawl MCP search failed after {} attempts for query={} url={}", MAX_ATTEMPTS, query, mcpUrl, lastException);
        return FirecrawlSearchResult.empty();
    }

    private FirecrawlSearchResult searchViaRestApi(String query) {
        if (!StringUtils.hasText(properties.getFirecrawlApiKey())) {
            return FirecrawlSearchResult.empty();
        }
        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(properties.getConnectTimeout())
                    .build();
            String requestBody = objectMapper.writeValueAsString(buildRestSearchRequest(query));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(FIRECRAWL_SEARCH_API_URL))
                    .timeout(properties.getRequestTimeout())
                    .header("Authorization", "Bearer " + properties.getFirecrawlApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("FireCrawl REST search failed with status={} body={}", response.statusCode(), truncate(response.body()));
                return FirecrawlSearchResult.empty();
            }
            List<SourceDocument> documents = parseDocuments(response.body());
            if (documents.isEmpty()) {
                return FirecrawlSearchResult.empty();
            }
            documents = deduplicate(documents);
            List<StudyFriendSource> sources = documents.stream()
                    .map(document -> new StudyFriendSource(document.title(), document.url(), document.snippet()))
                    .toList();
            return new FirecrawlSearchResult(buildContext(query, documents), sources);
        } catch (Exception e) {
            log.warn("FireCrawl REST API fallback failed for query={}", query, e);
            return FirecrawlSearchResult.empty();
        }
    }

    private McpSyncClient createClient(String mcpUrl) {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(properties.getConnectTimeout());
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(mcpUrl)
                .clientBuilder(clientBuilder)
                .connectTimeout(properties.getConnectTimeout())
                .build();
        return McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("jc-ai-agent-backend", "0.0.1-SNAPSHOT"))
                .requestTimeout(properties.getRequestTimeout())
                .build();
    }

    private Map<String, Object> buildArguments(String query) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("query", query);
        arguments.put("limit", properties.getMaxResults());
        arguments.put("sources", List.of(Map.of("type", "web")));
        if (properties.isScrapeMainContent()) {
            arguments.put("scrapeOptions", Map.of(
                    "formats", List.of("markdown"),
                    "onlyMainContent", true
            ));
        }
        return arguments;
    }

    private Map<String, Object> buildRestSearchRequest(String query) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("query", query);
        request.put("limit", properties.getMaxResults());
        request.put("sources", List.of("web"));
        if (properties.isScrapeMainContent()) {
            request.put("scrapeOptions", Map.of(
                    "formats", List.of("markdown"),
                    "onlyMainContent", true
            ));
        }
        return request;
    }

    private List<SourceDocument> extractSourceDocuments(McpSchema.CallToolResult toolResult) {
        List<SourceDocument> documents = parseDocuments(toolResult.structuredContent());
        if (!documents.isEmpty()) {
            return deduplicate(documents);
        }
        if (toolResult.content() == null) {
            return List.of();
        }
        for (McpSchema.Content content : toolResult.content()) {
            if (content instanceof McpSchema.TextContent textContent) {
                documents = parseDocuments(textContent.text());
                if (!documents.isEmpty()) {
                    return deduplicate(documents);
                }
            }
        }
        return List.of();
    }

    private List<SourceDocument> parseDocuments(Object rawPayload) {
        if (rawPayload == null) {
            return List.of();
        }
        Object payload = rawPayload;
        if (rawPayload instanceof String text) {
            String trimmed = text.trim();
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                return List.of();
            }
            try {
                payload = objectMapper.readValue(trimmed, Object.class);
            } catch (JsonProcessingException e) {
                return List.of();
            }
        }
        Map<String, Object> payloadMap = objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {
        });
        Object webResults = payloadMap.get("web");
        if (!(webResults instanceof List<?>)) {
            Object data = payloadMap.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                webResults = dataMap.get("web");
            }
        }
        if (!(webResults instanceof List<?> items)) {
            return List.of();
        }
        List<SourceDocument> documents = new ArrayList<>();
        for (Object item : items) {
            Map<String, Object> itemMap = objectMapper.convertValue(item, new TypeReference<Map<String, Object>>() {
            });
            String url = asText(itemMap.get("url"));
            if (!StringUtils.hasText(url)) {
                continue;
            }
            String title = firstNonBlank(
                    asText(itemMap.get("title")),
                    asText(itemMap.get("metadataTitle")),
                    url
            );
            String snippet = firstNonBlank(
                    asText(itemMap.get("description")),
                    asText(itemMap.get("snippet")),
                    compactText(asText(itemMap.get("markdown")))
            );
            String content = compactText(asText(itemMap.get("markdown")));
            documents.add(new SourceDocument(title, url, truncate(snippet), truncate(content)));
        }
        return documents;
    }

    private List<SourceDocument> deduplicate(List<SourceDocument> documents) {
        Set<String> seenUrls = new LinkedHashSet<>();
        List<SourceDocument> deduplicated = new ArrayList<>();
        for (SourceDocument document : documents) {
            if (!seenUrls.add(document.url())) {
                continue;
            }
            deduplicated.add(document);
        }
        return deduplicated;
    }

    private String buildContext(String query, List<SourceDocument> documents) {
        StringBuilder builder = new StringBuilder();
        builder.append("联网搜索查询: ").append(query).append('\n');
        builder.append("以下是通过 FireCrawl 联网检索获取的网页资料，请优先参考这些资料回答。\n");
        int index = 1;
        for (SourceDocument document : documents) {
            builder.append(index++).append(". 标题: ").append(document.title()).append('\n');
            builder.append("链接: ").append(document.url()).append('\n');
            if (StringUtils.hasText(document.snippet())) {
                builder.append("摘要: ").append(document.snippet()).append('\n');
            }
            if (StringUtils.hasText(document.content())) {
                builder.append("正文摘录: ").append(document.content()).append('\n');
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private String compactText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.length() <= properties.getMaxContentChars()) {
            return text;
        }
        return text.substring(0, properties.getMaxContentChars()) + "...";
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private record SourceDocument(String title, String url, String snippet, String content) {
    }
}
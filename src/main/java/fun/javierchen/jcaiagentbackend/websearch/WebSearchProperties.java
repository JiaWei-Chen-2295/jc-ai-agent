package fun.javierchen.jcaiagentbackend.websearch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "jc-ai-agent.web-search")
public class WebSearchProperties {

    private boolean enabled = false;

    private int maxResults = 5;

    private boolean scrapeMainContent = true;

    private int maxContentChars = 1200;

    private Duration requestTimeout = Duration.ofSeconds(30);

    private Duration connectTimeout = Duration.ofSeconds(10);

    private String firecrawlApiKey;

    private String firecrawlMcpUrl;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public boolean isScrapeMainContent() {
        return scrapeMainContent;
    }

    public void setScrapeMainContent(boolean scrapeMainContent) {
        this.scrapeMainContent = scrapeMainContent;
    }

    public int getMaxContentChars() {
        return maxContentChars;
    }

    public void setMaxContentChars(int maxContentChars) {
        this.maxContentChars = maxContentChars;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public String getFirecrawlApiKey() {
        return firecrawlApiKey;
    }

    public void setFirecrawlApiKey(String firecrawlApiKey) {
        this.firecrawlApiKey = firecrawlApiKey;
    }

    public String getFirecrawlMcpUrl() {
        return firecrawlMcpUrl;
    }

    public void setFirecrawlMcpUrl(String firecrawlMcpUrl) {
        this.firecrawlMcpUrl = firecrawlMcpUrl;
    }

    public String resolveFirecrawlMcpUrl() {
        if (StringUtils.hasText(firecrawlMcpUrl)) {
            return firecrawlMcpUrl;
        }
        if (StringUtils.hasText(firecrawlApiKey)) {
            return "https://mcp.firecrawl.dev/" + firecrawlApiKey + "/v2/mcp";
        }
        return null;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(resolveFirecrawlMcpUrl());
    }
}
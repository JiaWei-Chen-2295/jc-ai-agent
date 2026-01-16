package fun.javierchen.jcaiagentbackend.rag.vectorstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.javierchen.jcaiagentbackend.common.TenantContextHolder;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantVectorStoreTest {

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void similaritySearch_usesTenantIdFromFilterExpressionWhenThreadLocalMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
                org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(Collections.emptyList());

        TenantVectorStore store = new TenantVectorStore(jdbcTemplate, embeddingModel, new ObjectMapper(), "study_friends");

        TenantContextHolder.clear();
        Filter.Expression filter = new FilterExpressionBuilder().eq("tenantId", "123").build();
        SearchRequest request = SearchRequest.builder()
                .query("hello")
                .topK(3)
                .filterExpression(filter)
                .build();

        assertDoesNotThrow(() -> store.similaritySearch(request));
    }

    @Test
    void similaritySearch_throwsWhenTenantMissingEverywhere() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f});
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
                org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(Collections.emptyList());

        TenantVectorStore store = new TenantVectorStore(jdbcTemplate, embeddingModel, new ObjectMapper(), "study_friends");

        TenantContextHolder.clear();
        SearchRequest request = SearchRequest.builder()
                .query("hello")
                .topK(3)
                .build();

        assertThrows(BusinessException.class, () -> store.similaritySearch(request));
    }
}

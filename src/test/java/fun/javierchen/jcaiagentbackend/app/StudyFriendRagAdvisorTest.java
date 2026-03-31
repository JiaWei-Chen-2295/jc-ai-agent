package fun.javierchen.jcaiagentbackend.app;

import fun.javierchen.jcaiagentbackend.rag.retrieval.HybridSearchVectorStore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class StudyFriendRagAdvisorTest {

    @Test
    void buildRagAdvisor_includesTenantFilterWhenTenantProvided() throws Exception {
        StudyFriend studyFriend = new StudyFriend(mock(ChatModelRegistry.class));

        VectorStore vectorStore = mock(HybridSearchVectorStore.class);
        setField(studyFriend, "hybridSearchVectorStore", vectorStore);

        Advisor advisor = invokeBuildRagAdvisor(studyFriend, "hello", 123L);
        assertInstanceOf(RetrievalAugmentationAdvisor.class, advisor);

        VectorStoreDocumentRetriever retriever = extractByType(advisor, VectorStoreDocumentRetriever.class);
        assertNotNull(retriever, "Should contain a VectorStoreDocumentRetriever");

        @SuppressWarnings("unchecked")
        Supplier<Filter.Expression> filterSupplier = extractByType(retriever, Supplier.class);
        assertNotNull(filterSupplier, "Should have a filter expression supplier when tenantId is provided");
        Filter.Expression filterExpression = filterSupplier.get();
        assertNotNull(filterExpression, "Filter expression should not be null");
        assertEquals(Filter.ExpressionType.EQ, filterExpression.type());
        assertTrue(filterExpression.left() instanceof Filter.Key);
        assertTrue(filterExpression.right() instanceof Filter.Value);
        assertEquals("tenantId", ((Filter.Key) filterExpression.left()).key());
        assertEquals("123", String.valueOf(((Filter.Value) filterExpression.right()).value()));
    }

    @Test
    void buildRagAdvisor_omitsTenantFilterWhenTenantNull() throws Exception {
        StudyFriend studyFriend = new StudyFriend(mock(ChatModelRegistry.class));
        setField(studyFriend, "hybridSearchVectorStore", mock(HybridSearchVectorStore.class));

        Advisor advisor = invokeBuildRagAdvisor(studyFriend, "hello", null);
        assertInstanceOf(RetrievalAugmentationAdvisor.class, advisor);

        VectorStoreDocumentRetriever retriever = extractByType(advisor, VectorStoreDocumentRetriever.class);
        assertNotNull(retriever);

        @SuppressWarnings("unchecked")
        Supplier<Filter.Expression> filterSupplier = extractByType(retriever, Supplier.class);
        // When no tenantId, filterExpression supplier should return null
        if (filterSupplier != null) {
            assertNull(filterSupplier.get(), "Filter expression should be null when tenantId is null");
        }
    }

    private static Advisor invokeBuildRagAdvisor(StudyFriend studyFriend, String chatMessage, Long tenantId)
            throws Exception {
        Method method = StudyFriend.class.getDeclaredMethod("buildRagAdvisor", String.class, Long.class);
        method.setAccessible(true);
        return (Advisor) method.invoke(studyFriend, chatMessage, tenantId);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        if (field == null) {
            fail("Field not found: " + name);
        }
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static <T> T extractByType(Object target, Class<T> type) throws IllegalAccessException {
        Class<?> current = target.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(target);
                if (value != null && type.isAssignableFrom(value.getClass())) {
                    return type.cast(value);
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}


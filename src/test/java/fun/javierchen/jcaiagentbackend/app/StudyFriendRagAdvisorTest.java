package fun.javierchen.jcaiagentbackend.app;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class StudyFriendRagAdvisorTest {

    @Test
    void buildRagAdvisor_includesTenantFilterWhenTenantProvided() throws Exception {
        StudyFriend studyFriend = new StudyFriend(mock(ChatModel.class));

        VectorStore vectorStore = mock(VectorStore.class);
        setField(studyFriend, "studyFriendPGvectorStore", vectorStore);

        QuestionAnswerAdvisor advisor = invokeBuildRagAdvisor(studyFriend, "hello", 123L);
        assertSame(vectorStore, extractByType(advisor, VectorStore.class));

        SearchRequest request = extractSearchRequest(advisor);
        assertEquals("hello", invokeNoArg(request, "getQuery", "query"));
        assertEquals(3, ((Number) invokeNoArg(request, "getTopK", "topK")).intValue());
        assertEquals(0.75d, ((Number) invokeNoArg(request, "getSimilarityThreshold", "similarityThreshold")).doubleValue(), 0.00001d);

        Object filterExpression = invokeNoArg(request, "getFilterExpression", "filterExpression");
        assertNotNull(filterExpression);
        assertTrue(filterExpression instanceof Filter.Expression);

        Filter.Expression expression = (Filter.Expression) filterExpression;
        assertEquals(Filter.ExpressionType.EQ, expression.type());
        assertTrue(expression.left() instanceof Filter.Key);
        assertTrue(expression.right() instanceof Filter.Value);
        assertEquals("tenantId", ((Filter.Key) expression.left()).key());
        assertEquals("123", String.valueOf(((Filter.Value) expression.right()).value()));
    }

    @Test
    void buildRagAdvisor_omitsTenantFilterWhenTenantNull() throws Exception {
        StudyFriend studyFriend = new StudyFriend(mock(ChatModel.class));
        setField(studyFriend, "studyFriendPGvectorStore", mock(VectorStore.class));

        QuestionAnswerAdvisor advisor = invokeBuildRagAdvisor(studyFriend, "hello", null);

        SearchRequest request = extractSearchRequest(advisor);
        Object filterExpression = invokeNoArg(request, "getFilterExpression", "filterExpression");
        assertNull(filterExpression);
    }

    private static QuestionAnswerAdvisor invokeBuildRagAdvisor(StudyFriend studyFriend, String chatMessage, Long tenantId)
            throws Exception {
        Method method = StudyFriend.class.getDeclaredMethod("buildRagAdvisor", String.class, Long.class);
        method.setAccessible(true);
        return (QuestionAnswerAdvisor) method.invoke(studyFriend, chatMessage, tenantId);
    }

    private static SearchRequest extractSearchRequest(QuestionAnswerAdvisor advisor) throws Exception {
        Object fromMethod = invokeNoArgIfExists(advisor, "getSearchRequest", "searchRequest");
        if (fromMethod instanceof SearchRequest) {
            return (SearchRequest) fromMethod;
        }
        SearchRequest fromField = extractByType(advisor, SearchRequest.class);
        if (fromField == null) {
            fail("Unable to extract SearchRequest from QuestionAnswerAdvisor (no method/field found)");
        }
        return fromField;
    }

    private static Object invokeNoArg(Object target, String... methodNames) throws Exception {
        Object result = invokeNoArgIfExists(target, methodNames);
        if (result != null) {
            return result;
        }
        for (String methodName : methodNames) {
            try {
                Field field = findField(target.getClass(), methodName);
                if (field != null) {
                    field.setAccessible(true);
                    return field.get(target);
                }
            } catch (ReflectiveOperationException ignored) {
                // fall through
            }
        }
        fail("Unable to read value via methods/fields: " + String.join(", ", methodNames));
        return null;
    }

    private static Object invokeNoArgIfExists(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // try next
            }
        }
        return null;
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
                if (type.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value != null) {
                        return type.cast(value);
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}


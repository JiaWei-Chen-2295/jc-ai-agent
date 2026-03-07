package fun.javierchen.jcaiagentbackend.utils;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量库过滤表达式构建工具类
 *
 * @author JavierChen
 */
public class VectorStoreFilterUtils {

    private VectorStoreFilterUtils() {
        // 工具类禁止实例化
    }

    /**
     * 构建文档ID过滤表达式
     * 支持单个或多个文档ID
     *
     * @param documentIds 文档ID列表
     * @return 过滤表达式，如果列表为空则返回 null
     */
    public static Filter.Expression buildDocumentIdFilter(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return null;
        }

        if (documentIds.size() == 1) {
            // 单个文档ID，直接使用 eq
            return new FilterExpressionBuilder()
                    .eq("documentId", documentIds.get(0).toString())
                    .build();
        }

        // 多个文档ID，使用 in 操作
        List<String> idStrings = documentIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
        return new FilterExpressionBuilder()
                .in("documentId", idStrings.toArray(new String[0]))
                .build();
    }

    /**
     * 构建租户ID过滤表达式
     *
     * @param tenantId 租户ID
     * @return 过滤表达式
     */
    public static Filter.Expression buildTenantIdFilter(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        return new FilterExpressionBuilder()
                .eq("tenantId", tenantId.toString())
                .build();
    }

    /**
     * 构建通用的等值过滤表达式
     *
     * @param key   元数据键名
     * @param value 元数据值
     * @return 过滤表达式
     */
    public static Filter.Expression buildEqFilter(String key, String value) {
        if (key == null || value == null) {
            return null;
        }
        return new FilterExpressionBuilder()
                .eq(key, value)
                .build();
    }
}

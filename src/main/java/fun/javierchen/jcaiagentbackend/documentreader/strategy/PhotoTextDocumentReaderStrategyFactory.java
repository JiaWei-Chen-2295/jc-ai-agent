package fun.javierchen.jcaiagentbackend.documentreader.strategy;

import fun.javierchen.jcaiagentbackend.documentreader.PhotoType;

import java.util.HashMap;
import java.util.Map;

public class PhotoTextDocumentReaderStrategyFactory {

    private static final Map<String, PhotoTextDocumentReaderStrategy> strategies = new HashMap<>();

    static {
        strategies.put("default", new DefaultPhotoTextDocumentReaderStrategy());
        strategies.put("json", new JSONPhotoTextDocumentReaderStrategy());
    }

    public static PhotoTextDocumentReaderStrategy getStrategy(String strategyName) {
        PhotoTextDocumentReaderStrategy strategy = strategies.get(strategyName);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown strategy: " + strategyName);
        }
        return strategy;
    }

    // 可选：允许运行时动态注册策略
    public static void registerStrategy(String name, PhotoTextDocumentReaderStrategy strategy) {
        strategies.put(name, strategy);
    }
}

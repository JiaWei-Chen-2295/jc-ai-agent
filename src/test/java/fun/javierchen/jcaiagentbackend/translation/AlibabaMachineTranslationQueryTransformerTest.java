package fun.javierchen.jcaiagentbackend.translation;

import fun.javierchen.jcaiagentbackend.rag.AlibabaMachineTranslationQueryTransformer;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.rag.Query;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class AlibabaMachineTranslationQueryTransformerTest {

    @Resource
    private AlibabaMachineTranslationQueryTransformer alibabaMachineTranslationQueryTransformer;

    @Test
    public void transform() {
        Query query = new Query("Hello, I am currently struggling to maintain a relationship after marriage. Please give me some advice");
        Query transformed = alibabaMachineTranslationQueryTransformer.transform(query);
        Assertions.assertNotNull(transformed.text());
        System.out.println(transformed.text());
    }
}

package fun.javierchen.jcaiagentbackend.translation;

import fun.javierchen.jcaiagentbackend.rag.AlibabaMachineTranslationQueryTransformer;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.rag.Query;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.annotation.BeforeTestExecution;

@SpringBootTest
public class AlibabaMachineTranslationQueryTransformerTest {
    @Resource
    private AlibabaMachineTranslationQueryTransformer alibabaMachineTranslationQueryTransformer;

    @Test
    public void transformEnglishToChinese() {
        Query query = new Query("Hello, I am currently struggling to maintain a relationship after marriage. Please give me some advice");
        Query transformed = alibabaMachineTranslationQueryTransformer.transform(query);
        Assertions.assertNotNull(transformed.text());
        System.out.println(transformed.text());
    }

    @Test
    public void transformJapaneseToEnglish() {
        alibabaMachineTranslationQueryTransformer.setTargetLanguage("en");
        Query query = new Query("結婚後の関係が親密でない場合、私はどうすればいいですか");
        Query transformed = alibabaMachineTranslationQueryTransformer.transform(query);
        Assertions.assertNotNull(transformed.text());
        System.out.println(transformed.text());
    }

    @Test
    public void transformJapaneseToChinese() {
        alibabaMachineTranslationQueryTransformer.setTargetLanguage("zh");
        Query query = new Query("結婚後の関係が親密でない場合、私はどうすればいいですか");
        Query transformed = alibabaMachineTranslationQueryTransformer.transform(query);
        Assertions.assertNotNull(transformed.text());
        System.out.println(transformed.text());
    }


}

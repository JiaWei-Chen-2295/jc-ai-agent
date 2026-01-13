package fun.javierchen.jcaiagentbackend.rag;

import fun.javierchen.jcaiagentbackend.rag.application.retrieval.MyMultiQueryExpander;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.rag.Query;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class MultiQueryExpanderTest {

    @Resource
    private MyMultiQueryExpander multiQueryExpander;

    @Test
    public void testMultiQueryExpander() {
        List<Query> expandQueryList = multiQueryExpander.expand(new Query("嘛是 Java ，俺爱 Java 。"));
        Assertions.assertNotNull(expandQueryList);
    }

}

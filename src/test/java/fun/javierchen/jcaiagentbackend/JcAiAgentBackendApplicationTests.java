package fun.javierchen.jcaiagentbackend;

import com.github.xiaoymin.knife4j.core.util.Assert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class JcAiAgentBackendApplicationTests {

    @Value("${jc-ai-agent.api_key}")
    private String apiKey;

    @Test
    void contextLoads() {
    }

    @Test
    public void readContextAPIKEY() {
        Assert.notNull(apiKey, "apiKey is null");
        System.out.println("当前的APIkey 是" + apiKey);
    }

}

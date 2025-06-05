package fun.javierchen.jcaiagentbackend.rag.config;

import fun.javierchen.jcaiagentbackend.rag.AlibabaMachineTranslationQueryTransformer;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AlibabaMachineTranslationConfig {

    @Resource
    private com.aliyun.teaopenapi.Client client;

    @Bean
    public AlibabaMachineTranslationQueryTransformer alibabaMachineTranslationQueryTransformer() {
        return AlibabaMachineTranslationQueryTransformer.builder()
                .client(client)
                .build();
    }

}

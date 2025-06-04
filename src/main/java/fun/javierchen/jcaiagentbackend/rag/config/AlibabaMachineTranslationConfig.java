package fun.javierchen.jcaiagentbackend.rag.config;

import fun.javierchen.jcaiagentbackend.rag.AlibabaMachineTranslationQueryTransformer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AlibabaMachineTranslationConfig {

    @Bean
    public AlibabaMachineTranslationQueryTransformer alibabaMachineTranslationQueryTransformer() {
        return new AlibabaMachineTranslationQueryTransformer();
    }


}

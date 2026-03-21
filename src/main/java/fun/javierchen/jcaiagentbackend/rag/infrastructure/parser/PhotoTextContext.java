package fun.javierchen.jcaiagentbackend.rag.infrastructure.parser;

import java.util.List;

public record PhotoTextContext(
        List<String> photoUrlList,
        PhotoType photoType
) {

}

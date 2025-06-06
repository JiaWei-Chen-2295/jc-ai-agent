package fun.javierchen.jcaiagentbackend.documentreader;

import java.util.List;

public record PhotoTextContext(
        List<String> photoUrlList,
        PhotoType photoType
) {

}

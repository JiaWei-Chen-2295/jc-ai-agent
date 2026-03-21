package fun.javierchen.jcaiagentbackend.rag.infrastructure.parser.ocr;

import fun.javierchen.jcaiagentbackend.rag.infrastructure.parser.PhotoTextContext;

public interface PhotoTextConvertor {

    String convert(PhotoTextContext context);

}

package fun.javierchen.jcaiagentbackend.documentreader;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum PhotoType {

    HANDWRITE("手写体"),
    PRINTED("印刷体");

    @Getter
    private final String typeString;
}

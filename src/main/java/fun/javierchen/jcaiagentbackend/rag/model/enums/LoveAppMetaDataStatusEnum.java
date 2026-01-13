package fun.javierchen.jcaiagentbackend.rag.model.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum LoveAppMetaDataStatusEnum {

    SINGLE_PERSON("单身"),

    MARRING_PERSON("已婚"),

    LOVING_PERSON("恋爱");

    private final String text;
}

package fun.javierchen.jcaiagentbackend.event;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum FileTypeEnum {

    IMAGE(1,"图片"),
    TEXT(2,"文本"),
    PDF(3,"PDF"),
    WORD(4,"WORD"),
    EXCEL(5,"EXCEL"),
    PPT(6,"PPT"),
    OTHER(7,"其他");

    private int type;
    private String desc;


}

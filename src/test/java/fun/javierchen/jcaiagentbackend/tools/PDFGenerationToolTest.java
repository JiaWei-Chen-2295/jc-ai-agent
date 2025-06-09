package fun.javierchen.jcaiagentbackend.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PDFGenerationToolTest {
    @Test
    public void testGeneratePDF() {
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        String s = pdfGenerationTool.generatePDF("Dokodemo_Door.pdf", """
                行天宫后
                二楼前座那个小房间
                日夜排练 我们听着唱片
                唱片来自
                那唱片行叫摇滚万岁
                和驻唱小店都在士林边缘
                我们都想
                离开这边追寻另一边
                苗栗孩子 搬到台北求学
                水手之子
                重考挤进信义路校园
                和高雄学弟
                当时看不顺眼
                我们曾走过
                无数地方和无尽岁月
                搭着肩环游
                无法遗忘的光辉世界
                那年我们都冲出南阳街
                任意门通向了音乐
                任意门外我们都任意的飞
                是自由的滋味
                七号公园
                初次登场是那个三月
                自强隧道
                漫长的像永远
                椰林大道
                谁放弃了律师的家业
                头也不回地越来越唱越远
                外滩风光
                跃出课本是那么新鲜
                从回民街再飞到尖沙咀
                男儿立志
                成名在望不论多遥远
                一离开台北
                却又想念台北
                我们曾走过
                无数地方和无尽岁月
                搭着肩环游
                无法遗忘的光辉世界
                无名高地到鸟巢的十年
                一路铺满汗水泪水
                任意门外我们用尽全力飞
                管他有多遥远
                我们曾走过
                无数地方和无尽岁月
                搭着肩环游
                无法遗忘的光辉世界
                那个唱片行 何时已不见
                是谁说过摇滚万岁
                任意门里我们偶尔也疲倦
                平凡的我们
                也将回到平凡的世界
                生活中充满孩子哭声
                柴米和油盐
                曾和你走过麦迪逊花园
                任意门外绕一大圈
                你问我全世界是哪里最美
                答案是你身边
                只要是你身边
                行天宫后
                二楼前座 那个小房间
                兽妈准备宵夜是大鸡腿
                每个梦都像任意门
                往不同世界
                而你的故事
                现在正是起点
                """);
        System.out.println(s);
    }
}
package fun.javierchen.jcaiagentbackend.constant;

import java.io.File;

/**
 * 文件常量
 *
 * @author JavierChen
 */
public interface FileConstant {

    /**
     * COS 访问地址
     * todo 需替换配置
     */
    String COS_HOST = "https://yupi.icu";

    String FILE_BASE_PATH = System.getProperty("user.dir") + File.separator + "tmp";
}

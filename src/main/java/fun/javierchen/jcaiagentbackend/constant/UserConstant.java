package fun.javierchen.jcaiagentbackend.constant;

/**
 * 用户常量
 *
 * @author JavierChen
 */
public interface UserConstant {

    /**
     * 用户登录态键
     */
    String USER_LOGIN_STATE = "user_login";

    /**
     * 当前租户 ID
     */
    String USER_ACTIVE_TENANT_ID = "active_tenant_id";

    //  region 权限

    /**
     * 默认角色
     */
    String DEFAULT_ROLE = "user";

    /**
     * 管理员角色
     */
    String ADMIN_ROLE = "admin";

    /**
     * 被封号
     */
    String BAN_ROLE = "ban";

    // endregion
}

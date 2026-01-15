package fun.javierchen.jcaiagentbackend.service;

import fun.javierchen.jcaiagentbackend.controller.dto.TenantVO;
import fun.javierchen.jcaiagentbackend.model.entity.User;

import java.util.List;

public interface TenantService {

    /**
     * Ensure the personal tenant exists and return its ID.
     */
    Long ensurePersonalTenant(User user);

    /**
     * Create a team and set the creator as admin.
     */
    TenantVO createTeam(String tenantName, User loginUser);

    /**
     * List all tenants the user joined (including personal).
     */
    List<TenantVO> listMyTenants(Long userId);

    /**
     * Join the specified tenant.
     */
    void joinTenant(Long tenantId, Long userId);

    /**
     * Leave the specified tenant; auto-transfer admin if needed.
     */
    void leaveTenant(Long tenantId, Long userId);

    /**
     * Transfer admin role to another member.
     */
    void transferAdmin(Long tenantId, Long newAdminUserId, Long operatorUserId);

    /**
     * Check if the user is a member of the tenant.
     */
    boolean isMember(Long tenantId, Long userId);

    /**
     * Check if the user is an admin of the tenant.
     */
    boolean isAdmin(Long tenantId, Long userId);

    /**
     * Require membership or throw.
     */
    void requireMember(Long tenantId, Long userId);

    /**
     * Require admin role or throw.
     */
    void requireAdmin(Long tenantId, Long userId);

    /**
     * Get the personal tenant ID for the user.
     */
    Long getPersonalTenantId(Long userId);
}

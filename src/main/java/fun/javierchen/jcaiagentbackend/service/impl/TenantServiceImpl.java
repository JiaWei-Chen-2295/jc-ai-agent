package fun.javierchen.jcaiagentbackend.service.impl;

import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.constant.TenantConstant;
import fun.javierchen.jcaiagentbackend.controller.dto.TenantVO;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import fun.javierchen.jcaiagentbackend.exception.ThrowUtils;
import fun.javierchen.jcaiagentbackend.model.entity.Tenant;
import fun.javierchen.jcaiagentbackend.model.entity.TenantUser;
import fun.javierchen.jcaiagentbackend.model.entity.User;
import fun.javierchen.jcaiagentbackend.repository.TenantRepository;
import fun.javierchen.jcaiagentbackend.repository.TenantUserRepository;
import fun.javierchen.jcaiagentbackend.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private static final int DEFAULT_IS_DELETE = 0;

    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;

    @Value("${jc-ai-agent.tenant.max-create-teams:5}")
    private int maxCreateTeams;

    /**
     * Create or restore a personal tenant and admin membership.
     */
    @Override
    @Transactional
    public Long ensurePersonalTenant(User user) {
        ThrowUtils.throwIf(user == null || user.getId() == null, ErrorCode.PARAMS_ERROR, "用户信息为空");
        Optional<Tenant> existing = tenantRepository.findFirstByOwnerUserIdAndTenantTypeAndIsDelete(
                user.getId(), TenantConstant.TENANT_TYPE_PERSONAL, DEFAULT_IS_DELETE);

        Tenant tenant;
        if (existing.isPresent()) {
            tenant = existing.get();
        } else {
            tenant = new Tenant();
            tenant.setTenantType(TenantConstant.TENANT_TYPE_PERSONAL);
            tenant.setOwnerUserId(user.getId());
            tenant.setTenantName(buildPersonalTenantName(user));
            tenant = tenantRepository.save(tenant);
        }

        Optional<TenantUser> membership = tenantUserRepository.findByTenantIdAndUserIdAndIsDelete(
                tenant.getId(), user.getId(), DEFAULT_IS_DELETE);
        if (!membership.isPresent()) {
            Optional<TenantUser> history = tenantUserRepository.findByTenantIdAndUserId(tenant.getId(), user.getId());
            if (history.isPresent()) {
                TenantUser tenantUser = history.get();
                tenantUser.setIsDelete(DEFAULT_IS_DELETE);
                tenantUser.setRole(TenantConstant.TENANT_ROLE_ADMIN);
                tenantUserRepository.save(tenantUser);
            } else {
                TenantUser tenantUser = new TenantUser();
                tenantUser.setTenantId(tenant.getId());
                tenantUser.setUserId(user.getId());
                tenantUser.setRole(TenantConstant.TENANT_ROLE_ADMIN);
                tenantUserRepository.save(tenantUser);
            }
        }

        return tenant.getId();
    }

    /**
     * Create a team and persist admin membership.
     */
    @Override
    @Transactional
    public TenantVO createTeam(String tenantName, User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        String name = StringUtils.trimToEmpty(tenantName);
        ThrowUtils.throwIf(name.isEmpty(), ErrorCode.PARAMS_ERROR, "团队名称不能为空");

        long createdCount = tenantRepository.countByOwnerUserIdAndTenantTypeAndIsDelete(
                loginUser.getId(), TenantConstant.TENANT_TYPE_TEAM, DEFAULT_IS_DELETE);
        ThrowUtils.throwIf(createdCount >= maxCreateTeams, ErrorCode.OPERATION_ERROR, "创建团队数量已达上限");

        Tenant tenant = new Tenant();
        tenant.setTenantName(name);
        tenant.setTenantType(TenantConstant.TENANT_TYPE_TEAM);
        tenant.setOwnerUserId(loginUser.getId());
        tenant = tenantRepository.save(tenant);

        TenantUser tenantUser = new TenantUser();
        tenantUser.setTenantId(tenant.getId());
        tenantUser.setUserId(loginUser.getId());
        tenantUser.setRole(TenantConstant.TENANT_ROLE_ADMIN);
        tenantUserRepository.save(tenantUser);

        return toTenantVO(tenant, tenantUser);
    }

    /**
     * List all tenants for the user.
     */
    @Override
    public List<TenantVO> listMyTenants(Long userId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户不存在");
        List<TenantUser> memberships = tenantUserRepository.findByUserIdAndIsDelete(userId, DEFAULT_IS_DELETE);
        if (memberships.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> tenantIds = new ArrayList<>();
        for (TenantUser membership : memberships) {
            tenantIds.add(membership.getTenantId());
        }
        List<Tenant> tenants = tenantRepository.findByIdInAndIsDelete(tenantIds, DEFAULT_IS_DELETE);
        Map<Long, Tenant> tenantMap = new HashMap<>();
        for (Tenant tenant : tenants) {
            tenantMap.put(tenant.getId(), tenant);
        }
        List<TenantVO> result = new ArrayList<>();
        for (TenantUser membership : memberships) {
            Tenant tenant = tenantMap.get(membership.getTenantId());
            if (tenant == null) {
                continue;
            }
            result.add(toTenantVO(tenant, membership));
        }
        return result;
    }

    /**
     * Join a tenant and restore membership if previously left.
     */
    @Override
    @Transactional
    public void joinTenant(Long tenantId, Long userId) {
        ThrowUtils.throwIf(tenantId == null || userId == null, ErrorCode.PARAMS_ERROR);
        Tenant tenant = tenantRepository.findByIdAndIsDelete(tenantId, DEFAULT_IS_DELETE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "团队不存在"));
        ThrowUtils.throwIf(TenantConstant.TENANT_TYPE_PERSONAL.equals(tenant.getTenantType()),
                ErrorCode.NO_AUTH_ERROR, "不能加入个人团队");

        Optional<TenantUser> existing = tenantUserRepository.findByTenantIdAndUserIdAndIsDelete(
                tenantId, userId, DEFAULT_IS_DELETE);
        if (existing.isPresent()) {
            return;
        }
        Optional<TenantUser> history = tenantUserRepository.findByTenantIdAndUserId(tenantId, userId);
        if (history.isPresent()) {
            TenantUser tenantUser = history.get();
            tenantUser.setIsDelete(DEFAULT_IS_DELETE);
            tenantUser.setRole(TenantConstant.TENANT_ROLE_MEMBER);
            tenantUserRepository.save(tenantUser);
            return;
        }
        TenantUser tenantUser = new TenantUser();
        tenantUser.setTenantId(tenantId);
        tenantUser.setUserId(userId);
        tenantUser.setRole(TenantConstant.TENANT_ROLE_MEMBER);
        tenantUserRepository.save(tenantUser);
    }

    /**
     * Leave a tenant; auto-transfer admin by join order.
     */
    @Override
    @Transactional
    public void leaveTenant(Long tenantId, Long userId) {
        ThrowUtils.throwIf(tenantId == null || userId == null, ErrorCode.PARAMS_ERROR);
        TenantUser membership = tenantUserRepository.findByTenantIdAndUserIdAndIsDelete(
                tenantId, userId, DEFAULT_IS_DELETE).orElseThrow(() -> new BusinessException(ErrorCode.NO_AUTH_ERROR, "未加入该团队"));

        Tenant tenant = tenantRepository.findByIdAndIsDelete(tenantId, DEFAULT_IS_DELETE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "团队不存在"));

        boolean isAdmin = TenantConstant.TENANT_ROLE_ADMIN.equals(membership.getRole());
        membership.setIsDelete(1);
        tenantUserRepository.save(membership);

        if (!isAdmin) {
            return;
        }

        Optional<TenantUser> nextAdmin = tenantUserRepository
                .findFirstByTenantIdAndIsDeleteAndUserIdNotOrderByCreateTimeAscIdAsc(
                        tenantId, DEFAULT_IS_DELETE, userId);
        if (nextAdmin.isPresent()) {
            TenantUser newAdmin = nextAdmin.get();
            newAdmin.setRole(TenantConstant.TENANT_ROLE_ADMIN);
            tenantUserRepository.save(newAdmin);

            tenant.setOwnerUserId(newAdmin.getUserId());
            tenantRepository.save(tenant);
        } else {
            tenant.setIsDelete(1);
            tenantRepository.save(tenant);
        }
    }

    /**
     * Transfer admin role to another member.
     */
    @Override
    @Transactional
    public void transferAdmin(Long tenantId, Long newAdminUserId, Long operatorUserId) {
        ThrowUtils.throwIf(tenantId == null || newAdminUserId == null || operatorUserId == null, ErrorCode.PARAMS_ERROR);
        Tenant tenant = tenantRepository.findByIdAndIsDelete(tenantId, DEFAULT_IS_DELETE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "团队不存在"));
        ThrowUtils.throwIf(TenantConstant.TENANT_TYPE_PERSONAL.equals(tenant.getTenantType()),
                ErrorCode.NO_AUTH_ERROR, "个人团队不支持转让");

        requireAdmin(tenantId, operatorUserId);
        if (newAdminUserId.equals(operatorUserId)) {
            return;
        }
        TenantUser newAdmin = tenantUserRepository.findByTenantIdAndUserIdAndIsDelete(
                tenantId, newAdminUserId, DEFAULT_IS_DELETE)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAMS_ERROR, "新管理员不是团队成员"));

        TenantUser currentAdmin = tenantUserRepository.findByTenantIdAndUserIdAndIsDelete(
                tenantId, operatorUserId, DEFAULT_IS_DELETE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限"));

        currentAdmin.setRole(TenantConstant.TENANT_ROLE_MEMBER);
        newAdmin.setRole(TenantConstant.TENANT_ROLE_ADMIN);
        tenantUserRepository.save(currentAdmin);
        tenantUserRepository.save(newAdmin);

        tenant.setOwnerUserId(newAdminUserId);
        tenantRepository.save(tenant);
    }

    /**
     * Check if the user is a tenant member.
     */
    @Override
    public boolean isMember(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return false;
        }
        return tenantUserRepository.findByTenantIdAndUserIdAndIsDelete(tenantId, userId, DEFAULT_IS_DELETE).isPresent();
    }

    /**
     * Check if the user is a tenant admin.
     */
    @Override
    public boolean isAdmin(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return false;
        }
        Optional<TenantUser> membership = tenantUserRepository.findByTenantIdAndUserIdAndIsDelete(
                tenantId, userId, DEFAULT_IS_DELETE);
        return membership.isPresent() && TenantConstant.TENANT_ROLE_ADMIN.equals(membership.get().getRole());
    }

    /**
     * Require membership or throw.
     */
    @Override
    public void requireMember(Long tenantId, Long userId) {
        ThrowUtils.throwIf(!isMember(tenantId, userId), ErrorCode.NO_AUTH_ERROR, "未加入该团队");
    }

    /**
     * Require admin role or throw.
     */
    @Override
    public void requireAdmin(Long tenantId, Long userId) {
        ThrowUtils.throwIf(!isAdmin(tenantId, userId), ErrorCode.NO_AUTH_ERROR, "不是团队管理员");
    }

    /**
     * Get personal tenant ID or null.
     */
    @Override
    public Long getPersonalTenantId(Long userId) {
        Optional<Tenant> tenant = tenantRepository.findFirstByOwnerUserIdAndTenantTypeAndIsDelete(
                userId, TenantConstant.TENANT_TYPE_PERSONAL, DEFAULT_IS_DELETE);
        return tenant.map(Tenant::getId).orElse(null);
    }

    /**
     * Build a personal tenant name.
     */
    private String buildPersonalTenantName(User user) {
        String base = StringUtils.trimToNull(user.getUserName());
        if (base == null) {
            base = StringUtils.trimToNull(user.getUserAccount());
        }
        if (base == null) {
            base = "user-" + user.getId();
        }
        return base + "-personal";
    }

    /**
     * Map tenant and membership into a view object.
     */
    private TenantVO toTenantVO(Tenant tenant, TenantUser membership) {
        TenantVO vo = new TenantVO();
        vo.setId(tenant.getId());
        vo.setTenantName(tenant.getTenantName());
        vo.setTenantType(tenant.getTenantType());
        vo.setOwnerUserId(tenant.getOwnerUserId());
        vo.setRole(membership.getRole());
        vo.setCreateTime(tenant.getCreateTime());
        vo.setUpdateTime(tenant.getUpdateTime());
        return vo;
    }
}

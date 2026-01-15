package fun.javierchen.jcaiagentbackend.controller;

import fun.javierchen.jcaiagentbackend.common.BaseResponse;
import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.common.ResultUtils;
import fun.javierchen.jcaiagentbackend.constant.UserConstant;
import fun.javierchen.jcaiagentbackend.controller.dto.TenantCreateRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.TenantJoinRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.TenantLeaveRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.TenantSetActiveRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.TenantTransferAdminRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.TenantVO;
import fun.javierchen.jcaiagentbackend.exception.ThrowUtils;
import fun.javierchen.jcaiagentbackend.model.entity.User;
import fun.javierchen.jcaiagentbackend.service.TenantService;
import fun.javierchen.jcaiagentbackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
@Tag(name = "团队管理", description = "团队（租户）创建、加入、退出与切换")
public class TenantController {

    private final TenantService tenantService;
    private final UserService userService;

    /**
     * Create a team; creator becomes admin.
     */
    @PostMapping("/create")
    @Operation(summary = "创建团队")
    public BaseResponse<TenantVO> createTeam(@RequestBody TenantCreateRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        TenantVO tenantVO = tenantService.createTeam(request.getTenantName(), loginUser);
        return ResultUtils.success(tenantVO);
    }

    /**
     * List tenants the current user joined.
     */
    @GetMapping("/list")
    @Operation(summary = "查询我加入的团队")
    public BaseResponse<List<TenantVO>> listMyTenants(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        List<TenantVO> tenants = tenantService.listMyTenants(loginUser.getId());
        return ResultUtils.success(tenants);
    }

    /**
     * Join a tenant.
     */
    @PostMapping("/join")
    @Operation(summary = "加入团队")
    public BaseResponse<Boolean> joinTenant(@RequestBody TenantJoinRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getTenantId() == null, ErrorCode.PARAMS_ERROR);
        tenantService.joinTenant(request.getTenantId(), loginUser.getId());
        return ResultUtils.success(true);
    }

    /**
     * Leave a tenant; admin exits trigger transfer.
     */
    @PostMapping("/leave")
    @Operation(summary = "退出团队")
    public BaseResponse<Boolean> leaveTenant(@RequestBody TenantLeaveRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getTenantId() == null, ErrorCode.PARAMS_ERROR);
        tenantService.leaveTenant(request.getTenantId(), loginUser.getId());
        refreshActiveTenantIfNeeded(request.getTenantId(), loginUser.getId(), httpRequest);
        return ResultUtils.success(true);
    }

    /**
     * Transfer admin to another member.
     */
    @PostMapping("/transfer-admin")
    @Operation(summary = "管理员转让")
    public BaseResponse<Boolean> transferAdmin(@RequestBody TenantTransferAdminRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getTenantId() == null || request.getNewAdminUserId() == null,
                ErrorCode.PARAMS_ERROR);
        tenantService.transferAdmin(request.getTenantId(), request.getNewAdminUserId(), loginUser.getId());
        return ResultUtils.success(true);
    }

    /**
     * Switch active tenant in session.
     */
    @PostMapping("/active")
    @Operation(summary = "切换当前团队")
    public BaseResponse<Boolean> setActiveTenant(@RequestBody TenantSetActiveRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getTenantId() == null, ErrorCode.PARAMS_ERROR);
        tenantService.requireMember(request.getTenantId(), loginUser.getId());
        HttpSession session = httpRequest.getSession();
        session.setAttribute(UserConstant.USER_ACTIVE_TENANT_ID, request.getTenantId());
        return ResultUtils.success(true);
    }

    /**
     * Fallback to personal tenant when leaving active tenant.
     */
    private void refreshActiveTenantIfNeeded(Long tenantId, Long userId, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        Long activeTenantId = parseTenantId(session.getAttribute(UserConstant.USER_ACTIVE_TENANT_ID));
        if (activeTenantId != null && activeTenantId.equals(tenantId)) {
            Long personalTenantId = tenantService.getPersonalTenantId(userId);
            if (personalTenantId != null) {
                session.setAttribute(UserConstant.USER_ACTIVE_TENANT_ID, personalTenantId);
            } else {
                session.removeAttribute(UserConstant.USER_ACTIVE_TENANT_ID);
            }
        }
    }

    /**
     * Parse tenant id from session attribute.
     */
    private Long parseTenantId(Object tenantIdObj) {
        if (tenantIdObj == null) {
            return null;
        }
        if (tenantIdObj instanceof Long) {
            return (Long) tenantIdObj;
        }
        try {
            return Long.valueOf(tenantIdObj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

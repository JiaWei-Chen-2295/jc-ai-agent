package fun.javierchen.jcaiagentbackend.controller;

import fun.javierchen.jcaiagentbackend.common.BaseResponse;
import fun.javierchen.jcaiagentbackend.common.DeleteRequest;
import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.common.ResultUtils;
import fun.javierchen.jcaiagentbackend.controller.dto.UserCreateRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserLoginRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserQueryRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserRegisterRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserUpdateMyRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserUpdateRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserVO;
import fun.javierchen.jcaiagentbackend.exception.ThrowUtils;
import fun.javierchen.jcaiagentbackend.model.entity.User;
import fun.javierchen.jcaiagentbackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户管理与认证接口")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest request) {
        long userId = userService.userRegister(request);
        return ResultUtils.success(userId);
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public BaseResponse<UserVO> userLogin(@RequestBody UserLoginRequest request, HttpServletRequest httpRequest) {
        UserVO userVO = userService.userLogin(request, httpRequest);
        return ResultUtils.success(userVO);
    }

    @PostMapping("/logout")
    @Operation(summary = "用户退出登录")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    @GetMapping("/current")
    @Operation(summary = "获取当前登录用户")
    public BaseResponse<UserVO> getCurrentUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getUserVO(loginUser));
    }

    @PostMapping("/add")
    @Operation(summary = "新增用户（管理员）")
    public BaseResponse<Long> addUser(@RequestBody UserCreateRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);
        long userId = userService.addUser(request);
        return ResultUtils.success(userId);
    }

    @PostMapping("/delete")
    @Operation(summary = "删除用户（管理员）")
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);
        boolean result = userService.deleteUser(deleteRequest.getId());
        return ResultUtils.success(result);
    }

    @PostMapping("/update")
    @Operation(summary = "更新用户（管理员）")
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);
        boolean result = userService.updateUser(request);
        return ResultUtils.success(result);
    }

    @PostMapping("/update/my")
    @Operation(summary = "更新我的资料")
    public BaseResponse<UserVO> updateMyUser(@RequestBody UserUpdateMyRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        UserVO result = userService.updateMyUser(request, loginUser);
        return ResultUtils.success(result);
    }

    @GetMapping("/get")
    @Operation(summary = "根据 id 获取用户（管理员）")
    public BaseResponse<UserVO> getUserById(@RequestParam("id") long id, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);
        UserVO userVO = userService.getUserVOById(id);
        return ResultUtils.success(userVO);
    }

    @GetMapping("/list")
    @Operation(summary = "查询用户列表（管理员）")
    public BaseResponse<List<UserVO>> listUser(UserQueryRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);
        List<UserVO> userVOList = userService.listUsers(request);
        return ResultUtils.success(userVOList);
    }

    @PostMapping("/list/page")
    @Operation(summary = "分页查询用户（管理员）")
    public BaseResponse<Page<UserVO>> listUserByPage(@RequestBody UserQueryRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);
        Page<UserVO> page = userService.listUserByPage(request);
        return ResultUtils.success(page);
    }
}

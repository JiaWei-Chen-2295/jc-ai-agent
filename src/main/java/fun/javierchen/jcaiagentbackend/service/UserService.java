package fun.javierchen.jcaiagentbackend.service;

import fun.javierchen.jcaiagentbackend.controller.dto.UserCreateRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserLoginRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserQueryRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserRegisterRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserUpdateMyRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserUpdateRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserVO;
import fun.javierchen.jcaiagentbackend.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;

import java.util.List;

public interface UserService {

    long userRegister(UserRegisterRequest request);

    UserVO userLogin(UserLoginRequest request, HttpServletRequest httpRequest);

    boolean userLogout(HttpServletRequest request);

    User getLoginUser(HttpServletRequest request);

    boolean isAdmin(User user);

    UserVO getUserVO(User user);

    List<UserVO> getUserVOList(List<User> userList);

    long addUser(UserCreateRequest request);

    boolean updateUser(UserUpdateRequest request);

    boolean deleteUser(long id);

    UserVO getUserVOById(long id);

    List<UserVO> listUsers(UserQueryRequest request);

    Page<UserVO> listUserByPage(UserQueryRequest request);

    UserVO updateMyUser(UserUpdateMyRequest request, User loginUser);
}

package fun.javierchen.jcaiagentbackend.service.impl;

import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.constant.CommonConstant;
import fun.javierchen.jcaiagentbackend.constant.UserConstant;
import fun.javierchen.jcaiagentbackend.controller.dto.UserCreateRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserLoginRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserQueryRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserRegisterRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserUpdateMyRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserUpdateRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserVO;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import fun.javierchen.jcaiagentbackend.exception.ThrowUtils;
import fun.javierchen.jcaiagentbackend.model.entity.User;
import fun.javierchen.jcaiagentbackend.repository.UserRepository;
import fun.javierchen.jcaiagentbackend.service.UserService;
import fun.javierchen.jcaiagentbackend.utils.SqlUtils;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final int DEFAULT_IS_DELETE = 0;
    private static final int MIN_ACCOUNT_LENGTH = 4;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PAGE_SIZE = 50;

    private static final Set<String> ALLOWED_SORT_FIELDS = new HashSet<>(
            List.of("id", "userAccount", "userName", "userRole", "createTime", "updateTime")
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    @Override
    @Transactional
    public long userRegister(UserRegisterRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        String userAccount = StringUtils.trimToEmpty(request.getUserAccount());
        String userPassword = StringUtils.defaultString(request.getUserPassword());
        String checkPassword = StringUtils.defaultString(request.getCheckPassword());

        ThrowUtils.throwIf(StringUtils.isAnyBlank(userAccount, userPassword, checkPassword),
                ErrorCode.PARAMS_ERROR, "Account and password are required");
        validateAccount(userAccount);
        validatePassword(userPassword);
        ThrowUtils.throwIf(!userPassword.equals(checkPassword), ErrorCode.PARAMS_ERROR, "Passwords do not match");
        ThrowUtils.throwIf(userRepository.existsByUserAccountAndIsDelete(userAccount, DEFAULT_IS_DELETE),
                ErrorCode.PARAMS_ERROR, "Account already exists");

        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(passwordEncoder.encode(userPassword));
        user.setUserName(StringUtils.trimToNull(request.getUserName()));
        user.setUserRole(UserConstant.DEFAULT_ROLE);
        user.setIsDelete(DEFAULT_IS_DELETE);

        userRepository.save(user);
        return user.getId();
    }

    @Override
    public UserVO userLogin(UserLoginRequest request, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        String userAccount = StringUtils.trimToEmpty(request.getUserAccount());
        String userPassword = StringUtils.defaultString(request.getUserPassword());

        ThrowUtils.throwIf(StringUtils.isAnyBlank(userAccount, userPassword),
                ErrorCode.PARAMS_ERROR, "Account and password are required");
        validateAccount(userAccount);

        User user = userRepository.findByUserAccountAndIsDelete(userAccount, DEFAULT_IS_DELETE)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAMS_ERROR, "Invalid credentials"));

        ThrowUtils.throwIf(!passwordEncoder.matches(userPassword, user.getUserPassword()),
                ErrorCode.PARAMS_ERROR, "Invalid credentials");
        ThrowUtils.throwIf(UserConstant.BAN_ROLE.equals(user.getUserRole()),
                ErrorCode.FORBIDDEN_ERROR, "User is banned");

        httpRequest.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user.getId());
        return getUserVO(user);
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        if (request == null) {
            return true;
        }
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        Object userIdObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userIdObj == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        Long userId = parseUserId(userIdObj);
        User user = userRepository.findByIdAndIsDelete(userId, DEFAULT_IS_DELETE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGIN_ERROR));
        ThrowUtils.throwIf(UserConstant.BAN_ROLE.equals(user.getUserRole()),
                ErrorCode.FORBIDDEN_ERROR, "User is banned");
        return user;
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserConstant.ADMIN_ROLE.equals(user.getUserRole());
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        return new UserVO(
                user.getId(),
                user.getUserAccount(),
                user.getUserName(),
                user.getUserAvatar(),
                user.getUserProfile(),
                user.getUserRole(),
                user.getCreateTime(),
                user.getUpdateTime()
        );
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (userList == null || userList.isEmpty()) {
            return List.of();
        }
        return userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public long addUser(UserCreateRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        String userAccount = StringUtils.trimToEmpty(request.getUserAccount());
        String userPassword = StringUtils.defaultString(request.getUserPassword());

        ThrowUtils.throwIf(StringUtils.isAnyBlank(userAccount, userPassword),
                ErrorCode.PARAMS_ERROR, "Account and password are required");
        validateAccount(userAccount);
        validatePassword(userPassword);
        ThrowUtils.throwIf(userRepository.existsByUserAccountAndIsDelete(userAccount, DEFAULT_IS_DELETE),
                ErrorCode.PARAMS_ERROR, "Account already exists");

        String role = StringUtils.trimToNull(request.getUserRole());
        if (role != null) {
            validateRole(role);
        } else {
            role = UserConstant.DEFAULT_ROLE;
        }

        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(passwordEncoder.encode(userPassword));
        user.setUserName(StringUtils.trimToNull(request.getUserName()));
        user.setUserAvatar(StringUtils.trimToNull(request.getUserAvatar()));
        user.setUserProfile(StringUtils.trimToNull(request.getUserProfile()));
        user.setUserRole(role);
        user.setUnionId(StringUtils.trimToNull(request.getUnionId()));
        user.setMpOpenId(StringUtils.trimToNull(request.getMpOpenId()));
        user.setIsDelete(DEFAULT_IS_DELETE);

        userRepository.save(user);
        return user.getId();
    }

    @Override
    @Transactional
    public boolean updateUser(UserUpdateRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null || request.getId() <= 0,
                ErrorCode.PARAMS_ERROR, "User id is required");

        User user = userRepository.findByIdAndIsDelete(request.getId(), DEFAULT_IS_DELETE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "User not found"));

        if (request.getUserAccount() != null) {
            String userAccount = StringUtils.trimToEmpty(request.getUserAccount());
            ThrowUtils.throwIf(StringUtils.isBlank(userAccount), ErrorCode.PARAMS_ERROR, "Account is required");
            validateAccount(userAccount);
            if (!userAccount.equals(user.getUserAccount())
                    && userRepository.existsByUserAccountAndIsDelete(userAccount, DEFAULT_IS_DELETE)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "Account already exists");
            }
            user.setUserAccount(userAccount);
        }

        if (request.getUserPassword() != null) {
            String userPassword = StringUtils.defaultString(request.getUserPassword());
            ThrowUtils.throwIf(StringUtils.isBlank(userPassword), ErrorCode.PARAMS_ERROR, "Password is required");
            validatePassword(userPassword);
            user.setUserPassword(passwordEncoder.encode(userPassword));
        }

        if (request.getUserName() != null) {
            user.setUserName(StringUtils.trimToNull(request.getUserName()));
        }
        if (request.getUserAvatar() != null) {
            user.setUserAvatar(StringUtils.trimToNull(request.getUserAvatar()));
        }
        if (request.getUserProfile() != null) {
            user.setUserProfile(StringUtils.trimToNull(request.getUserProfile()));
        }
        if (request.getUserRole() != null) {
            String role = StringUtils.trimToEmpty(request.getUserRole());
            ThrowUtils.throwIf(StringUtils.isBlank(role), ErrorCode.PARAMS_ERROR, "Role is required");
            validateRole(role);
            user.setUserRole(role);
        }
        if (request.getUnionId() != null) {
            user.setUnionId(StringUtils.trimToNull(request.getUnionId()));
        }
        if (request.getMpOpenId() != null) {
            user.setMpOpenId(StringUtils.trimToNull(request.getMpOpenId()));
        }

        userRepository.save(user);
        return true;
    }

    @Override
    @Transactional
    public boolean deleteUser(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "User id is required");
        User user = userRepository.findByIdAndIsDelete(id, DEFAULT_IS_DELETE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "User not found"));
        user.setIsDelete(1);
        userRepository.save(user);
        return true;
    }

    @Override
    public UserVO getUserVOById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "User id is required");
        User user = userRepository.findByIdAndIsDelete(id, DEFAULT_IS_DELETE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "User not found"));
        return getUserVO(user);
    }

    @Override
    public List<UserVO> listUsers(UserQueryRequest request) {
        UserQueryRequest queryRequest = request == null ? new UserQueryRequest() : request;
        Sort sort = buildSort(queryRequest);
        List<User> users = userRepository.findAll(buildSpecification(queryRequest), sort);
        return getUserVOList(users);
    }

    @Override
    public Page<UserVO> listUserByPage(UserQueryRequest request) {
        UserQueryRequest queryRequest = request == null ? new UserQueryRequest() : request;
        int current = Math.max(queryRequest.getCurrent() - 1, 0);
        int pageSize = Math.max(queryRequest.getPageSize(), 1);
        pageSize = Math.min(pageSize, MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(current, pageSize, buildSort(queryRequest));
        Page<User> page = userRepository.findAll(buildSpecification(queryRequest), pageable);
        return page.map(this::getUserVO);
    }

    @Override
    @Transactional
    public UserVO updateMyUser(UserUpdateMyRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        User user = userRepository.findByIdAndIsDelete(loginUser.getId(), DEFAULT_IS_DELETE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "User not found"));

        if (request.getUserName() != null) {
            user.setUserName(StringUtils.trimToNull(request.getUserName()));
        }
        if (request.getUserAvatar() != null) {
            user.setUserAvatar(StringUtils.trimToNull(request.getUserAvatar()));
        }
        if (request.getUserProfile() != null) {
            user.setUserProfile(StringUtils.trimToNull(request.getUserProfile()));
        }

        userRepository.save(user);
        return getUserVO(user);
    }

    private void validateAccount(String userAccount) {
        ThrowUtils.throwIf(userAccount.length() < MIN_ACCOUNT_LENGTH || userAccount.length() > 256,
                ErrorCode.PARAMS_ERROR, "Account length is invalid");
        ThrowUtils.throwIf(StringUtils.containsAny(userAccount, " ", "\t", "\n", "\r"),
                ErrorCode.PARAMS_ERROR, "Account contains whitespace");
    }

    private void validatePassword(String userPassword) {
        ThrowUtils.throwIf(userPassword.length() < MIN_PASSWORD_LENGTH || userPassword.length() > 512,
                ErrorCode.PARAMS_ERROR, "Password length is invalid");
    }

    private void validateRole(String role) {
        boolean validRole = UserConstant.DEFAULT_ROLE.equals(role)
                || UserConstant.ADMIN_ROLE.equals(role)
                || UserConstant.BAN_ROLE.equals(role);
        ThrowUtils.throwIf(!validRole, ErrorCode.PARAMS_ERROR, "Role is invalid");
    }

    private Long parseUserId(Object userIdObj) {
        if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        }
        try {
            return Long.valueOf(userIdObj.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
    }

    private Sort buildSort(UserQueryRequest request) {
        String sortField = request.getSortField();
        if (!SqlUtils.validSortField(sortField) || !ALLOWED_SORT_FIELDS.contains(sortField)) {
            return Sort.unsorted();
        }
        String sortOrder = StringUtils.trimToEmpty(request.getSortOrder());
        boolean isDesc = CommonConstant.SORT_ORDER_DESC.trim().equalsIgnoreCase(sortOrder);
        return Sort.by(isDesc ? Sort.Direction.DESC : Sort.Direction.ASC, sortField);
    }

    private Specification<User> buildSpecification(UserQueryRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (request.getId() != null) {
                predicates.add(cb.equal(root.get("id"), request.getId()));
            }
            if (StringUtils.isNotBlank(request.getUserAccount())) {
                predicates.add(cb.like(root.get("userAccount"), "%" + request.getUserAccount().trim() + "%"));
            }
            if (StringUtils.isNotBlank(request.getUserName())) {
                predicates.add(cb.like(root.get("userName"), "%" + request.getUserName().trim() + "%"));
            }
            if (StringUtils.isNotBlank(request.getUserRole())) {
                predicates.add(cb.equal(root.get("userRole"), request.getUserRole().trim()));
            }
            predicates.add(cb.equal(root.get("isDelete"), DEFAULT_IS_DELETE));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

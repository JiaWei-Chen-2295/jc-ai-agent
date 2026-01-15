package fun.javierchen.jcaiagentbackend.config;

import fun.javierchen.jcaiagentbackend.common.TenantContextHolder;
import fun.javierchen.jcaiagentbackend.constant.UserConstant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                Object tenantIdObj = session.getAttribute(UserConstant.USER_ACTIVE_TENANT_ID);
                Long tenantId = parseTenantId(tenantIdObj);
                if (tenantId != null) {
                    TenantContextHolder.setTenantId(tenantId);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

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

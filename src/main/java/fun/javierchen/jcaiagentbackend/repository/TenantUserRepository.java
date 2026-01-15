package fun.javierchen.jcaiagentbackend.repository;

import fun.javierchen.jcaiagentbackend.model.entity.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantUserRepository extends JpaRepository<TenantUser, Long> {

    /**
     * Find membership by tenant and user (not deleted).
     */
    Optional<TenantUser> findByTenantIdAndUserIdAndIsDelete(Long tenantId, Long userId, Integer isDelete);

    /**
     * Find membership by tenant and user (including history).
     */
    Optional<TenantUser> findByTenantIdAndUserId(Long tenantId, Long userId);

    /**
     * List memberships for a user (not deleted).
     */
    List<TenantUser> findByUserIdAndIsDelete(Long userId, Integer isDelete);

    /**
     * Find next admin candidate by join order.
     */
    Optional<TenantUser> findFirstByTenantIdAndIsDeleteAndUserIdNotOrderByCreateTimeAscIdAsc(Long tenantId, Integer isDelete, Long userId);

    /**
     * List all tenant members by join order.
     */
    List<TenantUser> findByTenantIdAndIsDeleteOrderByCreateTimeAscIdAsc(Long tenantId, Integer isDelete);
}

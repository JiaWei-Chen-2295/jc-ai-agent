package fun.javierchen.jcaiagentbackend.repository;

import fun.javierchen.jcaiagentbackend.model.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    /**
     * Find a tenant by id and delete flag.
     */
    Optional<Tenant> findByIdAndIsDelete(Long id, Integer isDelete);

    /**
     * Get the personal tenant for a user.
     */
    Optional<Tenant> findFirstByOwnerUserIdAndTenantTypeAndIsDelete(Long ownerUserId, String tenantType, Integer isDelete);

    /**
     * Count teams created by a user.
     */
    long countByOwnerUserIdAndTenantTypeAndIsDelete(Long ownerUserId, String tenantType, Integer isDelete);

    /**
     * Fetch tenants by ids and delete flag.
     */
    List<Tenant> findByIdInAndIsDelete(List<Long> ids, Integer isDelete);
}

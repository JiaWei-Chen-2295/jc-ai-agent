package fun.javierchen.jcaiagentbackend.model.entity;

import fun.javierchen.jcaiagentbackend.constant.TenantConstant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "tenant")
@Data
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_name", nullable = false, length = 128)
    private String tenantName;

    @Column(name = "tenant_type", nullable = false, length = 32)
    private String tenantType;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "create_time", nullable = false)
    private OffsetDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private OffsetDateTime updateTime;

    @Column(name = "is_delete", nullable = false)
    private Integer isDelete;

    /**
     * Initialize default fields and timestamps.
     */
    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createTime == null) {
            createTime = now;
        }
        if (updateTime == null) {
            updateTime = now;
        }
        if (tenantType == null) {
            tenantType = TenantConstant.TENANT_TYPE_TEAM;
        }
        if (isDelete == null) {
            isDelete = 0;
        }
    }

    /**
     * Update the modification timestamp.
     */
    @PreUpdate
    public void preUpdate() {
        updateTime = OffsetDateTime.now(ZoneOffset.UTC);
    }
}

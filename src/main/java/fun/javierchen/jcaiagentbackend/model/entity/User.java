package fun.javierchen.jcaiagentbackend.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fun.javierchen.jcaiagentbackend.constant.UserConstant;
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
import java.time.ZoneId;

@Entity
@Table(name = "\"user\"")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_account", nullable = false, length = 256)
    private String userAccount;

    @JsonIgnore
    @Column(name = "user_password", nullable = false, length = 512)
    private String userPassword;

    @Column(name = "union_id", length = 256)
    private String unionId;

    @Column(name = "mp_open_id", length = 256)
    private String mpOpenId;

    @Column(name = "user_name", length = 256)
    private String userName;

    @Column(name = "user_avatar", length = 1024)
    private String userAvatar;

    @Column(name = "user_profile", length = 512)
    private String userProfile;

    @Column(name = "user_role", nullable = false, length = 256)
    private String userRole;

    @Column(name = "create_time", nullable = false)
    private OffsetDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private OffsetDateTime updateTime;

    @Column(name = "is_delete", nullable = false)
    private Integer isDelete;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createTime == null) {
            createTime = now;
        }
        if (updateTime == null) {
            updateTime = now;
        }
        if (userRole == null) {
            userRole = UserConstant.DEFAULT_ROLE;
        }
        if (isDelete == null) {
            isDelete = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = OffsetDateTime.now(ZoneOffset.UTC);
    }
}

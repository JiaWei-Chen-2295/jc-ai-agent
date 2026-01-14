package fun.javierchen.jcaiagentbackend.repository;

import fun.javierchen.jcaiagentbackend.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUserAccountAndIsDelete(String userAccount, Integer isDelete);

    Optional<User> findByIdAndIsDelete(Long id, Integer isDelete);

    boolean existsByUserAccountAndIsDelete(String userAccount, Integer isDelete);
}

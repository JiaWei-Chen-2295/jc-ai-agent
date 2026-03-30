package fun.javierchen.jcaiagentbackend.repository;

import fun.javierchen.jcaiagentbackend.model.entity.AiModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiModelConfigRepository extends JpaRepository<AiModelConfig, Long> {

    /**
     * 根据模型 ID 和启用状态查询
     */
    Optional<AiModelConfig> findByModelIdAndEnabled(String modelId, Boolean enabled);

    /**
     * 查询所有启用的模型，按排序权重升序
     */
    List<AiModelConfig> findByEnabledTrueOrderBySortOrderAsc();

    /**
     * 所有模型（含禁用），用于管理后台
     */
    List<AiModelConfig> findAllByOrderBySortOrderAsc();

    /**
     * 根据模型 ID 查询（不限制 enabled）
     */
    Optional<AiModelConfig> findByModelId(String modelId);
}

package com.javaclaw.mapper;

import com.javaclaw.entity.ToolRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 工具 Mapper 接口
 */
@Mapper
public interface ToolMapper {

    /**
     * 查询所有启用的工具
     */
    List<ToolRecord> findAllEnabled();

    /**
     * 根据工具key查询
     */
    ToolRecord findByKey(@Param("toolKey") String toolKey);

    /**
     * 根据工具key列表查询
     */
    List<ToolRecord> findByKeys(@Param("toolKeys") List<String> toolKeys);

    /**
     * 插入或更新工具
     */
    void saveOrUpdate(@Param("toolKey") String toolKey, @Param("toolJson") String toolJson, @Param("userId") String userId);

    /**
     * 删除工具
     */
    void delete(@Param("toolKey") String toolKey);

    /**
     * 启用/禁用工具
     */
    void setEnabled(@Param("toolKey") String toolKey, @Param("enabled") boolean enabled);
}

package com.javaclaw.mapper;

import com.javaclaw.entity.SkillRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 技能 Mapper 接口
 */
@Mapper
public interface SkillMapper {

    /**
     * 查询所有启用的技能
     */
    List<SkillRecord> findAllEnabled();

    /**
     * 根据skillId查询
     */
    SkillRecord findById(@Param("skillId") String skillId);

    /**
     * 根据skillId列表查询
     */
    List<SkillRecord> findByIds(@Param("skillIds") List<String> skillIds);

    /**
     * 插入或更新技能
     */
    void saveOrUpdate(SkillRecord record);

    /**
     * 删除技能
     */
    void delete(@Param("skillId") String skillId);

    /**
     * 启用/禁用技能
     */
    void setEnabled(@Param("skillId") String skillId, @Param("enabled") boolean enabled);
}

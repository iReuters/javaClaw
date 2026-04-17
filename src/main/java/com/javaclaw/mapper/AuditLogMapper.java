package com.javaclaw.mapper;

import com.javaclaw.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface AuditLogMapper {

    void insert(AuditLog auditLog);

    AuditLog findById(@Param("id") Long id);

    List<AuditLog> findByMemoryId(@Param("memoryId") String memoryId);

    List<AuditLog> findByToolName(@Param("toolName") String toolName);

    List<AuditLog> findByTimeRange(@Param("startTime") String startTime, @Param("endTime") String endTime);

    List<AuditLog> findByPage(@Param("offset") int offset, @Param("limit") int limit);

    Long countAll();

    List<Map<String, Object>> statsByTool();

    List<Map<String, Object>> statsByDay();
}
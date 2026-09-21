package com.todo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.todo.entity.TaskTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 任务标签关联表 Mapper。
 *
 * <p>{@code task_tag} 是复合主键表，MyBatis-Plus 的单主键 API 不适用，
 * 因此关联的写入与删除统一走自定义 SQL。
 */
public interface TaskTagMapper extends BaseMapper<TaskTag> {

    @Insert("<script>INSERT INTO task_tag (task_id, tag_id) VALUES "
            + "<foreach collection='tagIds' item='tagId' separator=','>(#{taskId}, #{tagId})</foreach>"
            + "</script>")
    int insertBatch(@Param("taskId") Long taskId, @Param("tagIds") Collection<Long> tagIds);

    @Delete("DELETE FROM task_tag WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") Long taskId);

    @Delete("<script>DELETE FROM task_tag WHERE task_id IN "
            + "<foreach collection='taskIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int deleteByTaskIds(@Param("taskIds") Collection<Long> taskIds);

    @Delete("DELETE FROM task_tag WHERE tag_id = #{tagId}")
    int deleteByTagId(@Param("tagId") Long tagId);

    @Select("SELECT tag_id FROM task_tag WHERE task_id = #{taskId}")
    List<Long> selectTagIdsByTaskId(@Param("taskId") Long taskId);

    @Select("<script>SELECT task_id, tag_id FROM task_tag WHERE task_id IN "
            + "<foreach collection='taskIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<TaskTag> selectByTaskIds(@Param("taskIds") Collection<Long> taskIds);

    /**
     * 各标签的任务数（只统计当前用户的任务）。
     *
     * <p>必须 join task 并排除已软删除的任务，否则标签计数会把已删除任务也算进去。
     */
    @Select("SELECT tt.tag_id, COUNT(*) AS cnt FROM task_tag tt "
            + "JOIN task t ON t.id = tt.task_id AND t.is_delete = FALSE AND t.user_id = #{userId} "
            + "GROUP BY tt.tag_id")
    List<Map<String, Object>> countByTag(@Param("userId") Long userId);

    /**
     * 找出同时包含全部指定标签的任务 ID（AND 语义），见设计方案 §4.4。
     */
    @Select("<script>SELECT task_id FROM task_tag WHERE tag_id IN "
            + "<foreach collection='tagIds' item='tagId' open='(' separator=',' close=')'>#{tagId}</foreach> "
            + "GROUP BY task_id HAVING COUNT(DISTINCT tag_id) = #{tagCount}</script>")
    List<Long> selectTaskIdsHavingAllTags(@Param("tagIds") Collection<Long> tagIds,
                                          @Param("tagCount") int tagCount);
}

package com.todo.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.todo.common.BizException;
import com.todo.dto.TagSaveRequest;
import com.todo.entity.Tag;
import com.todo.mapper.TagMapper;
import com.todo.mapper.TaskTagMapper;
import com.todo.support.UserContext;
import com.todo.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 标签服务，见设计方案 §4.4。
 *
 * <p>标签为独立表建模，而非逗号分隔字符串，以保证筛选（多标签 AND）与统计的准确性。
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private static final String DEFAULT_COLOR = "#3B82F6";

    private final TagMapper tagMapper;
    private final TaskTagMapper taskTagMapper;

    /** 标签列表，附各标签的任务数 */
    public List<TagVO> listWithTaskCount() {
        Long userId = UserContext.getUserId();
        Map<Long, Long> countByTag = new HashMap<>();
        for (Map<String, Object> row : taskTagMapper.countByTag(userId)) {
            Object tagId = row.get("tag_id");
            Object count = row.get("cnt");
            if (tagId instanceof Number id && count instanceof Number c) {
                countByTag.put(id.longValue(), c.longValue());
            }
        }

        return tagMapper.selectList(
                        Wrappers.<Tag>lambdaQuery()
                                .eq(Tag::getUserId, userId)
                                .orderByAsc(Tag::getId))
                .stream()
                .map(tag -> toVO(tag, countByTag.getOrDefault(tag.getId(), 0L)))
                .toList();
    }

    public Tag getExisting(Long id) {
        Tag tag = tagMapper.selectOne(Wrappers.<Tag>lambdaQuery()
                .eq(Tag::getId, id)
                .eq(Tag::getUserId, UserContext.getUserId()));
        if (tag == null) {
            throw BizException.notFound("标签不存在: " + id);
        }
        return tag;
    }

    @Transactional
    public TagVO create(TagSaveRequest request) {
        String name = request.getName().trim();
        assertNameAvailable(name, null);

        Tag tag = new Tag();
        tag.setName(name);
        tag.setColor(StringUtils.hasText(request.getColor()) ? request.getColor() : DEFAULT_COLOR);
        tag.setUserId(UserContext.getUserId());

        try {
            tagMapper.insert(tag);
        } catch (DuplicateKeyException e) {
            // 并发下由 uk_tag_name 唯一索引兜底
            throw BizException.conflict("标签名称已存在: " + name);
        }
        return toVO(tag, 0L);
    }

    @Transactional
    public TagVO update(Long id, TagSaveRequest request) {
        Tag tag = getExisting(id);
        String name = request.getName().trim();
        assertNameAvailable(name, id);

        tag.setName(name);
        if (StringUtils.hasText(request.getColor())) {
            tag.setColor(request.getColor());
        }

        try {
            tagMapper.updateById(tag);
        } catch (DuplicateKeyException e) {
            throw BizException.conflict("标签名称已存在: " + name);
        }
        return toVO(tag, null);
    }

    /**
     * 删除标签：软删除标签本身并清理关联，不影响任务。
     */
    @Transactional
    public void delete(Long id) {
        getExisting(id);
        taskTagMapper.deleteByTagId(id);
        tagMapper.deleteById(id);
    }

    /** 校验标签 ID 是否全部存在（只校验当前用户的标签），返回去重后的列表 */
    public List<Long> assertAllExist(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = tagIds.stream().distinct().toList();
        List<Tag> found = tagMapper.selectList(Wrappers.<Tag>lambdaQuery()
                .in(Tag::getId, distinct)
                .eq(Tag::getUserId, UserContext.getUserId()));
        if (found.size() != distinct.size()) {
            List<Long> foundIds = found.stream().map(Tag::getId).toList();
            List<Long> missing = distinct.stream().filter(id -> !foundIds.contains(id)).toList();
            throw BizException.paramInvalid("标签不存在: " + missing);
        }
        return distinct;
    }

    private void assertNameAvailable(String name, Long excludeId) {
        Long count = tagMapper.selectCount(Wrappers.<Tag>lambdaQuery()
                .eq(Tag::getName, name)
                .eq(Tag::getUserId, UserContext.getUserId())
                .ne(excludeId != null, Tag::getId, excludeId));
        if (count != null && count > 0) {
            throw BizException.conflict("标签名称已存在: " + name);
        }
    }

    private TagVO toVO(Tag tag, Long taskCount) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setColor(tag.getColor());
        vo.setTaskCount(taskCount);
        return vo;
    }
}

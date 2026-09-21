package com.todo.controller;

import com.todo.common.R;
import com.todo.dto.TagSaveRequest;
import com.todo.service.TagService;
import com.todo.vo.TagVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签接口，见设计方案 §6.4。
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public R<List<TagVO>> list() {
        return R.ok(tagService.listWithTaskCount());
    }

    @PostMapping
    public R<TagVO> create(@Valid @RequestBody TagSaveRequest request) {
        return R.ok("标签已创建", tagService.create(request));
    }

    @PutMapping("/{id}")
    public R<TagVO> update(@PathVariable Long id, @Valid @RequestBody TagSaveRequest request) {
        return R.ok("标签已更新", tagService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return R.ok("标签已删除", null);
    }
}

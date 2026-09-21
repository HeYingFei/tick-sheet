package com.todo.controller;

import com.todo.common.R;
import com.todo.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统配置接口，见设计方案 §6.4。
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @GetMapping
    public R<Map<String, String>> get() {
        return R.ok(configService.getAll());
    }

    @PutMapping
    public R<Map<String, String>> update(@RequestBody Map<String, String> values) {
        return R.ok("配置已保存", configService.updateAll(values));
    }
}

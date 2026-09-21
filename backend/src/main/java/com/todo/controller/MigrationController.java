package com.todo.controller;

import com.todo.common.R;
import com.todo.service.MigrationService;
import com.todo.vo.BackupRecordVO;
import com.todo.vo.ImportResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 数据迁移接口。
 */
@RestController
@RequestMapping("/api/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final MigrationService migrationService;

    @GetMapping("/export")
    public R<Map<String, Object>> export() {
        return R.ok("导出成功", migrationService.export());
    }

    @PostMapping("/import")
    public R<ImportResultVO> importData(@RequestParam("file") MultipartFile file) {
        return R.ok(migrationService.importJson(file));
    }

    @PostMapping("/backup")
    public R<BackupRecordVO> backup() {
        return R.ok("备份创建成功", migrationService.backup());
    }

    @GetMapping("/backups")
    public R<List<BackupRecordVO>> listBackups() {
        return R.ok(migrationService.listBackups());
    }

    @PostMapping("/backups/{id}/restore")
    public R<ImportResultVO> restore(@PathVariable Long id) {
        return R.ok(migrationService.restore(id));
    }

    @DeleteMapping("/backups/{id}")
    public R<Void> deleteBackup(@PathVariable Long id) {
        migrationService.deleteBackup(id);
        return R.ok("备份已删除", null);
    }

    @DeleteMapping("/data")
    public R<Void> clearAll() {
        migrationService.clearAll();
        return R.ok("数据已清空", null);
    }
}

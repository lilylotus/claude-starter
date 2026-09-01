package cn.nihility.rbac.chat.controller;

import cn.nihility.rbac.chat.dto.SensitiveWordCreateRequest;
import cn.nihility.rbac.chat.dto.SensitiveWordVO;
import cn.nihility.rbac.chat.service.SensitiveWordService;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 聊天敏感词库后台管理接口：分页查询、新增、删除（物理删除）、启用/停用词条，
 * 任一写操作完成后立即触发内存 AC 自动机重建，无需重启服务（chat-security spec
 * "敏感词库后台管理"需求）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "聊天敏感词管理", description = "聊天敏感词库维护接口")
public class SensitiveWordController {

    /** 敏感词后台管理业务逻辑接口。 */
    private final SensitiveWordService sensitiveWordService;

    /**
     * 分页查询敏感词。
     *
     * @param keyword  词条关键字模糊匹配
     * @param status   状态精确过滤
     * @param page     页码
     * @param pageSize 每页条数
     * @return 敏感词的分页结果
     */
    @Operation(summary = "分页查询敏感词")
    @GetMapping("/api/chat/sensitive-words")
    public PageResult<SensitiveWordVO> page(
            @Parameter(description = "词条关键字模糊匹配，可为空")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "状态精确过滤：2000=启用，3000=停用，可为空")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "页码，默认第 1 页")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10 条")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return sensitiveWordService.getPage(keyword, status, page, pageSize);
    }

    /**
     * 新增敏感词。
     *
     * @param request 创建请求
     * @return 创建后的敏感词详情
     */
    @Operation(summary = "新增敏感词")
    @PostMapping("/api/chat/sensitive-words")
    public SensitiveWordVO create(@Valid @RequestBody SensitiveWordCreateRequest request) {
        return sensitiveWordService.create(request);
    }

    /**
     * 删除敏感词。
     *
     * @param id 敏感词 id
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "删除敏感词", description = "物理删除")
    @DeleteMapping("/api/chat/sensitive-words/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sensitiveWordService.delete(id);
        return Result.success();
    }

    /**
     * 启用敏感词。
     *
     * @param id 敏感词 id
     * @return 更新后的敏感词详情
     */
    @Operation(summary = "启用敏感词")
    @PutMapping("/api/chat/sensitive-words/{id}/enable")
    public SensitiveWordVO enable(@PathVariable Long id) {
        return sensitiveWordService.enable(id);
    }

    /**
     * 停用敏感词。
     *
     * @param id 敏感词 id
     * @return 更新后的敏感词详情
     */
    @Operation(summary = "停用敏感词")
    @PutMapping("/api/chat/sensitive-words/{id}/disable")
    public SensitiveWordVO disable(@PathVariable Long id) {
        return sensitiveWordService.disable(id);
    }
}

package cn.nihility.rbac.sync.openapi.controller;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.sync.openapi.dto.SyncPullRecordVO;
import cn.nihility.rbac.sync.openapi.service.SyncPullService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用数据同步对外拉取接口，路径前缀 {@code /open/api/sync}（区别于 {@code /api/**} 管理端
 * 前缀，语义上是外部系统开放接口），面向持有合法 AccessKey 的外部应用调用，不受管理端登录
 * 鉴权/管辖组织范围过滤器影响，鉴权只走 {@code X-App-Key} + 签名
 * （{@code cn.nihility.rbac.sync.sign.OpenApiSignInterceptor}）（app-sync-notify-pull-api
 * change design.md Decision 9）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "应用数据同步-拉取", description = "面向外部应用的对外数据拉取接口，鉴权仅依赖 X-App-Key + 签名")
public class SyncNotifyPullController {

    /** 对外拉取业务逻辑接口。 */
    private final SyncPullService syncPullService;

    /**
     * 按数据类型 + 一个或多个变更对象 id 查询最新变更记录。
     *
     * @param dataType 数据类型：ORG/USER/POSITION/APP/ROLE
     * @param bizIds   变更对象 id，逗号分隔
     * @return 每个 id 最新一条变更记录（未开通该数据类型时返回空列表）
     */
    @Operation(summary = "按数据类型与 id 拉取变更数据",
            description = "对每个 bizId 取该 dataType+bizId 变更记录中最新一条返回，"
                    + "调用方应用未开通该数据类型时返回空列表而不是报错")
    @GetMapping("/open/api/sync/pull/by-id")
    public List<SyncPullRecordVO> pullById(
            @Parameter(description = "数据类型：ORG/USER/POSITION/APP/ROLE") @RequestParam String dataType,
            @Parameter(description = "变更对象 id，逗号分隔，如 1,2,3") @RequestParam String bizIds) {
        return syncPullService.pullByBizIds(dataType, parseBizIds(bizIds));
    }

    /**
     * 按起始序列号游标批量查询变更记录。
     *
     * @param dataType     数据类型，可选（缺省返回调用方应用当前允许同步的全部数据域）
     * @param fromSequence 起始序列号，只返回大于该值的记录
     * @param limit        单次最多返回条数，可选（缺省取对应数据域的分页大小配置）
     * @return 变更记录列表，按序列号升序排列
     */
    @Operation(summary = "按序列号批量拉取变更数据",
            description = "返回序列号大于 fromSequence、按升序排列的变更记录，dataType 缺省时返回该应用"
                    + "当前允许同步的全部数据域变更，limit 缺省时取对应数据域的拉取分页大小配置")
    @GetMapping("/open/api/sync/pull/by-sequence")
    public List<SyncPullRecordVO> pullBySequence(
            @Parameter(description = "数据类型：ORG/USER/POSITION/APP/ROLE，缺省返回全部已开通数据域")
            @RequestParam(required = false) String dataType,
            @Parameter(description = "起始序列号，只返回大于该值的记录") @RequestParam Long fromSequence,
            @Parameter(description = "单次最多返回条数，缺省取对应数据域的拉取分页大小配置")
            @RequestParam(required = false) Integer limit) {
        return syncPullService.pullBySequence(dataType, fromSequence, limit);
    }

    /**
     * 把逗号分隔的变更对象 id 字符串解析为 {@code Long} 列表。
     *
     * @param bizIds 逗号分隔的变更对象 id 字符串
     * @return 解析后的 id 列表
     */
    private List<Long> parseBizIds(String bizIds) {
        if (!StringUtils.hasText(bizIds)) {
            return List.of();
        }
        try {
            return Arrays.stream(bizIds.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(Long::parseLong)
                    .toList();
        } catch (NumberFormatException e) {
            throw new BusinessException("bizIds 参数格式不正确，应为逗号分隔的数字：" + bizIds);
        }
    }
}

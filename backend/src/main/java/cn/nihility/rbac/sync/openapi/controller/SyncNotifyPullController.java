package cn.nihility.rbac.sync.openapi.controller;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.sync.openapi.dto.SyncPullPageVO;
import cn.nihility.rbac.sync.openapi.dto.SyncPullRequest;
import cn.nihility.rbac.sync.openapi.service.SyncPullService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用数据同步对外拉取接口，路径前缀 {@code /open/api/sync}（区别于 {@code /api/**} 管理端
 * 前缀，语义上是外部系统开放接口），面向持有合法 AccessKey 的外部应用调用，不受管理端登录
 * 鉴权/管辖组织范围过滤器影响，鉴权只走 {@code appKey} 请求头 + 签名
 * （{@code cn.nihility.rbac.sync.sign.OpenApiSignInterceptor}）。原按 id / 按序列号两个
 * 拉取接口合并为一个统一的分页拉取接口，不兼容旧接口（app-sync-drop-changelog change
 * design.md Decision 3）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "应用数据同步-拉取", description = "面向外部应用的对外数据拉取接口，鉴权仅依赖 appKey 请求头 + 签名")
public class SyncNotifyPullController {

    /** 对外拉取业务逻辑接口。 */
    private final SyncPullService syncPullService;

    /**
     * 分页拉取归属调用方应用当前可见的数据域当前数据。
     *
     * @param dataType       数据类型：ORG/USER/POSITION/APP/ROLE/DICT
     * @param page           页码，未传时默认第 1 页
     * @param pageSize       每页大小，未传或非正数时取该应用该数据域配置的拉取分页大小
     * @param updateTimeFrom 更新时间范围起点（含），用于增量拉取
     * @param updateTimeTo   更新时间范围终点（含）
     * @param ids            主键 id 列表，逗号分隔，可选
     * @param codes          业务编码列表，逗号分隔，可选；任职数据类型传入时被忽略；字典数据类型按
     *                       字典项自身编码过滤（不是字典类型编码）
     * @param mobile         用户手机号，可选；仅 dataType=USER 时生效，其余数据类型传入时被忽略
     * @return 整页视图对象，顶层回显 dataType/本次实际生效的 page/pageSize/dataSize（本页
     *         records 实际条数）/latestUpdateTime（本页记录最大更新时间，records 为空时为
     *         null，可作为下一次增量拉取 updateTimeFrom 的取值），records 每条元素已合并
     *         bizId/bizCode/updateTime；未开通该数据类型/同步总开关关闭时 records 为空列表，
     *         翻到最后一页后也返回空列表
     */
    @Operation(summary = "分页拉取数据域当前数据",
            description = "直接分页查询组织/用户/任职/应用/角色/字典业务表当前数据（字典拉取的是字典项而非字典类型"
                    + "本身），不过滤停用/已删除记录，按 update_time ASC, id ASC 排序；响应是带分页信息的整页对象，"
                    + "顶层除 dataType/page/pageSize 外还携带 dataSize（本页 records 实际条数）与 latestUpdateTime"
                    + "（本页记录最大更新时间，records 为空时为 null，可原样作为下一次增量拉取 updateTimeFrom 的取值），"
                    + "records 每条元素直接合并了 bizId/bizCode/bizStatus/updateTime，不再嵌套一层 data；任职记录"
                    + "额外合并 userCode（关联用户的业务编码）与 orgCode（关联组织的业务编码），字典记录额外合并"
                    + "dictTypeCode（所属字典类型的编码，因字典项编码只在同一字典类型下唯一）；未开通该数据类型或"
                    + "同步总开关关闭时 records 为空列表而不是"
                    + "报错，翻到最后一页后也返回空列表，作为拉取完毕的标识")
    @GetMapping("/open/api/sync/pull")
    public SyncPullPageVO pull(
            @Parameter(description = "数据类型：ORG/USER/POSITION/APP/ROLE/DICT") @RequestParam String dataType,
            @Parameter(description = "页码，未传时默认第 1 页") @RequestParam(required = false) Integer page,
            @Parameter(description = "每页大小，未传或非正数时取该应用该数据域配置的拉取分页大小")
            @RequestParam(required = false) Integer pageSize,
            @Parameter(description = "更新时间范围起点（含），用于增量拉取，如 2026-07-01T00:00:00")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updateTimeFrom,
            @Parameter(description = "更新时间范围终点（含），如 2026-07-31T23:59:59")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updateTimeTo,
            @Parameter(description = "主键 id 列表，逗号分隔，如 1,2,3") @RequestParam(required = false) String ids,
            @Parameter(description = "业务编码列表，逗号分隔；任职数据类型传入时被忽略；字典数据类型按字典项自身编码"
                    + "过滤（不是字典类型编码）")
            @RequestParam(required = false) String codes,
            @Parameter(description = "用户手机号，仅 dataType=USER 时生效，其余数据类型传入时被忽略")
            @RequestParam(required = false) String mobile) {
        SyncPullRequest request = SyncPullRequest.builder()
                .dataType(dataType)
                .page(page)
                .pageSize(pageSize)
                .updateTimeFrom(updateTimeFrom)
                .updateTimeTo(updateTimeTo)
                .ids(parseIdList(ids))
                .codes(parseCodeList(codes))
                .mobile(mobile)
                .build();
        return syncPullService.pull(request);
    }

    /**
     * 把逗号分隔的主键 id 字符串解析为 {@code Long} 列表。
     *
     * @param ids 逗号分隔的主键 id 字符串，可为空
     * @return 解析后的 id 列表，未传入时返回 {@code null}（表示不过滤）
     */
    private List<Long> parseIdList(String ids) {
        if (!StringUtils.hasText(ids)) {
            return null;
        }
        try {
            return Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(Long::parseLong)
                    .toList();
        } catch (NumberFormatException e) {
            throw new BusinessException("ids 参数格式不正确，应为逗号分隔的数字：" + ids);
        }
    }

    /**
     * 把逗号分隔的业务编码字符串解析为字符串列表。
     *
     * @param codes 逗号分隔的业务编码字符串，可为空
     * @return 解析后的编码列表，未传入时返回 {@code null}（表示不过滤）
     */
    private List<String> parseCodeList(String codes) {
        if (!StringUtils.hasText(codes)) {
            return null;
        }
        return Arrays.stream(codes.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}

package cn.nihility.rbac.sync.openapi.controller;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.sync.openapi.dto.SyncChangesPageVO;
import cn.nihility.rbac.sync.openapi.dto.SyncChangesRequest;
import cn.nihility.rbac.sync.openapi.dto.SyncDigestVO;
import cn.nihility.rbac.sync.openapi.dto.SyncPullPageVO;
import cn.nihility.rbac.sync.openapi.dto.SyncPullRequest;
import cn.nihility.rbac.sync.openapi.service.SyncChangesService;
import cn.nihility.rbac.sync.openapi.service.SyncDigestService;
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

    /** 增量游标拉取变更指针业务逻辑接口。 */
    private final SyncChangesService syncChangesService;

    /** 对账摘要业务逻辑接口。 */
    private final SyncDigestService syncDigestService;

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
     * 增量游标拉取调用方当前可见范围内的变更指针，只返回定位信息（不返回业务数据），详情
     * 需要另行调用 {@link #pull} 携带 {@code ids} 复核。
     *
     * @param entityType 数据类型：ORG/USER/POSITION/APP/ROLE（不含 DICT）
     * @param sinceSeq   起始游标（不含），十进制字符串，未传时视为 "0"（从头开始）
     * @param pageSize   每页最多返回的可见记录数，未传或非正数时取默认值
     * @return 变更指针响应，携带 {@code nextSeq}/{@code hasMore}/{@code configEpoch}
     */
    @Operation(summary = "增量游标拉取变更指针",
            description = "按数据类型增量拉取变更流水指针（eventId/entityType/entityId/operationType/entityVersion/"
                    + "changeSeq/changeTime），不返回业务数据；changeSeq/eventId/entityId/entityVersion 均按十进制"
                    + "字符串序列化。USER 数据域没有组织路径前缀可用，服务端会批量查询候选用户的当前任职后过滤，循环扫描"
                    + "底层流水直到攒够 pageSize 条可见结果或底层流水耗尽；nextSeq 表示\"本轮已扫描到的最后一条底层"
                    + "流水\"，即使 records 为空也可能前进，不代表消费确认；hasMore 表示底层是否还有未扫描的记录。"
                    + "sinceSeq 早于变更流水保留窗口下界时返回业务错误，需改走 pull 全量重建并从 digest 返回的"
                    + "currentMaxSeq 重新开始增量。")
    @GetMapping("/open/api/sync/changes")
    public SyncChangesPageVO changes(
            @Parameter(description = "数据类型：ORG/USER/POSITION/APP/ROLE（不含 DICT）") @RequestParam String entityType,
            @Parameter(description = "起始游标（不含），十进制字符串，未传时视为 \"0\"")
            @RequestParam(required = false) String sinceSeq,
            @Parameter(description = "每页最多返回的可见记录数，未传或非正数时取默认值")
            @RequestParam(required = false) Integer pageSize) {
        SyncChangesRequest request =
                SyncChangesRequest.builder().entityType(entityType).sinceSeq(sinceSeq).pageSize(pageSize).build();
        return syncChangesService.changes(request);
    }

    /**
     * 对账摘要接口：返回调用方当前可见范围内该数据类型的记录数与内容摘要，并携带当前变更
     * 流水表最大 {@code changeSeq}（水位号），供"全量 pull + digest 拿到的水位号切入增量
     * changes"这套衔接协议使用。
     *
     * @param entityType 数据类型：ORG/USER/POSITION/APP/ROLE/DICT
     * @return 摘要响应
     */
    @Operation(summary = "对账摘要",
            description = "按 bizId 升序流式扫描调用方当前可见范围内该数据类型的全部记录，对每条记录做与 pull 一致的"
                    + "字段映射后完整输出记录，使用 SHA-256 + 版本化 canonical JSON（键按字典序、null 显式保留）逐条"
                    + "长度前缀分隔累加计算摘要，不整表加载进内存；响应返回算法名、摘要规则版本号、记录数、摘要值、"
                    + "当前 changeSeq 水位号（十进制字符串，表为空时为 \"0\"）与 configEpoch。")
    @GetMapping("/open/api/sync/digest")
    public SyncDigestVO digest(
            @Parameter(description = "数据类型：ORG/USER/POSITION/APP/ROLE/DICT") @RequestParam String entityType) {
        return syncDigestService.digest(entityType);
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

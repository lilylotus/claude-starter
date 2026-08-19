package cn.nihility.rbac.sync.pull.record.service;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.sync.pull.record.dto.AppPullRecordQueryRequest;
import cn.nihility.rbac.sync.pull.record.dto.AppPullRecordVO;

/**
 * 应用拉取变更数据请求记录业务逻辑接口：写入一条拉取日志（供 {@code SyncPullServiceImpl}
 * 的两个既有拉取方法调用）、按应用 id 分页查询拉取日志（add-app-sync-notify-pull-logs
 * change design.md Decision 2/3）。
 */
public interface AppPullRecordService {

    /**
     * 写入一条拉取日志。调用方（{@code SyncPullServiceImpl}）需要自行用 try/catch 包裹本次
     * 调用：本方法内部任何异常都不做兜底吞掉处理，写入失败不阻塞拉取主流程这一约定由调用方
     * 负责落实（design.md Decision 2）。
     *
     * @param appRefId       发起拉取的应用 id（{@code tab_app.id}）
     * @param pullMode       拉取方式：BY_ID/BY_SEQUENCE
     * @param dataType       请求的数据类型，按序列号拉取未传时为空
     * @param requestSummary 请求参数摘要
     * @param resultCount    本次返回的记录条数
     */
    void record(Long appRefId, String pullMode, String dataType, String requestSummary, int resultCount);

    /**
     * 按应用 id 分页查询拉取日志。
     *
     * @param request 分页查询参数，{@code appRefId} 必填
     * @return 拉取日志分页结果
     */
    PageResult<AppPullRecordVO> page(AppPullRecordQueryRequest request);
}

package cn.nihility.rbac.sync.openapi.service;

import cn.nihility.rbac.sync.openapi.dto.SyncPullPageVO;
import cn.nihility.rbac.sync.openapi.dto.SyncPullRequest;

/**
 * 对外拉取业务逻辑接口：统一的分页拉取数据域当前数据（app-sync-drop-changelog change
 * design.md Decision 2/3）。调用方应用由 {@code OpenApiSignInterceptor} 校验通过后标记在
 * {@code cn.nihility.rbac.sync.openapi.OpenApiCallerContext} 中，本接口内部直接读取，
 * 不作为方法参数显式传入。
 */
public interface SyncPullService {

    /**
     * 按请求参数分页拉取归属调用方应用当前可见的数据域当前数据。
     *
     * @param request 分页拉取请求参数
     * @return 整页视图对象，顶层回显 dataType/本次实际生效的 page/pageSize；数据类型不在
     *         当前调用方应用允许同步的范围内、或同步总开关关闭时 records 为空列表；翻到
     *         最后一页之后 records 也为空列表，作为拉取完毕的标识
     */
    SyncPullPageVO pull(SyncPullRequest request);
}

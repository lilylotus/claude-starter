package cn.nihility.rbac.identity.upstream.support;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.HttpClientUtils;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.identity.upstream.constant.UpstreamApiMethod;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 接口方式取数组件（design.md Decision 3）：复用项目已有的 {@link HttpClientUtils} 按配置
 * 的请求方式发起 GET/POST 请求，携带解密后的自定义请求头，响应体按 {@code application/json}
 * 解析为扁平 JSON 对象数组；非 2xx 状态码或响应体不是合法的 JSON 数组时判定该数据域取数
 * 失败，抛出 {@link BusinessException} 由调用方（{@code UpstreamSyncExecutor}）捕获并记为
 * 一条 {@code FAILED} 的同步记录。
 */
@Component
public class UpstreamHttpFetcher {

    /**
     * 拉取上游接口返回的原始数据。
     *
     * @param url     请求地址
     * @param method  请求方式，{@code POST} 时以空 JSON 请求体发起，其余按 GET 处理
     * @param headers 自定义请求头（已解密的明文取值），允许为 {@code null}
     * @return 原始行列表，每行 key 为上游字段编码
     */
    public List<Map<String, Object>> fetch(String url, String method, Map<String, String> headers) {
        HttpClientUtils.HttpResult result = UpstreamApiMethod.POST.equalsIgnoreCase(method)
                ? HttpClientUtils.postJson(url, headers, null, null)
                : HttpClientUtils.get(url, headers, null, null);
        if (result.getStatusCode() < 200 || result.getStatusCode() >= 300) {
            throw new BusinessException("接口请求失败，状态码：" + result.getStatusCode());
        }
        List<Map<String, Object>> rows;
        try {
            rows = JacksonUtils.toObj(result.getBody(), JacksonUtils.LIST_MAP_OBJECT_TYPE_REFERENCE);
        } catch (Exception e) {
            throw new BusinessException("接口响应体不是合法的 JSON 数组：" + e.getMessage());
        }
        return rows != null ? rows : List.of();
    }
}

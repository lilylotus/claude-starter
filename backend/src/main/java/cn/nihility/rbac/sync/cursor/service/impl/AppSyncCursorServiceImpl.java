package cn.nihility.rbac.sync.cursor.service.impl;

import cn.nihility.rbac.sync.cursor.mapper.AppSyncCursorMapper;
import cn.nihility.rbac.sync.cursor.service.AppSyncCursorService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 应用同步服务端投递水位业务逻辑实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppSyncCursorServiceImpl implements AppSyncCursorService {

    /** 应用同步服务端投递水位数据访问接口。 */
    private final AppSyncCursorMapper mapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public void advance(Long appRefId, String entityType, long nextSeq) {
        try {
            mapper.upsertLastDeliveredSeq(appRefId, entityType, nextSeq, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("更新应用同步服务端投递水位失败，不影响本次 /changes 响应结果：appRefId={}, entityType={}, nextSeq={}",
                    appRefId, entityType, nextSeq, e);
        }
    }
}

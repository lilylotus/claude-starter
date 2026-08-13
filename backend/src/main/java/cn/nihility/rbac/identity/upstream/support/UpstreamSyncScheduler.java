package cn.nihility.rbac.identity.upstream.support;

import cn.nihility.rbac.identity.upstream.constant.UpstreamIntervalUnit;
import cn.nihility.rbac.identity.upstream.constant.UpstreamScheduleType;
import cn.nihility.rbac.identity.upstream.constant.UpstreamTriggerType;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSourceEntity;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSourceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 上游数据同步定时轮询任务（design.md Decision 2）：每分钟查询全部 {@code enabled=true}
 * 的数据源，逐个判断是否到达配置的同步时间点，到期即调用
 * {@link UpstreamSyncExecutor#syncSource} 并更新 {@code lastTriggerTime}。风格仿照
 * {@code cn.nihility.rbac.sync.sign.NonceStore} 既有的 {@code @Scheduled} 用法。
 * {@code RbacApplication} 已 {@code @EnableScheduling}，不需要再加。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpstreamSyncScheduler {

    /** HH:mm 格式化器，用于 {@code FIXED_TIME} 类型的当前时间比对。 */
    private static final DateTimeFormatter HH_MM_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /** 上游数据源数据访问接口。 */
    private final UpstreamSourceMapper upstreamSourceMapper;

    /** 同步执行引擎。 */
    private final UpstreamSyncExecutor upstreamSyncExecutor;

    /**
     * 每分钟轮询一次，逐个数据源判断是否到期并触发同步。捕获单数据源级别的异常，不让
     * 一个数据源的异常中断其余数据源的轮询判断（design.md Decision 2）。
     */
    @Scheduled(fixedRate = 60_000L)
    public void tick() {
        List<UpstreamSourceEntity> sources = upstreamSourceMapper.selectList(
                new LambdaQueryWrapper<UpstreamSourceEntity>().eq(UpstreamSourceEntity::getEnabled, true));
        LocalDateTime now = LocalDateTime.now();
        for (UpstreamSourceEntity source : sources) {
            try {
                if (isDue(source, now)) {
                    upstreamSyncExecutor.syncSource(source.getId(), UpstreamTriggerType.SCHEDULE);
                    source.setLastTriggerTime(now);
                    upstreamSourceMapper.updateById(source);
                }
            } catch (Exception e) {
                log.warn("上游数据源[{}]定时同步失败", source.getId(), e);
            }
        }
    }

    /**
     * 判断一个已启用的数据源是否到达配置的同步时间点。
     *
     * <ul>
     *     <li>{@code INTERVAL} 类型：{@code lastTriggerTime} 为空视为立即到期（数据源
     *     刚启用/刚创建时首次轮询即触发一次）；否则自上次触发时间起满足间隔时长即到期。</li>
     *     <li>{@code FIXED_TIME} 类型：当前时间的 {@code HH:mm} 等于配置的
     *     {@code fixedTime} 且 {@code lastTriggerTime} 不在同一天内已经触发过（避免同一
     *     分钟内 tick 抖动或轮询延迟导致同一天重复触发多次）。</li>
     * </ul>
     *
     * @param source 数据源
     * @param now    当前时间，供单测注入固定时间
     * @return 是否到期
     */
    public boolean isDue(UpstreamSourceEntity source, LocalDateTime now) {
        if (UpstreamScheduleType.INTERVAL.equals(source.getScheduleType())) {
            if (source.getLastTriggerTime() == null) {
                return true;
            }
            long intervalValue = source.getIntervalValue() == null ? 0 : source.getIntervalValue();
            long unitMillis = UpstreamIntervalUnit.HOUR.equals(source.getIntervalUnit()) ? 3_600_000L : 60_000L;
            long dueMillis = intervalValue * unitMillis;
            long elapsedMillis = Duration.between(source.getLastTriggerTime(), now).toMillis();
            return elapsedMillis >= dueMillis;
        }
        if (UpstreamScheduleType.FIXED_TIME.equals(source.getScheduleType())) {
            if (!now.format(HH_MM_FORMATTER).equals(source.getFixedTime())) {
                return false;
            }
            if (source.getLastTriggerTime() == null) {
                return true;
            }
            return !source.getLastTriggerTime().toLocalDate().equals(now.toLocalDate());
        }
        return false;
    }
}

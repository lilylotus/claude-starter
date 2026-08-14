package cn.nihility.rbac.identity.upstream.support;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.identity.upstream.constant.UpstreamDataType;
import cn.nihility.rbac.identity.upstream.constant.UpstreamPositionPseudoFieldCode;
import cn.nihility.rbac.org.constant.OrgStatus;
import cn.nihility.rbac.org.dto.OrgCreateRequest;
import cn.nihility.rbac.org.dto.OrgUpdateRequest;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.org.service.OrgService;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.dto.PositionCreateRequest;
import cn.nihility.rbac.user.dto.PositionUpdateRequest;
import cn.nihility.rbac.user.dto.UserCreateRequest;
import cn.nihility.rbac.user.dto.UserUpdateRequest;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.user.service.PositionService;
import cn.nihility.rbac.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 上游同步单行落库处理器，按 {@link UpstreamDataType} 路由到组织/用户/任职三种匹配+落库
 * 算法。算法参照（不是直接依赖）{@code cn.nihility.rbac.excelimport.service.support.ImportRowExecutor}
 * 的 {@code processOrg}/{@code processUser}/{@code processPosition}/{@code bindProperties}
 * 写法：按调用方传入的主键标识字段（{@code primaryKeyFieldCodes}，由字段映射配置的
 * "主键标识"勾选项决定，upstream-field-mapping-primary-key change）动态匹配已有记录，
 * 命中零条调用既有 {@code create}、命中一条调用既有 {@code update}、命中多条判定失败，
 * 复用组织/用户/任职模块既有的创建/更新校验规则（必填/格式/唯一性等），不重新实现一套
 * （design.md Decision 3）。主键字段是运行时才知道的动态集合，无法用
 * {@link LambdaQueryWrapper} 的方法引用语法表达，改用 {@link QueryWrapper} 配合
 * {@code com.baomidou.mybatisplus.core.toolkit.StringUtils#camelToUnderline} 把字段编码
 * 转成数据库列名逐个拼接 {@code eq} 条件（多个 {@code eq} 默认 AND 语义，天然满足联合
 * 主键"全部相等"的要求，见 upstream-field-mapping-primary-key change design.md Decision 2）。
 *
 * <p>与 {@code ImportRowExecutor} 的差异：本类只保留"按编码匹配 + 调用既有 create/update
 * service + BeanWrapper 反射赋值"这部分算法，不引入 Excel 导入场景专属的字段配置驱动的
 * 必填校验/字典 label 反查（必填校验交给下游 CreateRequest 的 Bean Validation 兜底，
 * 由 {@code OrgService}/{@code UserService}/{@code PositionService} 各自的 create/update
 * 内部完成）。
 *
 * <p><b>实现阶段发现的 design.md 遗漏（已同步补充说明，详见
 * {@link UpstreamPositionPseudoFieldCode}）</b>：POSITION 数据域落库需要"所属人员标识"
 * "所属组织编码"两个值解析 {@code userId}/{@code orgId}，两者都不是 POSITION bizType
 * 下的元数据字段（外键关系不是可开放配置的展示字段），无法通过常规的字段映射目标传递。
 * 本类为 POSITION 数据域直接从取数阶段拉取到的原始行（转换前）按固定编码
 * {@link UpstreamPositionPseudoFieldCode#USER_IDENTIFIER}/
 * {@link UpstreamPositionPseudoFieldCode#ORG_CODE} 读取这两个值，其余字段（含 ORG 数据域
 * 的"上级组织编码" {@code parentCode}）仍走字段映射转换后的行数据
 * （upstream-org-parent-code-field-mapping change：{@code parentCode} 是 ORG bizType 下
 * 已存在的常规元数据字段，不再需要固定伪字段编码这条特殊路径）。
 */
@Component
@RequiredArgsConstructor
public class UpstreamRowUpserter {

    /** 组织数据访问接口，用于按编码查询已有记录做主键匹配。 */
    private final OrgMapper orgMapper;

    /** 组织业务逻辑接口，复用其既有的创建/更新校验与写入逻辑。 */
    private final OrgService orgService;

    /** 用户数据访问接口，用于按编码查询已有记录做主键匹配，POSITION 同步时还用于解析 userId。 */
    private final UserMapper userMapper;

    /** 用户业务逻辑接口，复用其既有的创建/更新校验与写入逻辑。 */
    private final UserService userService;

    /** 用户任职记录数据访问接口，用于按 userId+orgId+positionType 复合键查询已有记录。 */
    private final UserPositionMapper userPositionMapper;

    /** 任职管理业务逻辑接口，复用其既有的创建/更新校验与写入逻辑。 */
    private final PositionService positionService;

    /**
     * 处理一行上游数据：按数据域路由到组织/用户/任职各自的匹配+落库算法，独立开启一个
     * 新事务（{@link Propagation#REQUIRES_NEW}），任何异常均直接向上抛出触发本行事务
     * 回滚，由调用方（{@code UpstreamSyncExecutor}）捕获并计入该数据域同步记录的失败
     * 明细，不影响其余行的处理（design.md Decision 4）。
     *
     * @param dataType             数据域：ORG/USER/POSITION
     * @param transformedRow       经字段映射转换后的一行数据，key 为系统字段编码；ORG 数据域
     *                             通过其中的 {@code parentCode} 键解析上级组织
     * @param rawRow               取数阶段拉取到的原始行（转换前），key 为上游字段编码，
     *                             POSITION 数据域用其解析 {@link UpstreamPositionPseudoFieldCode}
     *                             约定的两个固定编码，ORG/USER 数据域不使用
     * @param primaryKeyFieldCodes 该数据域字段映射中标记为"主键标识"的系统字段编码列表
     *                             （一个或多个，联合、AND 语义），由调用方
     *                             （{@code UpstreamSyncExecutor}）保证非空
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertRow(String dataType, Map<String, Object> transformedRow, Map<String, Object> rawRow,
            List<String> primaryKeyFieldCodes) {
        switch (dataType) {
            case UpstreamDataType.ORG -> upsertOrg(transformedRow, primaryKeyFieldCodes);
            case UpstreamDataType.USER -> upsertUser(transformedRow, primaryKeyFieldCodes);
            case UpstreamDataType.POSITION -> upsertPosition(transformedRow, rawRow, primaryKeyFieldCodes);
            default -> throw new BusinessException("不支持的数据域：" + dataType);
        }
    }

    /**
     * 处理一行组织数据：按字段映射标记为"主键标识"的系统字段（一个或多个，联合、AND
     * 语义，见 {@link #appendPrimaryKeyConditions}）匹配已有记录，替换掉写死按
     * {@code code} 匹配的旧逻辑（upstream-field-mapping-primary-key change）；"上级组织
     * 编码"改为像其余系统字段一样通过字段映射配置到系统字段 {@code parentCode}（ORG
     * bizType 下已存在的常规元数据字段）解析 {@code parentId}：转换后行里取不到该键、
     * 取值为空均视为顶级组织（{@code parentId=0}，不判定失败——上游同步没有 Excel 模板
     * "固定必填列"那样的前置保障，管理员可能就是只想同步一批平级组织，或者干脆没有在
     * 字段映射里配置 {@code parentCode}）；其余取值按 {@code tab_org.code} 匹配已有组织，
     * 匹配不到时该行判定失败（upstream-org-parent-code-field-mapping change：不再依赖
     * 固定伪字段编码 {@code __parentCode} 从原始行读取）。
     *
     * @param row                  转换后的一行数据，同时用于解析 {@code parentCode}
     * @param primaryKeyFieldCodes 标记为主键标识的系统字段编码列表
     */
    private void upsertOrg(Map<String, Object> row, List<String> primaryKeyFieldCodes) {
        QueryWrapper<OrgEntity> wrapper = new QueryWrapper<>();
        appendPrimaryKeyConditions(wrapper, row, primaryKeyFieldCodes);
        wrapper.ne("status", OrgStatus.DELETED);
        List<OrgEntity> matches = orgMapper.selectList(wrapper);
        if (matches.size() > 1) {
            throw new BusinessException("按主键字段匹配到多条已存在的组织记录，无法确定更新目标");
        }
        Long parentId = resolveParentId(row);
        if (matches.isEmpty()) {
            OrgCreateRequest request = new OrgCreateRequest();
            bindProperties(request, row);
            request.setParentId(parentId);
            orgService.create(request);
        } else {
            OrgUpdateRequest request = new OrgUpdateRequest();
            bindProperties(request, row);
            request.setParentId(parentId);
            orgService.update(matches.get(0).getId(), request);
        }
    }

    /**
     * 从转换后行的系统字段 {@code parentCode} 解析 {@code parentId}：取不到该键或取值为空
     * 均视为顶级组织；非空取值按 {@code tab_org.code} 匹配已有组织，匹配不到时该行判定
     * 失败（upstream-org-parent-code-field-mapping change）。
     *
     * @param row 转换后的一行数据，key 为系统字段编码
     * @return 解析得到的上级组织 id，顶级组织为 0
     */
    private Long resolveParentId(Map<String, Object> row) {
        String parentCode = toText(row.get("parentCode"));
        if (!StringUtils.hasText(parentCode)) {
            return 0L;
        }
        OrgEntity parent = findSingleActive(orgMapper.selectList(new LambdaQueryWrapper<OrgEntity>()
                .eq(OrgEntity::getCode, parentCode)
                .ne(OrgEntity::getStatus, OrgStatus.DELETED)));
        if (parent == null) {
            throw new BusinessException("上级组织编码[" + parentCode + "]无法匹配到已有组织记录");
        }
        return parent.getId();
    }

    /**
     * 处理一行用户数据：按字段映射标记为"主键标识"的系统字段（一个或多个，联合、AND
     * 语义，见 {@link #appendPrimaryKeyConditions}）匹配已有记录，替换掉写死按
     * {@code code} 匹配的旧逻辑（upstream-field-mapping-primary-key change）。
     *
     * @param row                  转换后的一行数据
     * @param primaryKeyFieldCodes 标记为主键标识的系统字段编码列表
     */
    private void upsertUser(Map<String, Object> row, List<String> primaryKeyFieldCodes) {
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<>();
        appendPrimaryKeyConditions(wrapper, row, primaryKeyFieldCodes);
        wrapper.ne("status", UserStatus.DELETED);
        List<UserEntity> matches = userMapper.selectList(wrapper);
        if (matches.size() > 1) {
            throw new BusinessException("按主键字段匹配到多条已存在的用户记录，无法确定更新目标");
        }
        if (matches.isEmpty()) {
            UserCreateRequest request = new UserCreateRequest();
            bindProperties(request, row);
            userService.create(request);
        } else {
            UserUpdateRequest request = new UserUpdateRequest();
            bindProperties(request, row);
            userService.update(matches.get(0).getId(), request);
        }
    }

    /**
     * 处理一行任职数据：把"人员标识"（{@link UpstreamPositionPseudoFieldCode#USER_IDENTIFIER}）
     * 按 {@code tab_user.code}/{@code mobile}/{@code idCard} 三者任一精确相等匹配得到
     * {@code userId}，"组织编码"（{@link UpstreamPositionPseudoFieldCode#ORG_CODE}）按
     * {@code tab_org.code} 匹配得到 {@code orgId}，任一匹配不到或匹配到多条时该行判定
     * 失败——这两步只负责解析任职记录归属的所属人员、所属组织这层外键关系，不属于本次改动
     * 范围；在此基础上，再叠加字段映射标记为"主键标识"的系统字段（一个或多个，
     * 联合、AND 语义，见 {@link #appendPrimaryKeyConditions}）在该所属人员、所属组织
     * 范围内匹配已有任职记录，替换掉"提供了 {@code positionType} 就按它匹配、否则任选
     * 一条"的旧逻辑（upstream-field-mapping-primary-key change）。
     *
     * @param row                  转换后的一行数据（{@code positionType} 等 POSITION bizType 元数据字段）
     * @param rawRow               取数阶段拉取到的原始行（转换前），解析人员标识/组织编码
     * @param primaryKeyFieldCodes 标记为主键标识的系统字段编码列表
     */
    private void upsertPosition(Map<String, Object> row, Map<String, Object> rawRow,
            List<String> primaryKeyFieldCodes) {
        Map<String, Object> source = rawRow != null ? rawRow : Map.of();
        String userIdentifier = toText(source.get(UpstreamPositionPseudoFieldCode.USER_IDENTIFIER));
        if (!StringUtils.hasText(userIdentifier)) {
            throw new BusinessException("所属人员标识不能为空");
        }
        UserEntity user = findSingleActive(userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                .and(w -> w.eq(UserEntity::getCode, userIdentifier)
                        .or().eq(UserEntity::getMobile, userIdentifier)
                        .or().eq(UserEntity::getIdCard, userIdentifier))
                .ne(UserEntity::getStatus, UserStatus.DELETED)));
        if (user == null) {
            throw new BusinessException("所属人员标识[" + userIdentifier + "]无法匹配到已有人员记录");
        }

        String orgCode = toText(source.get(UpstreamPositionPseudoFieldCode.ORG_CODE));
        OrgEntity org = StringUtils.hasText(orgCode) ? findSingleActive(
                orgMapper.selectList(new LambdaQueryWrapper<OrgEntity>()
                        .eq(OrgEntity::getCode, orgCode)
                        .ne(OrgEntity::getStatus, OrgStatus.DELETED))) : null;
        if (org == null) {
            throw new BusinessException("所属组织编码[" + orgCode + "]无法匹配到已有组织记录");
        }

        QueryWrapper<UserPositionEntity> wrapper = new QueryWrapper<UserPositionEntity>()
                .eq("user_id", user.getId())
                .eq("org_id", org.getId())
                .ne("status", PositionStatus.DELETED);
        appendPrimaryKeyConditions(wrapper, row, primaryKeyFieldCodes);
        List<UserPositionEntity> matches = userPositionMapper.selectList(wrapper);
        if (matches.size() > 1) {
            throw new BusinessException("按主键字段匹配到多条已存在的任职记录，无法确定更新目标");
        }

        if (matches.isEmpty()) {
            PositionCreateRequest request = new PositionCreateRequest();
            bindProperties(request, row);
            request.setUserId(user.getId());
            request.setOrgId(org.getId());
            positionService.create(request);
        } else {
            PositionUpdateRequest request = new PositionUpdateRequest();
            bindProperties(request, row);
            request.setOrgId(org.getId());
            positionService.update(matches.get(0).getId(), request);
        }
    }

    /**
     * 把主键标识字段列表逐个转换为查询条件追加到给定的 {@link QueryWrapper} 上（多个
     * {@code eq} 默认 AND 语义，天然满足联合主键"全部相等"的要求）：字段编码通过
     * {@code com.baomidou.mybatisplus.core.toolkit.StringUtils#camelToUnderline} 转换成
     * 数据库列名——元数据字段目录里的字段本身就是"与业务表物理列一一对应的开放配置列"这一
     * 既有不变量，不存在字段编码与列名对不上驼峰/下划线转换规则的情形
     * （upstream-field-mapping-primary-key change design.md Decision 2）。任一主键字段
     * 在转换后的行里取值为空（{@code null} 或空白字符串）时，该行直接判定失败。
     *
     * @param wrapper              待追加条件的查询构造器
     * @param row                  转换后的一行数据，key 为系统字段编码
     * @param primaryKeyFieldCodes 标记为主键标识的系统字段编码列表
     */
    private void appendPrimaryKeyConditions(QueryWrapper<?> wrapper, Map<String, Object> row,
            List<String> primaryKeyFieldCodes) {
        for (String fieldCode : primaryKeyFieldCodes) {
            Object value = row.get(fieldCode);
            if (!StringUtils.hasText(toText(value))) {
                throw new BusinessException("主键字段[" + fieldCode + "]取值为空，无法判断新增或更新");
            }
            wrapper.eq(com.baomidou.mybatisplus.core.toolkit.StringUtils.camelToUnderline(fieldCode), value);
        }
    }

    /**
     * 在多条匹配记录中判定异常，单条时返回该记录，零条返回 {@code null}。
     *
     * @param matches 查询命中的记录列表
     * @param <T>     记录类型
     * @return 唯一命中的记录，未命中时为 {@code null}
     */
    private <T> T findSingleActive(List<T> matches) {
        if (matches.size() > 1) {
            throw new BusinessException("匹配到多条已存在记录，无法确定更新目标");
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    /**
     * 把本行数据按系统字段编码逐一设置到目标 CreateRequest/UpdateRequest 的同名属性上，
     * 跳过目标对象上不存在的属性；属性赋值失败（如数值格式不合法）时判定该行失败。
     *
     * <p>取值为 {@code null}/空白字符串且目标属性类型不是 {@code String}（如
     * {@code showOrder} 这类数值类型的原生列）时跳过本次设置，保留请求对象自身声明的
     * Java 默认值，避免意外触发该属性上独立于字段映射之外的硬编码
     * {@code @NotNull}（比照 {@code ImportRowExecutor.bindProperties} 的既有处理）；
     * 目标属性类型就是 {@code String} 时行为不变，仍然显式设置。
     *
     * <p>本方法只负责把"系统字段编码 → 值"逐一映射到同名可写属性上，不处理外键/层级类
     * 属性（{@code parentId}/{@code userId}/{@code orgId}）——这些属性由各自的调用方
     * （{@link #upsertOrg}/{@link #upsertPosition}）在调用本方法之后单独 {@code set}：
     * {@code parentId} 解析来源见 {@link #resolveParentId}，{@code userId}/{@code orgId}
     * 解析来源见 {@link UpstreamPositionPseudoFieldCode}。
     *
     * @param target 待填充的 CreateRequest/UpdateRequest 实例
     * @param row    本行数据，key 为系统字段编码
     */
    private void bindProperties(Object target, Map<String, Object> row) {
        BeanWrapper wrapper = new BeanWrapperImpl(target);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String fieldCode = entry.getKey();
            if (fieldCode == null || !wrapper.isWritableProperty(fieldCode)) {
                continue;
            }
            Object value = entry.getValue();
            boolean blank = value == null || (value instanceof String text && !StringUtils.hasText(text));
            if (blank && wrapper.getPropertyType(fieldCode) != String.class) {
                continue;
            }
            try {
                wrapper.setPropertyValue(fieldCode, value);
            } catch (Exception ex) {
                throw new BusinessException("字段[" + fieldCode + "]取值不合法：" + value);
            }
        }
    }

    /**
     * 把任意取值转换为文本，便于统一做 {@code code}/{@code mobile}/{@code idCard} 等
     * 匹配键的非空判断与比较。
     *
     * @param value 原始取值
     * @return 文本表示，{@code null} 时返回 {@code null}
     */
    private String toText(Object value) {
        return Objects.toString(value, null);
    }
}

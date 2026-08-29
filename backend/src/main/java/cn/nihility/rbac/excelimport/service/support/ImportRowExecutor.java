package cn.nihility.rbac.excelimport.service.support;

import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.constant.AppStatus;
import cn.nihility.rbac.app.dto.AppCreateRequest;
import cn.nihility.rbac.app.dto.AppUpdateRequest;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.app.service.AppService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.excelimport.constant.AppPseudoFieldCode;
import cn.nihility.rbac.excelimport.constant.OrgPseudoFieldCode;
import cn.nihility.rbac.excelimport.constant.PositionPseudoFieldCode;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigVO;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.operationlog.constant.OperationSource;
import cn.nihility.rbac.operationlog.context.OperationSourceContext;
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
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 批量导入单行处理器：按 {@code bizType} 把一行 Excel 数据（已按导入字段配置解析为
 * {@code fieldCode -> 单元格文本} 的映射）组装成对应业务模块的 CreateRequest/
 * UpdateRequest，按主键列查询是否已有匹配记录，命中零条调用 create、命中一条调用
 * update、命中多条判定失败，复用各业务模块既有的 create/update service（含其中的
 * 必填/正则/唯一性/锁定字段保护等既有校验），不重新实现一套校验规则
 * （design.md Decision 4）。
 *
 * <p>每行处理独立开启一个新事务（{@link Propagation#REQUIRES_NEW}），任何异常均
 * 直接向上抛出触发本行事务回滚，由调用方（{@code BatchImportServiceImpl}）捕获并
 * 转换为该行的失败明细，不影响其余行的处理（design.md Decision 3）。
 */
@Component
@RequiredArgsConstructor
public class ImportRowExecutor {

    /** 手动触发 Bean Validation（{@code @NotBlank}/{@code @Pattern}/{@code @Size} 等）的校验器。 */
    private final Validator validator;

    /** 组织数据访问接口，用于按编码查询已有记录做主键匹配。 */
    private final OrgMapper orgMapper;

    /** 组织业务逻辑接口，复用其既有的创建/更新校验与写入逻辑。 */
    private final OrgService orgService;

    /** 用户数据访问接口，用于按编码查询已有记录做主键匹配，POSITION 导入时还用于解析 userId。 */
    private final UserMapper userMapper;

    /** 用户业务逻辑接口，复用其既有的创建/更新校验与写入逻辑。 */
    private final UserService userService;

    /** 应用数据访问接口，用于按编码查询已有记录做主键匹配。 */
    private final AppMapper appMapper;

    /** 应用业务逻辑接口，复用其既有的创建/更新校验与写入逻辑。 */
    private final AppService appService;

    /** 用户任职记录数据访问接口，用于按 userId+orgId+positionType 复合键查询已有记录。 */
    private final UserPositionMapper userPositionMapper;

    /** 任职管理业务逻辑接口，复用其既有的创建/更新校验与写入逻辑。 */
    private final PositionService positionService;

    /** 字典列支持组件，用于识别字典下拉单选列并把单元格文本按 label 反查为 code。 */
    private final DictImportColumnSupport dictImportColumnSupport;

    /**
     * 处理一行 Excel 数据：先校验导入字段配置标记为必填的列是否均已提供非空值，
     * 再对字典下拉单选列做 label→code 反查（design.md Decision 3），最后按
     * {@code bizType} 路由到对应的新增/更新流程。
     *
     * 处理期间通过 {@link OperationSourceContext#mark(int)} 标记"当前操作来自 Excel
     * 导入"，使本行内部触发的 {@code OperationLogRecorder} 写库时能落库正确的操作
     * 来源；无论处理成功还是抛出异常，{@code finally} 块都会调用
     * {@link OperationSourceContext#clear()} 清除标记，避免线程复用时误继承
     * （fix-excel-import-followups design.md 决策 3）。
     *
     * @param bizType   业务对象类型：ORG/USER/POSITION/APP
     * @param rowValues 本行数据，key 为 {@code fieldCode}，value 为单元格文本（已 trim，缺失列缺省不存在于该 map）
     * @param configs   当前 bizType 下启用的导入字段配置列表
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void processRow(String bizType, Map<String, String> rowValues, List<ImportFieldConfigVO> configs) {
        checkRequiredColumns(rowValues, configs);
        resolveDictColumns(rowValues, configs);
        try {
            OperationSourceContext.mark(OperationSource.IMPORT);
            switch (bizType) {
                case FormFieldBizType.ORG -> processOrg(rowValues, configs);
                case FormFieldBizType.USER -> processUser(rowValues);
                case FormFieldBizType.APP -> processApp(rowValues, configs);
                case FormFieldBizType.POSITION -> processPosition(rowValues, configs);
                default -> throw new BusinessException("不支持的业务对象类型：" + bizType);
            }
        } finally {
            OperationSourceContext.clear();
        }
    }

    /**
     * 校验导入字段配置标记为必填的列在本行是否均已提供非空值。
     *
     * @param rowValues 本行数据
     * @param configs   当前 bizType 下启用的导入字段配置列表
     */
    private void checkRequiredColumns(Map<String, String> rowValues, List<ImportFieldConfigVO> configs) {
        for (ImportFieldConfigVO config : configs) {
            if (!Boolean.TRUE.equals(config.getIsRequired())) {
                continue;
            }
            if (!StringUtils.hasText(rowValues.get(config.getFieldCode()))) {
                throw new BusinessException("字段[" + config.getExcelHeaderName() + "]不能为空");
            }
        }
    }

    /**
     * 对本行数据中属于字典下拉单选列（{@code controlType=DICT}）的取值做 label→code
     * 反查：取值非空且能在该字典类型当前启用项的 label 集合中精确匹配到时，把
     * {@code rowValues} 中该列的值原地替换为对应的 code；非必填字典列取值为空时跳过
     * 反查（沿用既有"非必填列留空"的语义，不在此处重复必填校验）；取值非空但反查不到
     * 匹配项时，判定该行失败并在提示信息中包含该列的 Excel 表头文字（design.md
     * Decision 3/4）。
     *
     * @param rowValues 本行数据，反查命中的字典列取值会被原地替换为 code
     * @param configs   当前 bizType 下启用的导入字段配置列表
     */
    private void resolveDictColumns(Map<String, String> rowValues, List<ImportFieldConfigVO> configs) {
        Map<String, String> dictColumns = dictImportColumnSupport.resolveDictColumns(configs);
        if (dictColumns.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : dictColumns.entrySet()) {
            String fieldCode = entry.getKey();
            String label = rowValues.get(fieldCode);
            if (!StringUtils.hasText(label)) {
                continue;
            }
            String dictTypeCode = entry.getValue();
            String code = dictImportColumnSupport.resolveCodeByLabel(dictTypeCode, label);
            if (code == null) {
                throw new BusinessException(headerNameOf(configs, fieldCode, fieldCode) + "取值不是有效的字典选项");
            }
            rowValues.put(fieldCode, code);
        }
    }

    /**
     * 处理一行组织数据：按组织编码（{@code code}）匹配已有记录；"上级组织编码"列
     * （伪字段 {@link OrgPseudoFieldCode#PARENT_CODE}）与 POSITION/APP 的固定标识列
     * 一样为必填列，缺失/空白已由 {@link #checkRequiredColumns} 在到达本方法之前
     * 判定失败——本方法只需处理该列已提供非空值的情形：字面值为 {@code "0"} 时
     * {@code parentId} 直接置为 0（顶级组织本无上级）；其余取值按 {@code tab_org.code}
     * 匹配得到 {@code parentId}，匹配不到已有组织时该行判定失败（design.md
     * Decision 2 二次调整）。
     *
     * @param rowValues 本行数据
     * @param configs   当前 bizType（ORG）下启用的导入字段配置列表，用于解析
     *                  上级组织编码列对应的表头文字，拼装更友好的失败提示
     */
    private void processOrg(Map<String, String> rowValues, List<ImportFieldConfigVO> configs) {
        String code = rowValues.get("code");
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("组织编码不能为空");
        }

        String parentCodeHeader = headerNameOf(configs, OrgPseudoFieldCode.PARENT_CODE, "上级组织编码");
        String parentCode = rowValues.get(OrgPseudoFieldCode.PARENT_CODE);
        Long parentId;
        if ("0".equals(parentCode)) {
            parentId = 0L;
        } else {
            OrgEntity parent = findSingleActive(orgMapper.selectList(new LambdaQueryWrapper<OrgEntity>()
                    .eq(OrgEntity::getCode, parentCode)
                    .ne(OrgEntity::getStatus, OrgStatus.DELETED)));
            if (parent == null) {
                throw new BusinessException(parentCodeHeader + "无法匹配到已有组织记录");
            }
            parentId = parent.getId();
        }

        List<OrgEntity> matches = orgMapper.selectList(new LambdaQueryWrapper<OrgEntity>()
                .eq(OrgEntity::getCode, code)
                .ne(OrgEntity::getStatus, OrgStatus.DELETED));
        if (matches.size() > 1) {
            throw new BusinessException("匹配到多条已存在记录，无法确定更新目标");
        }
        if (matches.isEmpty()) {
            OrgCreateRequest request = new OrgCreateRequest();
            bindProperties(request, rowValues);
            request.setParentId(parentId);
            validateRequest(request);
            orgService.create(request);
        } else {
            OrgUpdateRequest request = new OrgUpdateRequest();
            bindProperties(request, rowValues);
            request.setParentId(parentId);
            validateRequest(request);
            orgService.update(matches.get(0).getId(), request);
        }
    }

    /**
     * 处理一行用户数据：按用户编号（{@code code}）匹配已有记录。
     *
     * @param rowValues 本行数据
     */
    private void processUser(Map<String, String> rowValues) {
        String code = rowValues.get("code");
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("用户编号不能为空");
        }
        List<UserEntity> matches = userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getCode, code)
                .ne(UserEntity::getStatus, UserStatus.DELETED));
        if (matches.size() > 1) {
            throw new BusinessException("匹配到多条已存在记录，无法确定更新目标");
        }
        if (matches.isEmpty()) {
            UserCreateRequest request = new UserCreateRequest();
            bindProperties(request, rowValues);
            validateRequest(request);
            userService.create(request);
        } else {
            UserUpdateRequest request = new UserUpdateRequest();
            bindProperties(request, rowValues);
            validateRequest(request);
            userService.update(matches.get(0).getId(), request);
        }
    }

    /**
     * 处理一行应用数据：按应用编码（{@code code}）匹配已有记录；"负责人编号"列按
     * {@code tab_user.code} 匹配得到 {@code ownerId}、"组织编码"列按
     * {@code tab_org.code} 匹配得到 {@code orgId}（{@link AppCreateRequest}/
     * {@link AppUpdateRequest} 上二者均为必填但不属于表单字段定义体系，做法比照
     * {@link #processPosition}），任一匹配不到时该行判定失败并注明具体是哪一列
     * 无法匹配。
     *
     * @param rowValues 本行数据
     * @param configs   当前 bizType（APP）下启用的导入字段配置列表，用于解析
     *                  负责人编号/组织编码列对应的表头文字，拼装更友好的失败提示
     */
    private void processApp(Map<String, String> rowValues, List<ImportFieldConfigVO> configs) {
        String code = rowValues.get("code");
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("应用编码不能为空");
        }

        String ownerCodeHeader = headerNameOf(configs, AppPseudoFieldCode.OWNER_CODE, "负责人编号");
        String orgCodeHeader = headerNameOf(configs, AppPseudoFieldCode.ORG_CODE, "组织编码");

        String ownerCode = rowValues.get(AppPseudoFieldCode.OWNER_CODE);
        UserEntity owner = StringUtils.hasText(ownerCode) ? findSingleActive(
                userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getCode, ownerCode)
                        .ne(UserEntity::getStatus, UserStatus.DELETED))) : null;
        if (owner == null) {
            throw new BusinessException(ownerCodeHeader + "无法匹配到已有人员记录");
        }

        String orgCode = rowValues.get(AppPseudoFieldCode.ORG_CODE);
        OrgEntity org = StringUtils.hasText(orgCode) ? findSingleActive(
                orgMapper.selectList(new LambdaQueryWrapper<OrgEntity>()
                        .eq(OrgEntity::getCode, orgCode)
                        .ne(OrgEntity::getStatus, OrgStatus.DELETED))) : null;
        if (org == null) {
            throw new BusinessException(orgCodeHeader + "无法匹配到已有组织记录");
        }

        List<AppEntity> matches = appMapper.selectList(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getCode, code)
                .ne(AppEntity::getStatus, AppStatus.DELETED));
        if (matches.size() > 1) {
            throw new BusinessException("匹配到多条已存在记录，无法确定更新目标");
        }
        if (matches.isEmpty()) {
            AppCreateRequest request = new AppCreateRequest();
            bindProperties(request, rowValues);
            request.setOwnerId(owner.getId());
            request.setOrgId(org.getId());
            validateRequest(request);
            appService.create(request);
        } else {
            AppUpdateRequest request = new AppUpdateRequest();
            bindProperties(request, rowValues);
            request.setOwnerId(owner.getId());
            request.setOrgId(org.getId());
            validateRequest(request);
            appService.update(matches.get(0).getId(), request);
        }
    }

    /**
     * 处理一行任职数据：把"人员标识"列按 {@code tab_user.code}、{@code mobile}、
     * {@code idCard} 三者任一精确相等匹配得到 {@code userId}（编号、手机号、身份证号
     * 中任意一种取值都能命中，不要求预先声明具体是哪一种）、"组织编码"列按
     * {@code tab_org.code} 匹配得到 {@code orgId}，任一匹配不到时该行判定失败并注明
     * 具体是哪一列无法匹配；再按 {@code userId+orgId+positionType} 复合键匹配已有
     * 任职记录。
     *
     * @param rowValues 本行数据
     * @param configs   当前 bizType（POSITION）下启用的导入字段配置列表，用于解析
     *                  人员标识/组织编码列对应的表头文字，拼装更友好的失败提示
     */
    private void processPosition(Map<String, String> rowValues, List<ImportFieldConfigVO> configs) {
        String userCodeHeader = headerNameOf(configs, PositionPseudoFieldCode.USER_CODE, "人员编号");
        String orgCodeHeader = headerNameOf(configs, PositionPseudoFieldCode.ORG_CODE, "组织编码");

        String userCode = rowValues.get(PositionPseudoFieldCode.USER_CODE);
        UserEntity user = StringUtils.hasText(userCode) ? findSingleActive(
                userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                        .and(w -> w.eq(UserEntity::getCode, userCode)
                                .or().eq(UserEntity::getMobile, userCode)
                                .or().eq(UserEntity::getIdCard, userCode))
                        .ne(UserEntity::getStatus, UserStatus.DELETED))) : null;
        if (user == null) {
            throw new BusinessException(userCodeHeader + "无法匹配到已有人员记录");
        }

        String orgCode = rowValues.get(PositionPseudoFieldCode.ORG_CODE);
        OrgEntity org = StringUtils.hasText(orgCode) ? findSingleActive(
                orgMapper.selectList(new LambdaQueryWrapper<OrgEntity>()
                        .eq(OrgEntity::getCode, orgCode)
                        .ne(OrgEntity::getStatus, OrgStatus.DELETED))) : null;
        if (org == null) {
            throw new BusinessException(orgCodeHeader + "无法匹配到已有组织记录");
        }

        String positionType = rowValues.get("positionType");
        LambdaQueryWrapper<UserPositionEntity> wrapper = new LambdaQueryWrapper<UserPositionEntity>()
                .eq(UserPositionEntity::getUserId, user.getId())
                .eq(UserPositionEntity::getOrgId, org.getId())
                .ne(UserPositionEntity::getStatus, PositionStatus.DELETED);
        if (StringUtils.hasText(positionType)) {
            wrapper.eq(UserPositionEntity::getPositionType, positionType);
        }
        List<UserPositionEntity> matches = userPositionMapper.selectList(wrapper);
        if (matches.size() > 1) {
            throw new BusinessException("匹配到多条已存在记录，无法确定更新目标");
        }

        if (matches.isEmpty()) {
            PositionCreateRequest request = new PositionCreateRequest();
            bindProperties(request, rowValues);
            request.setUserId(user.getId());
            request.setOrgId(org.getId());
            validateRequest(request);
            positionService.create(request);
        } else {
            PositionUpdateRequest request = new PositionUpdateRequest();
            bindProperties(request, rowValues);
            request.setOrgId(org.getId());
            validateRequest(request);
            positionService.update(matches.get(0).getId(), request);
        }
    }

    /**
     * 在多条匹配记录中判定异常，单条时返回该记录，零条返回 {@code null}，供
     * {@link #processOrg}/{@link #processApp}/{@link #processPosition} 复用统一的
     * "零/一/多"判定语义。
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
     * 从导入字段配置列表中解析指定伪字段标识对应的 Excel 表头文字，未配置时回退到
     * 给定的默认文案（正常不会发生，POSITION/APP/ORG 的固定标识列均由数据库迁移
     * 预置）。
     *
     * @param configs      导入字段配置列表
     * @param fieldCode    字段标识
     * @param defaultLabel 未找到配置时的默认表头文案
     * @return 表头文字
     */
    private String headerNameOf(List<ImportFieldConfigVO> configs, String fieldCode, String defaultLabel) {
        return configs.stream()
                .filter(config -> fieldCode.equals(config.getFieldCode()))
                .map(ImportFieldConfigVO::getExcelHeaderName)
                .findFirst()
                .orElse(defaultLabel);
    }

    /**
     * 把本行数据按 {@code fieldCode} 逐一设置到目标 CreateRequest/UpdateRequest 的
     * 同名属性上（fieldCode 与 DTO 属性名按项目约定保持一致），跳过 POSITION 专属的
     * 伪字段（{@code __userCode}/{@code __orgCode}，由调用方另行解析）与目标对象上
     * 不存在的属性；属性赋值失败（如数值格式不合法）时判定该行失败。
     *
     * <p>单元格文本为空白且目标属性类型不是 {@code String}（如 {@code showOrder} 这类
     * 数值类型的原生列）时跳过本次设置，保留请求对象自身声明的 Java 默认值（如
     * {@code showOrder} 默认 {@code 0}），避免 Spring 把空字符串隐式转换成 {@code null}
     * 从而意外触发该属性上独立于"导入字段配置"必填开关之外的硬编码 {@code @NotNull}
     * （design.md 决策 2）；目标属性类型就是 {@code String} 时（备注、扩展字段等）行为
     * 不变，仍然显式设置为空字符串，保持"导入 = 整行覆盖"的既有语义。
     *
     * @param target    待填充的 CreateRequest/UpdateRequest 实例
     * @param rowValues 本行数据
     */
    private void bindProperties(Object target, Map<String, String> rowValues) {
        BeanWrapper wrapper = new BeanWrapperImpl(target);
        for (Map.Entry<String, String> entry : rowValues.entrySet()) {
            String fieldCode = entry.getKey();
            if (fieldCode.startsWith("__")) {
                continue;
            }
            if (!wrapper.isWritableProperty(fieldCode)) {
                continue;
            }
            if (!StringUtils.hasText(entry.getValue()) && wrapper.getPropertyType(fieldCode) != String.class) {
                continue;
            }
            try {
                wrapper.setPropertyValue(fieldCode, entry.getValue());
            } catch (Exception ex) {
                throw new BusinessException("字段[" + fieldCode + "]取值不合法：" + entry.getValue());
            }
        }
    }

    /**
     * 手动触发 Bean Validation（{@code @NotBlank}/{@code @Pattern}/{@code @Size} 等），
     * 因为本引擎直接构造请求对象调用 service 方法，不经过控制器的 {@code @Valid}，
     * 校验注解不会自动生效；违反任一约束即判定该行失败。
     *
     * @param request 待校验的请求对象
     */
    private void validateRequest(Object request) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining("；"));
            throw new BusinessException(message);
        }
    }
}

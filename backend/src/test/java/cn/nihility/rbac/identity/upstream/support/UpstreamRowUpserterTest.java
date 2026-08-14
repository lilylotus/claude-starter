package cn.nihility.rbac.identity.upstream.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.identity.upstream.constant.UpstreamDataType;
import cn.nihility.rbac.identity.upstream.constant.UpstreamPositionPseudoFieldCode;
import cn.nihility.rbac.org.constant.OrgStatus;
import cn.nihility.rbac.org.dto.OrgCreateRequest;
import cn.nihility.rbac.org.dto.OrgUpdateRequest;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.org.service.OrgService;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UpstreamRowUpserter} 的单元测试，覆盖组织/用户/任职各自按调用方传入的主键标识
 * 字段（{@code primaryKeyFieldCodes}）动态匹配的新增/更新/联合主键/主键取值为空/多条
 * 匹配失败场景（upstream-field-mapping-primary-key change tasks.md 6.1），组织
 * "上级组织编码"通过字段映射转换后行的系统字段 {@code parentCode} 解析：未配置/取值为空/
 * 匹配到已有组织/匹配不到已有组织四种场景（upstream-org-parent-code-field-mapping
 * change tasks.md 3.1/3.2），以及匹配到已有记录但数据与当前实际值完全一致时跳过更新的
 * 场景（upstream-sync-skip-noop-update change tasks.md 2.1）。
 */
@ExtendWith(MockitoExtension.class)
class UpstreamRowUpserterTest {

    /** 被测组件的组织数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private OrgMapper orgMapper;

    /** 被测组件的组织业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private OrgService orgService;

    /** 被测组件的用户数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserMapper userMapper;

    /** 被测组件的用户业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private UserService userService;

    /** 被测组件的任职数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserPositionMapper userPositionMapper;

    /** 被测组件的任职业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private PositionService positionService;

    /** 被测组件实例。 */
    private UpstreamRowUpserter upstreamRowUpserter;

    /**
     * 每个用例执行前重新构造被测组件。
     */
    @BeforeEach
    void setUp() {
        upstreamRowUpserter = new UpstreamRowUpserter(orgMapper, orgService, userMapper, userService,
                userPositionMapper, positionService);
    }

    /**
     * 组织编码在未删除的组织中不存在匹配记录时，应走新增流程，调用组织模块既有的
     * create 方法；转换后行未携带系统字段 {@code parentCode}（即字段映射未配置该字段）
     * 时，应视为顶级组织，{@code parentId} 落地为 0，不判定该行失败。
     */
    @Test
    void upsertRow_shouldCreateOrg_whenNoMatch() {
        when(orgMapper.selectList(any())).thenReturn(java.util.List.of());
        Map<String, Object> row = Map.of("code", "ORG001", "name", "测试组织");

        upstreamRowUpserter.upsertRow(UpstreamDataType.ORG, row, row, List.of("code"));

        ArgumentCaptor<OrgCreateRequest> captor = ArgumentCaptor.forClass(OrgCreateRequest.class);
        verify(orgService).create(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("ORG001");
        assertThat(captor.getValue().getName()).isEqualTo("测试组织");
        assertThat(captor.getValue().getParentId()).isEqualTo(0L);
    }

    /**
     * 字段映射配置了 {@code parentCode} 但转换后取值为空白字符串时，同样视为顶级组织，
     * {@code parentId} 落地为 0，不判定该行失败。
     */
    @Test
    void upsertRow_shouldCreateOrg_whenParentCodeBlank() {
        when(orgMapper.selectList(any())).thenReturn(java.util.List.of());
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("code", "ORG001");
        row.put("name", "测试组织");
        row.put("parentCode", "  ");

        upstreamRowUpserter.upsertRow(UpstreamDataType.ORG, row, row, List.of("code"));

        ArgumentCaptor<OrgCreateRequest> captor = ArgumentCaptor.forClass(OrgCreateRequest.class);
        verify(orgService).create(captor.capture());
        assertThat(captor.getValue().getParentId()).isEqualTo(0L);
    }

    /**
     * 字段映射转换后行的 {@code parentCode} 取值能唯一匹配到一条已有的未删除组织时，
     * {@code parentId} 取自匹配结果，而不是默认值 0。
     */
    @Test
    void upsertRow_shouldCreateOrg_whenParentCodeMatched() {
        OrgEntity parent = OrgEntity.builder().id(9L).code("ROOT").status(OrgStatus.ENABLED).build();
        when(orgMapper.selectList(any()))
                .thenReturn(java.util.List.of())
                .thenReturn(java.util.List.of(parent));
        Map<String, Object> row = Map.of("code", "ORG001", "name", "测试组织", "parentCode", "ROOT");

        upstreamRowUpserter.upsertRow(UpstreamDataType.ORG, row, row, List.of("code"));

        ArgumentCaptor<OrgCreateRequest> captor = ArgumentCaptor.forClass(OrgCreateRequest.class);
        verify(orgService).create(captor.capture());
        assertThat(captor.getValue().getParentId()).isEqualTo(9L);
    }

    /**
     * 字段映射转换后行的 {@code parentCode} 取值在未删除的组织中不存在匹配记录时，应
     * 判定该行失败，明确提示是上级组织编码无法匹配，不调用 create/update。
     */
    @Test
    void upsertRow_shouldFailOrg_whenParentCodeNotFound() {
        when(orgMapper.selectList(any()))
                .thenReturn(java.util.List.of())
                .thenReturn(java.util.List.of());
        Map<String, Object> row = Map.of("code", "ORG001", "name", "测试组织", "parentCode", "NOT_EXIST");

        assertThatThrownBy(() -> upstreamRowUpserter.upsertRow(UpstreamDataType.ORG, row, row, List.of("code")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上级组织编码")
                .hasMessageContaining("无法匹配");
    }

    /**
     * 组织编码匹配到一条已存在的未删除组织时，应走更新流程，调用组织模块既有的
     * update 方法。
     */
    @Test
    void upsertRow_shouldUpdateOrg_whenOneMatch() {
        OrgEntity existing = OrgEntity.builder().id(5L).code("ORG001").status(OrgStatus.ENABLED).build();
        when(orgMapper.selectList(any())).thenReturn(java.util.List.of(existing));
        Map<String, Object> row = Map.of("code", "ORG001", "name", "测试组织（更新）");

        upstreamRowUpserter.upsertRow(UpstreamDataType.ORG, row, row, List.of("code"));

        ArgumentCaptor<OrgUpdateRequest> captor = ArgumentCaptor.forClass(OrgUpdateRequest.class);
        verify(orgService).update(eq(5L), captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("测试组织（更新）");
    }

    /**
     * 组织编码匹配到一条已存在的未删除组织，且本次同步数据与该记录当前实际值完全一致
     * 时，应跳过更新（不调用 create/update），不产生无意义的写入与操作日志
     * （upstream-sync-skip-noop-update change design.md Decision 1/2）。
     */
    @Test
    void upsertRow_shouldSkipUpdate_whenOrgUnchanged() {
        OrgEntity existing = OrgEntity.builder().id(5L).code("ORG001").name("测试组织").parentId(0L).showOrder(0)
                .status(OrgStatus.ENABLED).build();
        when(orgMapper.selectList(any())).thenReturn(java.util.List.of(existing));
        Map<String, Object> row = Map.of("code", "ORG001", "name", "测试组织");

        upstreamRowUpserter.upsertRow(UpstreamDataType.ORG, row, row, List.of("code"));

        verify(orgService, never()).update(any(), any());
        verify(orgService, never()).create(any());
    }

    /**
     * 组织按联合主键（两个字段：{@code code}+{@code name}）匹配不到已有记录时应走新增
     * 流程（upstream-field-mapping-primary-key change：支持联合主键场景）。
     */
    @Test
    void upsertRow_shouldCreateOrg_whenCompositePrimaryKeyNoMatch() {
        when(orgMapper.selectList(any())).thenReturn(java.util.List.of());
        Map<String, Object> row = Map.of("code", "ORG001", "name", "测试组织");

        upstreamRowUpserter.upsertRow(UpstreamDataType.ORG, row, row, List.of("code", "name"));

        ArgumentCaptor<OrgCreateRequest> captor = ArgumentCaptor.forClass(OrgCreateRequest.class);
        verify(orgService).create(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("ORG001");
    }

    /**
     * 组织按联合主键（两个字段）匹配到一条已存在记录时应走更新流程。
     */
    @Test
    void upsertRow_shouldUpdateOrg_whenCompositePrimaryKeyOneMatch() {
        OrgEntity existing = OrgEntity.builder().id(5L).code("ORG001").status(OrgStatus.ENABLED).build();
        when(orgMapper.selectList(any())).thenReturn(java.util.List.of(existing));
        Map<String, Object> row = Map.of("code", "ORG001", "name", "测试组织（更新）");

        upstreamRowUpserter.upsertRow(UpstreamDataType.ORG, row, row, List.of("code", "name"));

        ArgumentCaptor<OrgUpdateRequest> captor = ArgumentCaptor.forClass(OrgUpdateRequest.class);
        verify(orgService).update(eq(5L), captor.capture());
    }

    /**
     * 组织标记的主键字段之一在转换后的行里取值为空时，该行应判定失败，不调用
     * create/update/查询。
     */
    @Test
    void upsertRow_shouldFailOrg_whenPrimaryKeyValueBlank() {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("code", "ORG001");
        row.put("name", "  ");

        assertThatThrownBy(() -> upstreamRowUpserter.upsertRow(UpstreamDataType.ORG, row, row,
                List.of("code", "name")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("主键字段")
                .hasMessageContaining("name");
    }

    /**
     * 组织编码匹配到多条已存在的未删除组织时，应判定该行失败，不调用 create/update。
     */
    @Test
    void upsertRow_shouldFailOrg_whenMultipleMatch() {
        OrgEntity match1 = OrgEntity.builder().id(5L).code("ORG001").status(OrgStatus.ENABLED).build();
        OrgEntity match2 = OrgEntity.builder().id(6L).code("ORG001").status(OrgStatus.ENABLED).build();
        when(orgMapper.selectList(any())).thenReturn(java.util.List.of(match1, match2));
        Map<String, Object> row = Map.of("code", "ORG001", "name", "测试组织");

        assertThatThrownBy(() -> upstreamRowUpserter.upsertRow(UpstreamDataType.ORG, row, row, List.of("code")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("多条");
    }

    /**
     * 用户编号在未删除的用户中不存在匹配记录时，应走新增流程。
     */
    @Test
    void upsertRow_shouldCreateUser_whenNoMatch() {
        when(userMapper.selectList(any())).thenReturn(java.util.List.of());
        Map<String, Object> row = Map.of("code", "U001", "name", "张三");

        upstreamRowUpserter.upsertRow(UpstreamDataType.USER, row, row, List.of("code"));

        ArgumentCaptor<UserCreateRequest> captor = ArgumentCaptor.forClass(UserCreateRequest.class);
        verify(userService).create(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("U001");
        assertThat(captor.getValue().getName()).isEqualTo("张三");
    }

    /**
     * 用户编号匹配到一条已存在的未删除用户时，应走更新流程。
     */
    @Test
    void upsertRow_shouldUpdateUser_whenOneMatch() {
        UserEntity existing = UserEntity.builder().id(9L).code("U001").status(UserStatus.ENABLED).build();
        when(userMapper.selectList(any())).thenReturn(java.util.List.of(existing));
        Map<String, Object> row = Map.of("code", "U001", "name", "张三（更新）");

        upstreamRowUpserter.upsertRow(UpstreamDataType.USER, row, row, List.of("code"));

        ArgumentCaptor<UserUpdateRequest> captor = ArgumentCaptor.forClass(UserUpdateRequest.class);
        verify(userService).update(eq(9L), captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("张三（更新）");
    }

    /**
     * 用户编号匹配到一条已存在的未删除用户，且本次同步数据与该记录当前实际值完全一致
     * （含 {@code UserUpdateRequest.gender} 默认值 {@code "unknown"}）时，应跳过更新
     * （upstream-sync-skip-noop-update change design.md Decision 1/2）。
     */
    @Test
    void upsertRow_shouldSkipUpdate_whenUserUnchanged() {
        UserEntity existing = UserEntity.builder().id(9L).code("U001").name("张三").gender("unknown").showOrder(0)
                .status(UserStatus.ENABLED).build();
        when(userMapper.selectList(any())).thenReturn(java.util.List.of(existing));
        Map<String, Object> row = Map.of("code", "U001", "name", "张三");

        upstreamRowUpserter.upsertRow(UpstreamDataType.USER, row, row, List.of("code"));

        verify(userService, never()).update(any(), any());
        verify(userService, never()).create(any());
    }

    /**
     * 用户按联合主键（{@code code}+{@code idCard} 两个字段）匹配不到已有记录时应走新增
     * 流程。
     */
    @Test
    void upsertRow_shouldCreateUser_whenCompositePrimaryKeyNoMatch() {
        when(userMapper.selectList(any())).thenReturn(java.util.List.of());
        Map<String, Object> row = Map.of("code", "U001", "idCard", "110101199001010011");

        upstreamRowUpserter.upsertRow(UpstreamDataType.USER, row, row, List.of("code", "idCard"));

        ArgumentCaptor<UserCreateRequest> captor = ArgumentCaptor.forClass(UserCreateRequest.class);
        verify(userService).create(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("U001");
    }

    /**
     * 用户按联合主键（两个字段）匹配到一条已存在记录时应走更新流程。
     */
    @Test
    void upsertRow_shouldUpdateUser_whenCompositePrimaryKeyOneMatch() {
        UserEntity existing = UserEntity.builder().id(9L).code("U001").idCard("110101199001010011")
                .status(UserStatus.ENABLED).build();
        when(userMapper.selectList(any())).thenReturn(java.util.List.of(existing));
        Map<String, Object> row = Map.of("code", "U001", "idCard", "110101199001010011");

        upstreamRowUpserter.upsertRow(UpstreamDataType.USER, row, row, List.of("code", "idCard"));

        ArgumentCaptor<UserUpdateRequest> captor = ArgumentCaptor.forClass(UserUpdateRequest.class);
        verify(userService).update(eq(9L), captor.capture());
    }

    /**
     * 用户标记的主键字段之一在转换后的行里取值为空时，该行应判定失败。
     */
    @Test
    void upsertRow_shouldFailUser_whenPrimaryKeyValueBlank() {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("code", "U001");
        row.put("idCard", null);

        assertThatThrownBy(() -> upstreamRowUpserter.upsertRow(UpstreamDataType.USER, row, row,
                List.of("code", "idCard")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("主键字段")
                .hasMessageContaining("idCard");
    }

    /**
     * 用户编号匹配到多条已存在的未删除用户时，应判定该行失败。
     */
    @Test
    void upsertRow_shouldFailUser_whenMultipleMatch() {
        UserEntity match1 = UserEntity.builder().id(1L).code("U001").status(UserStatus.ENABLED).build();
        UserEntity match2 = UserEntity.builder().id(2L).code("U001").status(UserStatus.ENABLED).build();
        when(userMapper.selectList(any())).thenReturn(java.util.List.of(match1, match2));
        Map<String, Object> row = Map.of("code", "U001", "name", "张三");

        assertThatThrownBy(() -> upstreamRowUpserter.upsertRow(UpstreamDataType.USER, row, row, List.of("code")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("多条");
    }

    /**
     * 任职人员标识（按用户编号匹配）、组织编码均匹配成功且按主键字段（单字段
     * {@code positionType}）未命中已有记录时，应走新增流程，{@code userId}/{@code orgId}
     * 取自解析结果。
     */
    @Test
    void upsertRow_shouldCreatePosition_whenNoMatch() {
        UserEntity user = UserEntity.builder().id(1L).code("U001").status(UserStatus.ENABLED).build();
        OrgEntity org = OrgEntity.builder().id(2L).code("ORG001").status(OrgStatus.ENABLED).build();
        when(userMapper.selectList(any())).thenReturn(java.util.List.of(user));
        when(orgMapper.selectList(any())).thenReturn(java.util.List.of(org));
        when(userPositionMapper.selectList(any())).thenReturn(java.util.List.of());
        Map<String, Object> transformedRow = Map.of("positionType", "primary");
        Map<String, Object> rawRow = Map.of(
                UpstreamPositionPseudoFieldCode.USER_IDENTIFIER, "U001",
                UpstreamPositionPseudoFieldCode.ORG_CODE, "ORG001");

        upstreamRowUpserter.upsertRow(UpstreamDataType.POSITION, transformedRow, rawRow, List.of("positionType"));

        ArgumentCaptor<PositionCreateRequest> captor = ArgumentCaptor.forClass(PositionCreateRequest.class);
        verify(positionService).create(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getOrgId()).isEqualTo(2L);
        assertThat(captor.getValue().getPositionType()).isEqualTo("primary");
    }

    /**
     * 在 {@code userId}/{@code orgId} 匹配的基础上，按主键字段（单字段
     * {@code positionType}）命中一条已有任职记录时，应走更新流程。
     */
    @Test
    void upsertRow_shouldUpdatePosition_whenOneMatch() {
        UserEntity user = UserEntity.builder().id(1L).code("U001").status(UserStatus.ENABLED).build();
        OrgEntity org = OrgEntity.builder().id(2L).code("ORG001").status(OrgStatus.ENABLED).build();
        UserPositionEntity existing = UserPositionEntity.builder().id(9L).userId(1L).orgId(2L)
                .positionType("primary").build();
        when(userMapper.selectList(any())).thenReturn(java.util.List.of(user));
        when(orgMapper.selectList(any())).thenReturn(java.util.List.of(org));
        when(userPositionMapper.selectList(any())).thenReturn(java.util.List.of(existing));
        Map<String, Object> transformedRow = Map.of("positionType", "primary");
        Map<String, Object> rawRow = Map.of(
                UpstreamPositionPseudoFieldCode.USER_IDENTIFIER, "U001",
                UpstreamPositionPseudoFieldCode.ORG_CODE, "ORG001");

        upstreamRowUpserter.upsertRow(UpstreamDataType.POSITION, transformedRow, rawRow, List.of("positionType"));

        ArgumentCaptor<PositionUpdateRequest> captor = ArgumentCaptor.forClass(PositionUpdateRequest.class);
        verify(positionService).update(eq(9L), captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo(2L);
        assertThat(captor.getValue().getPositionType()).isEqualTo("primary");
    }

    /**
     * 任职匹配到一条已存在记录，且本次同步数据（含解析出的 {@code orgId}）与该记录当前
     * 实际值完全一致时，应跳过更新（upstream-sync-skip-noop-update change design.md
     * Decision 1/2）。
     */
    @Test
    void upsertRow_shouldSkipUpdate_whenPositionUnchanged() {
        UserEntity user = UserEntity.builder().id(1L).code("U001").status(UserStatus.ENABLED).build();
        OrgEntity org = OrgEntity.builder().id(2L).code("ORG001").status(OrgStatus.ENABLED).build();
        UserPositionEntity existing = UserPositionEntity.builder().id(9L).userId(1L).orgId(2L)
                .positionType("primary").showOrder(0).build();
        when(userMapper.selectList(any())).thenReturn(java.util.List.of(user));
        when(orgMapper.selectList(any())).thenReturn(java.util.List.of(org));
        when(userPositionMapper.selectList(any())).thenReturn(java.util.List.of(existing));
        Map<String, Object> transformedRow = Map.of("positionType", "primary");
        Map<String, Object> rawRow = Map.of(
                UpstreamPositionPseudoFieldCode.USER_IDENTIFIER, "U001",
                UpstreamPositionPseudoFieldCode.ORG_CODE, "ORG001");

        upstreamRowUpserter.upsertRow(UpstreamDataType.POSITION, transformedRow, rawRow, List.of("positionType"));

        verify(positionService, never()).update(any(), any());
        verify(positionService, never()).create(any());
    }

    /**
     * 任职按联合主键（两个字段：{@code positionType}+{@code positionAddress}）匹配不到
     * 已有记录时应走新增流程。
     */
    @Test
    void upsertRow_shouldCreatePosition_whenCompositePrimaryKeyNoMatch() {
        UserEntity user = UserEntity.builder().id(1L).code("U001").status(UserStatus.ENABLED).build();
        OrgEntity org = OrgEntity.builder().id(2L).code("ORG001").status(OrgStatus.ENABLED).build();
        when(userMapper.selectList(any())).thenReturn(java.util.List.of(user));
        when(orgMapper.selectList(any())).thenReturn(java.util.List.of(org));
        when(userPositionMapper.selectList(any())).thenReturn(java.util.List.of());
        Map<String, Object> transformedRow = Map.of("positionType", "primary", "positionAddress", "总部");
        Map<String, Object> rawRow = Map.of(
                UpstreamPositionPseudoFieldCode.USER_IDENTIFIER, "U001",
                UpstreamPositionPseudoFieldCode.ORG_CODE, "ORG001");

        upstreamRowUpserter.upsertRow(UpstreamDataType.POSITION, transformedRow, rawRow,
                List.of("positionType", "positionAddress"));

        ArgumentCaptor<PositionCreateRequest> captor = ArgumentCaptor.forClass(PositionCreateRequest.class);
        verify(positionService).create(captor.capture());
        assertThat(captor.getValue().getPositionAddress()).isEqualTo("总部");
    }

    /**
     * 任职按联合主键（两个字段）匹配到一条已存在记录时应走更新流程。
     */
    @Test
    void upsertRow_shouldUpdatePosition_whenCompositePrimaryKeyOneMatch() {
        UserEntity user = UserEntity.builder().id(1L).code("U001").status(UserStatus.ENABLED).build();
        OrgEntity org = OrgEntity.builder().id(2L).code("ORG001").status(OrgStatus.ENABLED).build();
        UserPositionEntity existing = UserPositionEntity.builder().id(9L).userId(1L).orgId(2L)
                .positionType("primary").positionAddress("总部").build();
        when(userMapper.selectList(any())).thenReturn(java.util.List.of(user));
        when(orgMapper.selectList(any())).thenReturn(java.util.List.of(org));
        when(userPositionMapper.selectList(any())).thenReturn(java.util.List.of(existing));
        Map<String, Object> transformedRow = Map.of("positionType", "primary", "positionAddress", "总部");
        Map<String, Object> rawRow = Map.of(
                UpstreamPositionPseudoFieldCode.USER_IDENTIFIER, "U001",
                UpstreamPositionPseudoFieldCode.ORG_CODE, "ORG001");

        upstreamRowUpserter.upsertRow(UpstreamDataType.POSITION, transformedRow, rawRow,
                List.of("positionType", "positionAddress"));

        ArgumentCaptor<PositionUpdateRequest> captor = ArgumentCaptor.forClass(PositionUpdateRequest.class);
        verify(positionService).update(eq(9L), captor.capture());
    }

    /**
     * 任职标记的主键字段之一在转换后的行里取值为空时，该行应判定失败（此时人员/组织
     * 均已成功解析，仍应因主键字段取值缺失而失败）。
     */
    @Test
    void upsertRow_shouldFailPosition_whenPrimaryKeyValueBlank() {
        UserEntity user = UserEntity.builder().id(1L).code("U001").status(UserStatus.ENABLED).build();
        OrgEntity org = OrgEntity.builder().id(2L).code("ORG001").status(OrgStatus.ENABLED).build();
        when(userMapper.selectList(any())).thenReturn(java.util.List.of(user));
        when(orgMapper.selectList(any())).thenReturn(java.util.List.of(org));
        Map<String, Object> transformedRow = new java.util.HashMap<>();
        transformedRow.put("positionType", "");
        Map<String, Object> rawRow = Map.of(
                UpstreamPositionPseudoFieldCode.USER_IDENTIFIER, "U001",
                UpstreamPositionPseudoFieldCode.ORG_CODE, "ORG001");

        assertThatThrownBy(() -> upstreamRowUpserter.upsertRow(UpstreamDataType.POSITION, transformedRow, rawRow,
                List.of("positionType")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("主键字段")
                .hasMessageContaining("positionType");
    }

    /**
     * 人员标识取值不等于任何用户编号，但等于唯一一个未删除用户的手机号时，应按该
     * 手机号匹配到该用户并继续走新增流程。
     */
    @Test
    void upsertRow_shouldMatchUserByMobile_whenCodeNotMatched() {
        UserEntity user = UserEntity.builder().id(1L).code("U999").mobile("13800000000")
                .status(UserStatus.ENABLED).build();
        OrgEntity org = OrgEntity.builder().id(2L).code("ORG001").status(OrgStatus.ENABLED).build();
        when(userMapper.selectList(any())).thenReturn(java.util.List.of(user));
        when(orgMapper.selectList(any())).thenReturn(java.util.List.of(org));
        when(userPositionMapper.selectList(any())).thenReturn(java.util.List.of());
        Map<String, Object> transformedRow = Map.of("positionType", "primary");
        Map<String, Object> rawRow = Map.of(
                UpstreamPositionPseudoFieldCode.USER_IDENTIFIER, "13800000000",
                UpstreamPositionPseudoFieldCode.ORG_CODE, "ORG001");

        upstreamRowUpserter.upsertRow(UpstreamDataType.POSITION, transformedRow, rawRow, List.of("positionType"));

        ArgumentCaptor<PositionCreateRequest> captor = ArgumentCaptor.forClass(PositionCreateRequest.class);
        verify(positionService).create(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
    }

    /**
     * 人员标识在未删除的用户中不存在匹配记录（code/mobile/idCard 均不命中）时，应判定
     * 该行失败，不触及组织匹配与任职记录查询。
     */
    @Test
    void upsertRow_shouldFailPosition_whenUserIdentifierNotFound() {
        when(userMapper.selectList(any())).thenReturn(java.util.List.of());
        Map<String, Object> transformedRow = Map.of("positionType", "primary");
        Map<String, Object> rawRow = Map.of(
                UpstreamPositionPseudoFieldCode.USER_IDENTIFIER, "NOT_EXIST",
                UpstreamPositionPseudoFieldCode.ORG_CODE, "ORG001");

        assertThatThrownBy(() -> upstreamRowUpserter.upsertRow(UpstreamDataType.POSITION, transformedRow, rawRow,
                List.of("positionType")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("人员标识")
                .hasMessageContaining("无法匹配");
    }

    /**
     * 人员标识匹配到多个不同未删除用户（如手机号被重复使用）时，应判定该行失败，
     * 不触及组织匹配与任职记录查询。
     */
    @Test
    void upsertRow_shouldFailPosition_whenUserIdentifierMatchesMultiple() {
        UserEntity match1 = UserEntity.builder().id(1L).code("U001").mobile("13800000000")
                .status(UserStatus.ENABLED).build();
        UserEntity match2 = UserEntity.builder().id(2L).code("U002").mobile("13800000000")
                .status(UserStatus.ENABLED).build();
        when(userMapper.selectList(any())).thenReturn(java.util.List.of(match1, match2));
        Map<String, Object> transformedRow = Map.of("positionType", "primary");
        Map<String, Object> rawRow = Map.of(
                UpstreamPositionPseudoFieldCode.USER_IDENTIFIER, "13800000000",
                UpstreamPositionPseudoFieldCode.ORG_CODE, "ORG001");

        assertThatThrownBy(() -> upstreamRowUpserter.upsertRow(UpstreamDataType.POSITION, transformedRow, rawRow,
                List.of("positionType")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("多条");
    }

    /**
     * 人员标识匹配成功但组织编码在未删除的组织中不存在匹配记录时，应判定该行失败，
     * 明确提示是组织编码无法匹配。
     */
    @Test
    void upsertRow_shouldFailPosition_whenOrgCodeNotFound() {
        UserEntity user = UserEntity.builder().id(1L).code("U001").status(UserStatus.ENABLED).build();
        when(userMapper.selectList(any())).thenReturn(java.util.List.of(user));
        when(orgMapper.selectList(any())).thenReturn(java.util.List.of());
        Map<String, Object> transformedRow = Map.of("positionType", "primary");
        Map<String, Object> rawRow = Map.of(
                UpstreamPositionPseudoFieldCode.USER_IDENTIFIER, "U001",
                UpstreamPositionPseudoFieldCode.ORG_CODE, "ORG001");

        assertThatThrownBy(() -> upstreamRowUpserter.upsertRow(UpstreamDataType.POSITION, transformedRow, rawRow,
                List.of("positionType")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("组织编码")
                .hasMessageContaining("无法匹配");
    }

    /**
     * 在 {@code userId}/{@code orgId} 匹配的基础上，按主键字段（单字段
     * {@code positionType}）匹配到多条已存在任职记录时，应判定该行失败。
     */
    @Test
    void upsertRow_shouldFailPosition_whenMultiplePositionMatch() {
        UserEntity user = UserEntity.builder().id(1L).code("U001").status(UserStatus.ENABLED).build();
        OrgEntity org = OrgEntity.builder().id(2L).code("ORG001").status(OrgStatus.ENABLED).build();
        UserPositionEntity match1 = UserPositionEntity.builder().id(9L).userId(1L).orgId(2L)
                .positionType("primary").build();
        UserPositionEntity match2 = UserPositionEntity.builder().id(10L).userId(1L).orgId(2L)
                .positionType("primary").build();
        when(userMapper.selectList(any())).thenReturn(java.util.List.of(user));
        when(orgMapper.selectList(any())).thenReturn(java.util.List.of(org));
        when(userPositionMapper.selectList(any())).thenReturn(java.util.List.of(match1, match2));
        Map<String, Object> transformedRow = Map.of("positionType", "primary");
        Map<String, Object> rawRow = Map.of(
                UpstreamPositionPseudoFieldCode.USER_IDENTIFIER, "U001",
                UpstreamPositionPseudoFieldCode.ORG_CODE, "ORG001");

        assertThatThrownBy(() -> upstreamRowUpserter.upsertRow(UpstreamDataType.POSITION, transformedRow, rawRow,
                List.of("positionType")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("多条");
    }
}

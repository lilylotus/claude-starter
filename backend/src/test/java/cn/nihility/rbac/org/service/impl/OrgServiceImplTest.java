package cn.nihility.rbac.org.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.formfield.service.FormFieldDefinitionService;
import cn.nihility.rbac.formfield.support.FormFieldSnapshotSupport;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.org.constant.OrgStatus;
import cn.nihility.rbac.org.dto.OrgCreateRequest;
import cn.nihility.rbac.org.dto.OrgTreeNodeVO;
import cn.nihility.rbac.org.dto.OrgUpdateRequest;
import cn.nihility.rbac.org.dto.OrgVO;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.user.service.UserDisplayService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link OrgServiceImpl} 的单元测试，重点覆盖树形组装、编码唯一性校验、
 * 删除前置校验、操作日志记录调用等分支逻辑。
 */
@ExtendWith(MockitoExtension.class)
class OrgServiceImplTest {

    /** 被测服务的数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private OrgMapper orgMapper;

    /** 被测服务的操作日志记录组件依赖，使用 Mockito 打桩。 */
    @Mock
    private OperationLogRecorder operationLogRecorder;

    /** 被测服务的表单字段定义业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private FormFieldDefinitionService formFieldDefinitionService;

    /** 被测服务的操作日志扩展字段快照填充依赖，使用 Mockito 打桩。 */
    @Mock
    private FormFieldSnapshotSupport formFieldSnapshotSupport;

    /** 被测服务的管辖组织范围解析依赖，使用 Mockito 打桩。 */
    @Mock
    private OrgScopeService orgScopeService;

    /** 被测服务的当前登录操作人用户 id 解析依赖，使用 Mockito 打桩。 */
    @Mock
    private CurrentOperatorService currentOperatorService;

    /** 被测服务的审计字段展示名批量解析依赖，使用 Mockito 打桩。 */
    @Mock
    private UserDisplayService userDisplayService;

    /** 被测服务实例。 */
    private OrgServiceImpl orgService;

    /**
     * 每个用例执行前重新构造被测服务；实体/DTO 转换通过 {@code OrgConvert.INSTANCE}
     * 静态调用完成，无需在此注入或 mock。动态字段定义默认桩为空列表，
     * 各分支逻辑测试不受"表单字段定义"驱动的校验管线影响。管辖组织范围默认桩为
     * {@code Optional.empty()}（不受限制），使既有分支逻辑测试不受本次新增的
     * 管辖范围过滤影响，虚拟根节点等受限场景在下方单独的用例中覆盖。
     */
    @BeforeEach
    void setUp() {
        orgService = new OrgServiceImpl(orgMapper, operationLogRecorder, formFieldDefinitionService,
                formFieldSnapshotSupport, orgScopeService, currentOperatorService, userDisplayService);
        lenient().when(formFieldDefinitionService.listActiveByBizType(any())).thenReturn(List.of());
        lenient().when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.empty());
        lenient().when(currentOperatorService.resolveUserId()).thenReturn(1L);
        lenient().when(userDisplayService.resolveDisplayNames(any())).thenReturn(Map.of());
    }

    /**
     * 组织树应按父子关系正确嵌套，顶级节点（parentId 未命中任何已知节点）归入根列表。
     */
    @Test
    void getTree_shouldNestChildrenUnderTheirParent() {
        OrgEntity root = buildEntity(1L, "总公司", "ROOT", 0L, OrgStatus.ENABLED, 10);
        OrgEntity child = buildEntity(2L, "研发部", "DEV", 1L, OrgStatus.ENABLED, 5);
        when(orgMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(root, child));

        List<OrgTreeNodeVO> tree = orgService.getTree();

        assertThat(tree).hasSize(1);
        OrgTreeNodeVO rootNode = tree.get(0);
        assertThat(rootNode.getId()).isEqualTo(1L);
        assertThat(rootNode.getChildren()).hasSize(1);
        assertThat(rootNode.getChildren().get(0).getId()).isEqualTo(2L);
    }

    /**
     * 分页查询直属子组织时，应返回携带总条数、页码、每页条数的分页结果。
     */
    @Test
    void getChildren_shouldReturnPageResult_withRecordsAndTotal() {
        OrgEntity child = buildEntity(2L, "研发部", "DEV", 1L, OrgStatus.ENABLED, 5);
        Page<OrgEntity> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(List.of(child));
        when(orgMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);

        PageResult<OrgVO> pageResult = orgService.getChildren(1L, 1, 10);

        assertThat(pageResult.getTotal()).isEqualTo(1L);
        assertThat(pageResult.getPage()).isEqualTo(1);
        assertThat(pageResult.getPageSize()).isEqualTo(10);
        assertThat(pageResult.getRecords()).hasSize(1);
        assertThat(pageResult.getRecords().get(0).getId()).isEqualTo(2L);
    }

    /**
     * 树懒加载查询直属子组织时，应返回不分页的节点列表，且每个节点的 children 固定为空列表。
     */
    @Test
    void getChildrenTreeNodes_shouldReturnFlatNodes_withEmptyChildren() {
        OrgEntity child = buildEntity(2L, "研发部", "DEV", 1L, OrgStatus.ENABLED, 5);
        when(orgMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(child));

        List<OrgTreeNodeVO> nodes = orgService.getChildrenTreeNodes(1L);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).getId()).isEqualTo(2L);
        assertThat(nodes.get(0).getChildren()).isEmpty();
    }

    /**
     * 树懒加载查询未指定 parentId 时，应按顶级（0）处理。
     */
    @Test
    void getChildrenTreeNodes_shouldTreatNullParentIdAsRoot() {
        OrgEntity root = buildEntity(1L, "总公司", "ROOT", 0L, OrgStatus.ENABLED, 10);
        when(orgMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(root));

        List<OrgTreeNodeVO> nodes = orgService.getChildrenTreeNodes(null);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).getParentId()).isEqualTo(0L);
    }

    /**
     * 组织树受限（管辖范围为组织树中间层的一个节点）时，该节点应表现为"虚拟根节点"——
     * 其祖先节点不出现在响应中；不需要额外的"虚拟根节点"特判分支，这是过滤 +
     * 既有树组装算法的自然结果（org-scope-data-permission change design.md Decision 4）。
     */
    @Test
    void getTree_shouldExposeVirtualRoot_whenScopeRestrictedToMiddleOrg() {
        OrgEntity root = buildEntity(1L, "总公司", "ROOT", 0L, OrgStatus.ENABLED, 10);
        OrgEntity middle = buildEntity(2L, "研发中心", "RDC", 1L, OrgStatus.ENABLED, 5);
        OrgEntity leaf = buildEntity(3L, "研发一部", "RD1", 2L, OrgStatus.ENABLED, 1);
        when(orgMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(root, middle, leaf));
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.of(Set.of(2L, 3L)));

        List<OrgTreeNodeVO> tree = orgService.getTree();

        assertThat(tree).hasSize(1);
        OrgTreeNodeVO virtualRoot = tree.get(0);
        assertThat(virtualRoot.getId()).isEqualTo(2L);
        assertThat(virtualRoot.getChildren()).hasSize(1);
        assertThat(virtualRoot.getChildren().get(0).getId()).isEqualTo(3L);
    }

    /**
     * 分页查询直属子组织受限且请求顶层（parentId 为 0）时，管辖范围中间层节点应作为
     * "虚拟根节点"被识别出来，分页元信息需要基于过滤后的列表手工计算，不能再依赖
     * {@code orgMapper.selectPage} 的 {@code IPage} 元信息（design.md Decision 4 / tasks 4.4）。
     */
    @Test
    void getChildren_shouldPaginateManually_whenTopLevelRestricted() {
        OrgEntity root = buildEntity(1L, "总公司", "ROOT", 0L, OrgStatus.ENABLED, 10);
        OrgEntity branchB = buildEntity(2L, "分公司B", "B", 1L, OrgStatus.ENABLED, 9);
        OrgEntity branchBChild = buildEntity(3L, "分公司B下级", "C", 2L, OrgStatus.ENABLED, 8);
        OrgEntity branchD = buildEntity(4L, "分公司D", "D", 1L, OrgStatus.ENABLED, 7);
        when(orgMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(branchB, branchBChild, branchD, root));
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.of(Set.of(2L, 3L, 4L)));

        PageResult<OrgVO> pageResult = orgService.getChildren(null, 1, 1);

        assertThat(pageResult.getTotal()).isEqualTo(2L);
        assertThat(pageResult.getPage()).isEqualTo(1);
        assertThat(pageResult.getPageSize()).isEqualTo(1);
        assertThat(pageResult.getRecords()).hasSize(1);
        assertThat(pageResult.getRecords().get(0).getId()).isEqualTo(2L);
    }

    /**
     * 树懒加载查询下钻某个具体节点时，若该 parentId 本身不在管辖范围内，应直接返回空列表，
     * 不发起任何数据库查询——不区分"该 id 不存在"和"存在但不在管辖范围内"，避免用查询
     * 结果反向确认某个 org id 是否存在（design.md Decision 4）。
     */
    @Test
    void getChildrenTreeNodes_shouldReturnEmptyList_whenParentIdOutOfScope() {
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.of(Set.of(5L)));

        List<OrgTreeNodeVO> nodes = orgService.getChildrenTreeNodes(10L);

        assertThat(nodes).isEmpty();
        verify(orgMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    /**
     * 创建组织时，若编码在未删除的组织中已存在，应抛出业务异常且不执行插入。
     */
    @Test
    void create_shouldThrowBusinessException_whenCodeAlreadyExists() {
        when(orgMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        OrgCreateRequest request = new OrgCreateRequest();
        request.setName("财务部");
        request.setCode("FIN");
        request.setParentId(0L);
        request.setShowOrder(0);

        assertThatThrownBy(() -> orgService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("FIN");
    }

    /**
     * 删除组织时，若存在未删除的下级组织，应拒绝删除并给出明确提示。
     */
    @Test
    void delete_shouldThrowBusinessException_whenUndeletedChildrenExist() {
        OrgEntity entity = buildEntity(1L, "总公司", "ROOT", 0L, OrgStatus.ENABLED, 0);
        when(orgMapper.selectById(1L)).thenReturn(entity);
        when(orgMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> orgService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下级组织");
    }

    /**
     * 删除组织时，若不存在下级组织，应正常执行逻辑删除。
     */
    @Test
    void delete_shouldSucceed_whenNoChildrenExist() {
        OrgEntity entity = buildEntity(1L, "总公司", "ROOT", 0L, OrgStatus.ENABLED, 0);
        when(orgMapper.selectById(1L)).thenReturn(entity);
        when(orgMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        orgService.delete(1L);

        assertThat(entity.getStatus()).isEqualTo(OrgStatus.DELETED);
        verify(operationLogRecorder, times(1))
                .recordDelete(org.mockito.ArgumentMatchers.eq("org"), org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq("总公司"), any(Map.class));
    }

    /**
     * 更新组织时，若上级组织 id 与自身 id 相同，应拒绝更新并给出明确提示，避免产生
     * 自环（parentId 指向自身）。
     */
    @Test
    void update_shouldThrowBusinessException_whenParentIdIsSelf() {
        OrgEntity entity = buildEntity(1L, "研发部", "DEV", 0L, OrgStatus.ENABLED, 0);
        when(orgMapper.selectById(1L)).thenReturn(entity);
        lenient().when(orgMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        OrgUpdateRequest request = new OrgUpdateRequest();
        request.setName("研发部");
        request.setCode("DEV");
        request.setParentId(1L);
        request.setShowOrder(0);

        assertThatThrownBy(() -> orgService.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上级组织不能是自身");
    }

    /**
     * 查询一个不存在（或已被逻辑删除）的组织时，应抛出业务异常。
     */
    @Test
    void getById_shouldThrowBusinessException_whenOrgNotFound() {
        when(orgMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> orgService.getById(99L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 构造一个测试用的组织实体。
     *
     * @param id        主键 id
     * @param name      组织名称
     * @param code      组织编码
     * @param parentId  上级组织 id
     * @param status    状态
     * @param showOrder 显示序号
     * @return 组织实体
     */
    private OrgEntity buildEntity(long id, String name, String code, long parentId, int status, int showOrder) {
        return OrgEntity.builder()
                .id(id)
                .name(name)
                .code(code)
                .parentId(parentId)
                .status(status)
                .showOrder(showOrder)
                .build();
    }
}

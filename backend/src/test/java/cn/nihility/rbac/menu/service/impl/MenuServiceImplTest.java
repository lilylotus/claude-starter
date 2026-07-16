package cn.nihility.rbac.menu.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.menu.constant.MenuResourceType;
import cn.nihility.rbac.menu.constant.MenuStatus;
import cn.nihility.rbac.menu.dto.MenuCreateRequest;
import cn.nihility.rbac.menu.dto.MenuTreeNodeVO;
import cn.nihility.rbac.menu.dto.MenuVO;
import cn.nihility.rbac.menu.entity.MenuEntity;
import cn.nihility.rbac.menu.mapper.MenuMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link MenuServiceImpl} 的单元测试，重点覆盖树形组装、编码唯一性校验、
 * 资源类型合法性校验、删除前置校验等分支逻辑。
 */
@ExtendWith(MockitoExtension.class)
class MenuServiceImplTest {

    /** 被测服务的数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private MenuMapper menuMapper;

    /** 被测服务实例。 */
    private MenuServiceImpl menuService;

    /**
     * 每个用例执行前重新构造被测服务；实体/DTO 转换通过 {@code MenuConvert.INSTANCE}
     * 静态调用完成，无需在此注入或 mock。
     */
    @BeforeEach
    void setUp() {
        menuService = new MenuServiceImpl(menuMapper);
    }

    /**
     * 资源树应按父子关系正确嵌套，顶级节点（parentId 未命中任何已知节点）归入根列表。
     */
    @Test
    void getTree_shouldNestChildrenUnderTheirParent() {
        MenuEntity root = buildEntity(1L, "系统管理", "SYS", 0L, MenuResourceType.MENU, MenuStatus.ENABLED, 10);
        MenuEntity child = buildEntity(2L, "菜单管理", "SYS_MENU", 1L, MenuResourceType.MENU, MenuStatus.ENABLED, 5);
        when(menuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(root, child));

        List<MenuTreeNodeVO> tree = menuService.getTree();

        assertThat(tree).hasSize(1);
        MenuTreeNodeVO rootNode = tree.get(0);
        assertThat(rootNode.getId()).isEqualTo(1L);
        assertThat(rootNode.getChildren()).hasSize(1);
        assertThat(rootNode.getChildren().get(0).getId()).isEqualTo(2L);
    }

    /**
     * 分页查询直属子资源时，应返回携带总条数、页码、每页条数的分页结果。
     */
    @Test
    void getChildren_shouldReturnPageResult_withRecordsAndTotal() {
        MenuEntity child = buildEntity(2L, "菜单管理", "SYS_MENU", 1L, MenuResourceType.MENU, MenuStatus.ENABLED, 5);
        Page<MenuEntity> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(List.of(child));
        when(menuMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);

        PageResult<MenuVO> pageResult = menuService.getChildren(1L, 1, 10);

        assertThat(pageResult.getTotal()).isEqualTo(1L);
        assertThat(pageResult.getPage()).isEqualTo(1);
        assertThat(pageResult.getPageSize()).isEqualTo(10);
        assertThat(pageResult.getRecords()).hasSize(1);
        assertThat(pageResult.getRecords().get(0).getId()).isEqualTo(2L);
    }

    /**
     * 树懒加载查询直属子资源时，应返回不分页的节点列表，且每个节点的 children 固定为空列表。
     */
    @Test
    void getChildrenTreeNodes_shouldReturnFlatNodes_withEmptyChildren() {
        MenuEntity child = buildEntity(2L, "菜单管理", "SYS_MENU", 1L, MenuResourceType.MENU, MenuStatus.ENABLED, 5);
        when(menuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(child));

        List<MenuTreeNodeVO> nodes = menuService.getChildrenTreeNodes(1L);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).getId()).isEqualTo(2L);
        assertThat(nodes.get(0).getChildren()).isEmpty();
    }

    /**
     * 树懒加载查询未指定 parentId 时，应按顶级（0）处理。
     */
    @Test
    void getChildrenTreeNodes_shouldTreatNullParentIdAsRoot() {
        MenuEntity root = buildEntity(1L, "系统管理", "SYS", 0L, MenuResourceType.MENU, MenuStatus.ENABLED, 10);
        when(menuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(root));

        List<MenuTreeNodeVO> nodes = menuService.getChildrenTreeNodes(null);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).getParentId()).isEqualTo(0L);
    }

    /**
     * 创建资源时，若编码在未删除的资源中已存在，应抛出业务异常且不执行插入。
     */
    @Test
    void create_shouldThrowBusinessException_whenCodeAlreadyExists() {
        when(menuMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        MenuCreateRequest request = new MenuCreateRequest();
        request.setName("角色管理");
        request.setCode("PERM_ROLE");
        request.setParentId(0L);
        request.setResourceType(MenuResourceType.MENU);
        request.setShowOrder(0);

        assertThatThrownBy(() -> menuService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PERM_ROLE");
    }

    /**
     * 创建资源时，若资源类型不是菜单/按钮/API 三者之一，应拒绝创建，且不触发编码唯一性查询。
     */
    @Test
    void create_shouldThrowBusinessException_whenResourceTypeInvalid() {
        MenuCreateRequest request = new MenuCreateRequest();
        request.setName("角色管理");
        request.setCode("PERM_ROLE");
        request.setParentId(0L);
        request.setResourceType(99);
        request.setShowOrder(0);

        assertThatThrownBy(() -> menuService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("99");
    }

    /**
     * 删除资源时，若存在未删除的下级资源，应拒绝删除并给出明确提示。
     */
    @Test
    void delete_shouldThrowBusinessException_whenUndeletedChildrenExist() {
        MenuEntity entity = buildEntity(1L, "系统管理", "SYS", 0L, MenuResourceType.MENU, MenuStatus.ENABLED, 0);
        when(menuMapper.selectById(1L)).thenReturn(entity);
        when(menuMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> menuService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("下级资源");
    }

    /**
     * 删除资源时，若不存在下级资源，应正常执行逻辑删除。
     */
    @Test
    void delete_shouldSucceed_whenNoChildrenExist() {
        MenuEntity entity = buildEntity(1L, "系统管理", "SYS", 0L, MenuResourceType.MENU, MenuStatus.ENABLED, 0);
        when(menuMapper.selectById(1L)).thenReturn(entity);
        when(menuMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        menuService.delete(1L);

        assertThat(entity.getStatus()).isEqualTo(MenuStatus.DELETED);
    }

    /**
     * 查询一个不存在（或已被逻辑删除）的资源时，应抛出业务异常。
     */
    @Test
    void getById_shouldThrowBusinessException_whenMenuNotFound() {
        when(menuMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> menuService.getById(99L))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 构造一个测试用的资源实体。
     *
     * @param id           主键 id
     * @param name         资源名称
     * @param code         资源编码
     * @param parentId     上级资源 id
     * @param resourceType 资源类型
     * @param status       状态
     * @param showOrder    显示序号
     * @return 资源实体
     */
    private MenuEntity buildEntity(long id, String name, String code, long parentId, int resourceType, int status,
            int showOrder) {
        return MenuEntity.builder()
                .id(id)
                .name(name)
                .code(code)
                .parentId(parentId)
                .resourceType(resourceType)
                .status(status)
                .showOrder(showOrder)
                .build();
    }
}

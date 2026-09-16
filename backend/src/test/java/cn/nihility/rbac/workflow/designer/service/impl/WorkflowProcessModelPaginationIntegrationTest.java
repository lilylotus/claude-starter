package cn.nihility.rbac.workflow.designer.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.designer.dto.ProcessModelVO;
import cn.nihility.rbac.workflow.designer.service.WorkflowProcessModelService;
import cn.nihility.rbac.workflow.entity.ProcessModelEntity;
import cn.nihility.rbac.workflow.mapper.ProcessModelMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 使用真实分页拦截器验证数据库限量、跨页排序与末页，测试数据在事务结束时回滚。 */
@SpringBootTest
@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
class WorkflowProcessModelPaginationIntegrationTest {

    /** 真实流程模型查询服务。 */
    @Autowired
    private WorkflowProcessModelService service;

    /** 用于构造相同更新时间的数据。 */
    @Autowired
    private ProcessModelMapper modelMapper;

    /** 二十五条同时间记录跨越三页，按主键倒序且不重复或遗漏。 */
    @Test
    void shouldPageInDatabaseWithStableOrderAndAccurateTotals() {
        long baselineTotal = modelMapper.selectCount(null);
        String prefix = "PAGE_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime updateTime = LocalDateTime.of(2100, 1, 1, 0, 0);
        List<Long> expectedIds = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            ProcessModelEntity model = ProcessModelEntity.builder()
                    .processCode(prefix + "_" + index).processName("分页测试 " + index)
                    .status(ProcessModelStatus.DRAFT)
                    .createBy("pagination-test").updateBy("pagination-test")
                    .createTime(updateTime).updateTime(updateTime).build();
            modelMapper.insert(model);
            expectedIds.add(model.getId());
        }
        expectedIds.sort(Comparator.reverseOrder());

        List<Long> actualIds = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            var result = service.pageModels(page, 10);
            assertThat(result.getTotal()).isEqualTo(baselineTotal + 25);
            assertThat(result.getPage()).isEqualTo(page);
            assertThat(result.getPageSize()).isEqualTo(10);
            assertThat(result.getRecords()).hasSize((int) Math.min(10, baselineTotal + 25 - (page - 1) * 10));
            actualIds.addAll(result.getRecords().stream().map(ProcessModelVO::getId).toList());
        }
        assertThat(actualIds.subList(0, 25)).containsExactlyElementsOf(expectedIds);

        int lastPage = (int) ((baselineTotal + 34) / 10);
        var last = service.pageModels(lastPage, 10);
        assertThat(last.getRecords()).hasSize((int) (baselineTotal + 25 - (lastPage - 1) * 10));
        var beyondLast = service.pageModels(lastPage + 1, 10);
        assertThat(beyondLast.getRecords()).isEmpty();
        assertThat(beyondLast.getTotal()).isEqualTo(baselineTotal + 25);
    }
}

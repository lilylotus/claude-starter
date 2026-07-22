package cn.nihility.rbac.org.mapper;

import cn.nihility.rbac.org.entity.OrgEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 组织机构 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，
 * 不在此处编写 SQL；{@link #countByColumnValue} 是唯一的例外，SQL 写在
 * {@code resources/mybatis/mapper/OrgMapper.xml} 里。
 */
@Mapper
public interface OrgMapper extends BaseMapper<OrgEntity> {

    /**
     * 统计未被逻辑删除的组织中，指定列等于给定值的记录数，供"表单字段定义"驱动的
     * 非锁定字段唯一性校验使用。{@code column} 只接受调用方从
     * {@code tab_metadata_field} 目录解析得到、并经白名单校验的合法列名，不接受
     * 任意字符串拼接，避免 SQL 注入（design.md Decision 8）。
     *
     * @param column    目标列名，仅接受白名单内的合法列名
     * @param value     待比对的值
     * @param excludeId 需要排除的自身 id，创建场景传 {@code null}
     * @return 命中的记录数
     */
    int countByColumnValue(@Param("column") String column, @Param("value") String value,
            @Param("excludeId") Long excludeId);
}

package cn.nihility.rbac.identity.upstream.support;

import cn.nihility.rbac.common.exception.BusinessException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 数据库表方式取数组件（design.md Decision 3）：{@link DriverManager#getConnection} 用
 * 解密后的用户名/密码连接，执行数据域配置的只读 SQL，按 {@link ResultSetMetaData} 的列名
 * 逐行转换成 {@code Map}，用完即关闭连接，不做连接池（同步任务是分钟级低频操作，没有必要
 * 为此引入连接池管理的复杂度）。{@code mysql-connector-j} 已是项目既有依赖。
 */
@Component
public class UpstreamJdbcFetcher {

    /**
     * 执行只读查询并拉取上游数据库返回的原始数据。
     *
     * @param jdbcUrl  JDBC 连接地址（{@code jdbc:mysql://} 前缀）
     * @param username 连接用户名
     * @param password 连接密码（已解密的明文）
     * @param sql      数据域配置的只读查询 SQL
     * @return 原始行列表，每行 key 为查询结果的列别名（即上游字段编码）
     */
    public List<Map<String, Object>> fetch(String jdbcUrl, String username, String password, String sql) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnLabel(i), resultSet.getObject(i));
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            throw new BusinessException("数据库查询失败：" + e.getMessage());
        }
        return rows;
    }
}

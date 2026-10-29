package com.meetingroom.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapper XML 装载自检。
 *
 * <p><b>为什么需要这组用例：</b>本机没有 MySQL，无法起真实应用跑接口。
 * 但 Mapper XML 里最容易出错的地方（SQL 语法、{@code #{}} 占位符拼错、
 * {@code resultType} 全限定名写错、{@code <if test>} 表达式引用不存在的属性）
 * <b>不需要连库就能发现</b>——MyBatis 在解析阶段就会报错。</p>
 *
 * <p>这组用例替代不了真机联调，但能把"一启动就报 MyBatis 异常"这类
 * 低级错误挡在提交之前。真机联调仍需在有 MySQL 的环境跑一遍。</p>
 */
class MapperXmlParsingTest {

    /** 逐个装载 resources/mapper 下的 XML，任一解析失败即抛异常 */
    private Configuration loadAll() throws Exception {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.getTypeAliasRegistry().registerAliases("com.meetingroom.entity");

        String[] mappers = {
                "mapper/UserMapper.xml",
                "mapper/MeetingRoomMapper.xml",
                "mapper/ReservationMapper.xml",
                "mapper/OperationLogMapper.xml"
        };
        for (String path : mappers) {
            try (InputStream in = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(path)) {
                assertNotNull(in, "找不到 Mapper XML：" + path);
                new XMLMapperBuilder(in, configuration, path, configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }

    @Test
    @DisplayName("4 个 Mapper XML 全部可被 MyBatis 装载（语法／resultType／占位符无误）")
    void 全部XML可装载() throws Exception {
        Configuration configuration = loadAll();

        // 第 10 周新增的语句逐条确认存在
        assertTrue(configuration.hasStatement(
                "com.meetingroom.mapper.ReservationMapper.selectAdminList"));
        assertTrue(configuration.hasStatement(
                "com.meetingroom.mapper.ReservationMapper.countAdminList"));
        assertTrue(configuration.hasStatement(
                "com.meetingroom.mapper.ReservationMapper.updateStatusToCancelled"));
        assertTrue(configuration.hasStatement(
                "com.meetingroom.mapper.ReservationMapper.countByRoomId"));
        assertTrue(configuration.hasStatement(
                "com.meetingroom.mapper.MeetingRoomMapper.selectByName"));
        assertTrue(configuration.hasStatement(
                "com.meetingroom.mapper.MeetingRoomMapper.deleteById"));
        assertTrue(configuration.hasStatement(
                "com.meetingroom.mapper.UserMapper.countActiveAdmins"));
        assertTrue(configuration.hasStatement(
                "com.meetingroom.mapper.OperationLogMapper.selectList"));
    }

    @Test
    @DisplayName("管理端列表 SQL 无筛选时不含 where 条件，有筛选时正确拼入")
    void 动态条件拼接() throws Exception {
        Configuration configuration = loadAll();
        MappedStatement ms = configuration.getMappedStatement(
                "com.meetingroom.mapper.ReservationMapper.selectAdminList");

        // 无条件：不应出现 room_id 过滤
        com.meetingroom.dto.AdminBookingQuery empty = new com.meetingroom.dto.AdminBookingQuery();
        BoundSql emptySql = ms.getBoundSql(empty);
        String emptyText = normalize(emptySql.getSql());
        assertTrue(!emptyText.contains("r.room_id ="), "无筛选时不该加房间条件，实际：" + emptyText);

        // 有房间条件：必须拼进 room_id
        com.meetingroom.dto.AdminBookingQuery withRoom = new com.meetingroom.dto.AdminBookingQuery();
        withRoom.setRoomId(1L);
        String roomText = normalize(ms.getBoundSql(withRoom).getSql());
        assertTrue(roomText.contains("r.room_id = ?"), "应拼入房间条件，实际：" + roomText);

        // 日期区间：两个占位符都要绑上
        // ★ 这里刻意不设分页：未设分页时 LIMIT 整段不拼，占位符数量恰好等于
        //   "拼进去的条件数"（=2），可以精确断言。若带上分页，pageSize／offset
        //   两个占位符也会进来，数量就不再是"条件数"，断言会变成近似值。
        com.meetingroom.dto.AdminBookingQuery withDate = new com.meetingroom.dto.AdminBookingQuery();
        withDate.setStartDate(java.time.LocalDate.of(2026, 11, 1));
        withDate.setEndDate(java.time.LocalDate.of(2026, 11, 3));
        BoundSql dateSql = ms.getBoundSql(withDate);
        String dateText = normalize(dateSql.getSql());
        assertTrue(dateText.contains("r.reserve_date >= ?"), "应拼入开始日期，实际：" + dateText);
        assertTrue(dateText.contains("r.reserve_date <= ?"), "应拼入结束日期，实际：" + dateText);
        assertTrue(dateSql.getParameterMappings().size() == 2,
                "日期区间只有 2 个占位符，实际 " + dateSql.getParameterMappings().size()
                        + "，SQL：" + dateText);
    }

    @Test
    @DisplayName("日志查询的 endDate 用 < 次日，不用 <=（否则当天日志全漏）")
    void 日志日期边界正确() throws Exception {
        Configuration configuration = loadAll();
        MappedStatement ms = configuration.getMappedStatement(
                "com.meetingroom.mapper.OperationLogMapper.selectList");

        com.meetingroom.dto.AdminLogQuery query = new com.meetingroom.dto.AdminLogQuery();
        query.setEndDate(java.time.LocalDate.of(2026, 11, 4));
        String sql = normalize(ms.getBoundSql(query).getSql());

        assertTrue(sql.contains("INTERVAL 1 DAY"), "endDate 应转为 < 次日，实际：" + sql);
        assertTrue(sql.contains("l.operated_at <"), "应为严格小于，实际：" + sql);
    }

    @Test
    @DisplayName("分页：传了 pageSize 才在最外层拼 LIMIT，没传不拼（否则查出空结果）")
    void 分页条件() throws Exception {
        Configuration configuration = loadAll();
        MappedStatement ms = configuration.getMappedStatement(
                "com.meetingroom.mapper.ReservationMapper.selectAdminList");

        // ★ 断言必须看"最外层有没有 LIMIT"而不是"整段 SQL 里有没有 LIMIT"：
        //   adminColumns 里有个取最后一条强制取消日志的标量子查询，那段自带
        //   LIMIT 1（限制子查询返回行数，与分页无关）。直接搜 contains("LIMIT")
        //   会被子查询里的那个命中，用例就永远为真、什么也测不出来。
        //
        // ★ Service 在调 Mapper 前一定会把 pageNum／pageSize 补成默认值（见
        //   AdminBookingService.list），但 XML 也必须能处理"没设分页"的直接调用，
        //   否则会拼出 LIMIT null OFFSET null 让 MySQL 报语法错。
        com.meetingroom.dto.AdminBookingQuery noPage = new com.meetingroom.dto.AdminBookingQuery();
        noPage.setPageNum(null);
        noPage.setPageSize(null);
        String noPageText = normalize(ms.getBoundSql(noPage).getSql());
        assertTrue(!outermostLimit(noPageText),
                "未设分页不该在最外层拼 LIMIT，实际：" + noPageText);

        com.meetingroom.dto.AdminBookingQuery paged = new com.meetingroom.dto.AdminBookingQuery();
        paged.setPageNum(2);
        paged.setPageSize(20);
        String pagedText = normalize(ms.getBoundSql(paged).getSql());
        assertTrue(pagedText.contains("LIMIT ? OFFSET ?"),
                "设了分页应在最外层拼 LIMIT ? OFFSET ?，实际：" + pagedText);
        assertTrue(outermostLimit(pagedText), "分页时最外层应有 LIMIT，实际：" + pagedText);
        // LIMIT 必须在 ORDER BY 之后（拼错位置 MySQL 直接报语法错）
        assertTrue(pagedText.lastIndexOf("ORDER BY") < pagedText.lastIndexOf("LIMIT ?"),
                "LIMIT 应在 ORDER BY 之后，实际：" + pagedText);
        // offset 计算正确：(2-1) * 20 = 20
        assertTrue(paged.getOffset() == 20, "offset 应为 20，实际：" + paged.getOffset());

        // 第 1 页的 offset 是 0，不是 pageNum（常见手误是直接拿 pageNum 当 offset）
        paged.setPageNum(1);
        assertTrue(paged.getOffset() == 0, "第 1 页 offset 应为 0，实际：" + paged.getOffset());
    }

    /**
     * 判断 SQL 最外层（非括号内）是否带 LIMIT。
     *
     * <p>逐字符扫一遍记括号深度，只在深度 0 处找 LIMIT；
     * 子查询里的 LIMIT 1 都在括号内，会被跳过。</p>
     */
    private boolean outermostLimit(String sql) {
        int depth = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0 && Character.toUpperCase(c) == 'L'
                    && sql.regionMatches(true, i, "LIMIT", 0, 5)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("强制取消的 SQL 带 status = 'PENDING'（原子判定，防重复取消）")
    void 强制取消带状态条件() throws Exception {
        Configuration configuration = loadAll();
        MappedStatement ms = configuration.getMappedStatement(
                "com.meetingroom.mapper.ReservationMapper.updateStatusToCancelled");
        String sql = normalize(ms.getBoundSql(Map.of(
                "id", 1L, "cancelReason", "测试原因", "now", java.time.LocalDateTime.now()
        )).getSql());

        assertTrue(sql.contains("status = 'PENDING'"),
                "必须带状态条件，否则并发下两人都能取消成功，实际：" + sql);
        assertTrue(sql.toUpperCase().contains("UPDATE RESERVATION"));
    }

    @Test
    @DisplayName("每个 Mapper 接口方法与 XML 中的语句一一对应（无漏写、无拼错）")
    void 接口与XML一一对应() throws Exception {
        Configuration configuration = loadAll();
        Class<?>[] mappers = {
                UserMapper.class, MeetingRoomMapper.class,
                ReservationMapper.class, OperationLogMapper.class
        };
        Map<String, String> missing = new HashMap<>();
        for (Class<?> mapper : mappers) {
            for (Method method : mapper.getDeclaredMethods()) {
                String id = mapper.getName() + "." + method.getName();
                if (!configuration.hasStatement(id)) {
                    missing.put(id, "XML 中找不到对应语句");
                }
            }
        }
        assertTrue(missing.isEmpty(), "以下接口方法在 XML 中缺失：" + missing);
    }

    /** 把换行与多余空格压掉，便于断言 */
    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}

package com.meetingroom.mapper;

import com.meetingroom.dto.AdminBookingQuery;
import com.meetingroom.dto.OccupiedSlotDTO;
import com.meetingroom.dto.ReservationDetailDTO;
import com.meetingroom.entity.Reservation;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 预约记录表（D3 reservation）的数据访问。SQL 写在
 * {@code resources/mapper/ReservationMapper.xml}。
 */
public interface ReservationMapper {

    /** 新增一条预约，回填自增主键到 {@link Reservation#getId()} */
    int insert(Reservation reservation);

    /**
     * 查询某会议室某天的"已占用时段"。
     *
     * <p>★ 这份查询是**冲突判定与 409 响应载荷的唯一数据来源**（第 9 周推进计划
     * §3.1「冲突判定只写一遍」）：判定在 Java 侧用 {@code BookingTimeRules.conflicts}
     * 逐条比对，409 时把同一批结果原样回给前端。</p>
     */
    List<OccupiedSlotDTO> selectOccupiedSlotsOfRoom(@Param("roomId") Long roomId,
                                                    @Param("date") LocalDate date);

    /**
     * 取某天已用的最大预约编号（加行/间隙锁，供编号生成用）。
     *
     * @param prefix 形如 {@code MR20261027}
     * @return 最大编号；当天还没有预约时返回 null
     */
    String selectMaxReservationNo(@Param("prefix") String prefix);

    // -------------------------------------------------------------------------
    // 管理端（第 10 周，李知渔）。接口 #9 全部预约、#10 强制取消。
    // ★ 以下查询只读，不改任何数据；写操作只有 updateStatusToCancelled 一条。
    // -------------------------------------------------------------------------

    /**
     * 管理员视角的全部预约（接口 #9，FR-15／FR-19 简化版）。
     *
     * <p>带出 {@code userName}／{@code roomName} 两个联表字段，让管理端页面不必
     * 再逐条回查用户表和会议室表（N+1 查询）。</p>
     *
     * <p>筛选条件全部可空：传哪个就按哪个过滤，全不传则返回全部。</p>
     */
    List<ReservationDetailDTO> selectAdminList(AdminBookingQuery query);

    /** 同上但只取总数（分页用）。★ 与 {@link #selectAdminList} 共用同一段 where，避免两处口径漂移 */
    long countAdminList(AdminBookingQuery query);

    /** 按主键查一条预约的完整信息（含联表名称），用于强制取消后回带更新结果 */
    ReservationDetailDTO selectDetailById(@Param("id") Long id);

    /**
     * 把预约置为已取消并写入取消原因（FR-16 强制取消）。
     *
     * <p>★ where 里带 {@code status = 'PENDING'} 是有意的：它让"已取消的预约不能重复取消"
     * 变成一次原子判定——两个管理员同时点强制取消，只有一个能更新到行，
     * 另一个影响行数为 0，由 Service 判为业务错误。若先查后改，这个窗口会被漏掉。</p>
     *
     * @return 受影响行数（0 表示该预约不存在或已不是 PENDING）
     */
    int updateStatusToCancelled(@Param("id") Long id,
                                @Param("cancelReason") String cancelReason,
                                @Param("now") LocalDateTime now);

    /**
     * 统计某会议室"未取消"的预约条数（FR-03 删除前置校验）。
     *
     * <p>★ 这里刻意不只数"未来"的预约：{@code FK ... ON DELETE RESTRICT} 拦的是
     * 全部引用行，只要还有一条历史预约挂着，DELETE 就会失败。前置校验若只数未完成的，
     * 会放行一个注定 409 的请求，让前端拿到数据库外键异常。</p>
     */
    long countByRoomId(@Param("roomId") Long roomId);
}

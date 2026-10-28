package com.meetingroom.mapper;

import com.meetingroom.dto.BookingItemVO;
import com.meetingroom.dto.OccupiedSlotDTO;
import com.meetingroom.entity.Reservation;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 预约记录表（D3 reservation）的数据访问。SQL 写在
 * {@code resources/mapper/ReservationMapper.xml}。
 *
 * <p>第 10 周（虞业宸）扩展：我的预约／修改／取消三条接口的数据访问，
 * 以及编号方案改造后的"占位插入 → 正式编号回填"。</p>
 */
public interface ReservationMapper {

    /** 新增一条预约，回填自增主键到 {@link Reservation#getId()} */
    int insert(Reservation reservation);

    /**
     * 插入成功后，把由（日期，主键）派生的正式编号回填（编号方案第 10 周改造）。
     */
    int updateReservationNo(@Param("id") Long id, @Param("reservationNo") String reservationNo);

    /**
     * 查询某会议室某天的"已占用时段"。
     *
     * <p>★ 这份查询是**冲突判定与 409 响应载荷的唯一数据来源**（第 9 周推进计划
     * §3.1「冲突判定只写一遍」）：判定在 Java 侧用 {@code BookingTimeRules.conflicts}
     * 逐条比对，409 时把同一批结果原样回给前端。</p>
     *
     * @param excludeId 修改场景排除自身（排除自己占用的时段），新建场景传 {@code null}
     */
    List<OccupiedSlotDTO> selectOccupiedSlotsOfRoom(@Param("roomId") Long roomId,
                                                    @Param("date") LocalDate date,
                                                    @Param("excludeId") Long excludeId);

    /**
     * 按主键查预约并加行锁（修改／取消场景，必须在事务内调用）。
     *
     * <p>★ 锁自己的那一行：防止"读状态 → 判 30 分钟 → 写"之间被另一个并发请求
     * （比如另一端点了一次取消）插进来，造成状态判断失真。</p>
     */
    Reservation selectByIdForUpdate(@Param("id") Long id);

    /**
     * 我的预约列表（含会议室名称与位置），按开始时间倒序。
     */
    List<BookingItemVO> selectMine(@Param("userId") Long userId);

    /**
     * 修改预约的业务字段（编号、状态、取消原因不动）。
     */
    int updateBookingFields(Reservation reservation);

    /**
     * 更新状态与取消原因（取消场景写 CANCELLED；读取校正写 FINISHED）。
     *
     * @param cancelReason 取消原因，可为 {@code null}
     */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("cancelReason") String cancelReason);

    /**
     * 落库时校正（NF-08"状态流转准确"，不引入定时器）：
     * 把已结束但仍存为 PENDING 的预约批量校正为 FINISHED。
     * 幂等，任何一次"我的预约"读取都会顺手执行。
     */
    int correctOverduePending();
}

package com.meetingroom.mapper;

import com.meetingroom.dto.OccupiedSlotDTO;
import com.meetingroom.entity.Reservation;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
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
}

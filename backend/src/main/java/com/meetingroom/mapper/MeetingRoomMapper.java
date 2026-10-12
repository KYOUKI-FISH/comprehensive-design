package com.meetingroom.mapper;

import com.meetingroom.dto.OccupiedSlotDTO;
import com.meetingroom.entity.MeetingRoom;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 会议室表（D2）与预约表（D3）的数据访问。SQL 写在
 * {@code resources/mapper/MeetingRoomMapper.xml}。
 */
public interface MeetingRoomMapper {

    /**
     * 按条件查询会议室（DFD 加工 P2 查询会议室）。
     *
     * @param location    位置关键字，为空表示不限
     * @param minCapacity 最小容纳人数，为空表示不限
     * @param status      启用状态（ENABLED／DISABLED），为空表示不限
     */
    List<MeetingRoom> selectByCondition(@Param("location") String location,
                                        @Param("minCapacity") Integer minCapacity,
                                        @Param("status") String status);

    /**
     * 查询一批会议室在某天的已占用时段（只读，供查询页展示）。
     *
     * @param roomIds 会议室ID列表，调用方需保证非空
     * @param date    日期
     */
    List<OccupiedSlotDTO> selectOccupiedSlots(@Param("roomIds") List<Long> roomIds,
                                              @Param("date") LocalDate date);
}

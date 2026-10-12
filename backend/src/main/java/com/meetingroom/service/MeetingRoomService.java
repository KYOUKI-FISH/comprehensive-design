package com.meetingroom.service;

import com.meetingroom.dto.OccupiedSlotDTO;
import com.meetingroom.dto.RoomVO;
import com.meetingroom.entity.MeetingRoom;
import com.meetingroom.mapper.MeetingRoomMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会议室查询服务（DFD 加工 P2 查询会议室 → 功能需求 FR-05、FR-06）。
 *
 * <p>★ 边界说明：本类只做"查出来给人看"，属环境验证通路。
 * 提交预约时的冲突检测（FR-09）不在此处实现，按计划放在第 9～10 周，
 * 与数据库事务／行锁一起做，避免本周越过"不写业务代码"的红线。</p>
 */
@Service
public class MeetingRoomService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final MeetingRoomMapper meetingRoomMapper;

    public MeetingRoomService(MeetingRoomMapper meetingRoomMapper) {
        this.meetingRoomMapper = meetingRoomMapper;
    }

    /**
     * 按条件查询会议室；传入 {@code date} 时附带该会议室的当日占用时段。
     *
     * @param location    位置关键字（模糊匹配位置或名称），可为 null
     * @param minCapacity 最小容纳人数，可为 null
     * @param status      启用状态，可为 null。查询页建议传 ENABLED，
     *                    这样停用的会议室不会出现在前台（FR-06／问题 P03）
     * @param date        需要展示占用时段的日期，可为 null
     */
    public List<RoomVO> list(String location, Integer minCapacity, String status, LocalDate date) {
        List<MeetingRoom> rooms = meetingRoomMapper.selectByCondition(location, minCapacity, status);
        if (rooms.isEmpty()) {
            return new ArrayList<>();
        }

        List<RoomVO> result = rooms.stream().map(RoomVO::from).collect(Collectors.toList());

        if (date == null) {
            return result;
        }

        List<Long> roomIds = rooms.stream().map(MeetingRoom::getId).collect(Collectors.toList());
        Map<Long, List<String>> slotMap = groupSlotsByRoom(meetingRoomMapper.selectOccupiedSlots(roomIds, date));
        result.forEach(vo -> vo.setOccupiedSlots(slotMap.getOrDefault(vo.getId(), Collections.emptyList())));
        return result;
    }

    /** 把同一会议室的占用行聚成 {@code ["09:00-10:00", ...]} 形式，按开始时间升序 */
    private Map<Long, List<String>> groupSlotsByRoom(List<OccupiedSlotDTO> slots) {
        Map<Long, List<String>> map = new LinkedHashMap<>();
        for (OccupiedSlotDTO slot : slots) {
            String text = slot.getStartTime().format(TIME_FORMAT) + "-" + slot.getEndTime().format(TIME_FORMAT);
            map.computeIfAbsent(slot.getRoomId(), key -> new ArrayList<>()).add(text);
        }
        return map;
    }
}

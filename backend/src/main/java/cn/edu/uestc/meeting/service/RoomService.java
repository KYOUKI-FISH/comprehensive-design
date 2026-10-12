package cn.edu.uestc.meeting.service;

import cn.edu.uestc.meeting.entity.MeetingRoom;
import cn.edu.uestc.meeting.mapper.MeetingRoomMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会议室查询的骨架实现 —— 对应 DFD 加工 <b>P2 查询会议室</b>、
 * 功能需求 <b>FR-05</b>（按条件查询）、<b>FR-06</b>（只显示启用中的会议室）。
 *
 * <p><b>本周边界</b>：只返回会议室基础信息。DFD 里 P2 的输出数据流写作
 * "会议室列表（含当日可用时段）"，其中"当日可用时段"需要读 D3 预约记录并与
 * 半开区间规则配合计算，属业务实现（第 9～10 周），本周不做——
 * D3 表已建好并灌了种子数据，但没有查询代码，这是刻意的。
 */
@Service
public class RoomService {

    private final MeetingRoomMapper meetingRoomMapper;

    public RoomService(MeetingRoomMapper meetingRoomMapper) {
        this.meetingRoomMapper = meetingRoomMapper;
    }

    /**
     * 查询启用中的会议室列表。
     *
     * @param keyword     名称/位置模糊关键字，可为空
     * @param minCapacity 最小容纳人数，可为 null
     */
    public List<MeetingRoom> listEnabled(String keyword, Integer minCapacity) {
        return meetingRoomMapper.selectEnabled(keyword, minCapacity);
    }
}

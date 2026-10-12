package cn.edu.uestc.meeting.mapper;

import cn.edu.uestc.meeting.entity.MeetingRoom;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * D2 会议室信息的数据访问接口（SQL 写在 resources/mapper/MeetingRoomMapper.xml）。
 *
 * <p>骨架阶段只实现"查询启用中的会议室"——查询通路唯一需要的方法。
 * 管理会议室（FR-03／FR-04）的增删改属第 9～10 周。
 */
public interface MeetingRoomMapper {

    /**
     * 查询<b>启用状态</b>的会议室列表（FR-06：停用会议室不出现在前台查询结果中）。
     *
     * @param keyword     名称/位置模糊关键字，可为 {@code null}
     * @param minCapacity 最小容纳人数，可为 {@code null}
     * @return 会议室列表，按容纳人数升序
     */
    List<MeetingRoom> selectEnabled(@Param("keyword") String keyword,
                                    @Param("minCapacity") Integer minCapacity);

    /** 统计记录总数——仅用于 /api/health 连通性自检，不做业务用途。 */
    int countAll();
}

package com.meetingroom.mapper;

import com.meetingroom.dto.OccupiedSlotDTO;
import com.meetingroom.entity.MeetingRoom;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 会议室表（D2）与预约表（D3）的数据访问。SQL 写在
 * {@code resources/mapper/MeetingRoomMapper.xml}。
 *
 * <p>★ 合并说明（第 9 周融合）：本接口由两条并行开发线合并而成 ——
 * 预约模块（虞业宸）需要 {@link #selectByIdForUpdate}，
 * 详情页与占用时段接口（李知渔）需要 {@link #selectById} 与
 * {@link #selectRoomOccupiedSlots}。两者互不冲突，故取并集。</p>
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

    /**
     * 按ID查会议室（第 9 周详情页用；也用于确认会议室存在）。
     *
     * @param id 会议室ID
     * @return 会议室；不存在时返回 {@code null}
     */
    MeetingRoom selectById(@Param("id") Long id);

    /**
     * 查某会议室某天的占用时段（<b>只读</b>版，供详情页展示）。
     *
     * <p>★ 口径与 {@link #selectOccupiedSlots} 保持一致：只有 PENDING／ONGOING 算占用，
     * CANCELLED 已释放时段、FINISHED 的时段已经过去。两个接口给同一个页面供数，
     * 口径不一致会让"列表页说有占用、详情页说没有"。</p>
     *
     * <p>★ 这是普通 SELECT（快照读），<b>不能用于冲突检测</b>——
     * 冲突检测走 {@code ReservationMapper.selectOccupiedSlotsOfRoom}（锁定读）。</p>
     *
     * @param roomId 会议室ID
     * @param date   日期
     * @return 占用行
     */
    List<OccupiedSlotDTO> selectRoomOccupiedSlots(@Param("roomId") Long roomId,
                                                  @Param("date") LocalDate date);

    /**
     * 按ID查会议室并**加行锁**（{@code SELECT ... FOR UPDATE}），供提交预约时使用。
     *
     * <p>★ 为什么提交预约要先锁会议室行：两个用户同时订同一间会议室，若都先查冲突
     * 再插入，双方都会查到"没有冲突"从而同时插入（幻读）。锁住这间会议室的行之后，
     * 同一间会议室的提交被串行化，第二个人一定能看到第一个人刚插入的预约（NF-07 并发不超订）。</p>
     *
     * <p>★ 必须在事务内调用（{@code BookingService.create} 标了 {@code @Transactional}），
     * 否则 {@code FOR UPDATE} 立即释放，等于没锁。</p>
     *
     * @return 会议室；不存在时返回 null
     */
    MeetingRoom selectByIdForUpdate(@Param("id") Long id);

    // -------------------------------------------------------------------------
    // 管理端会议室管理（第 10 周，李知渔）。接口 #11。
    // -------------------------------------------------------------------------

    /**
     * 按名称查会议室（FR-03 同名拒绝前置校验）。
     *
     * <p>★ 前置校验只是给用户一句能看懂的提示；真正的唯一性由 {@code uk_room_name} 兜底。
     * 两个管理员同时建同名会议室时，前置校验都会通过，最终靠数据库唯一键挡住一个。</p>
     */
    MeetingRoom selectByName(@Param("name") String name);

    /** 新增会议室，回填自增主键 */
    int insert(MeetingRoom room);

    /**
     * 更新会议室（接口 #11 PUT）。
     *
     * <p>★ 只更新非 null 字段：管理员改容量时不该把设备描述清空。
     * 启用／停用也走这条（传 status），不单开接口——需求清单里停用不属独立功能。</p>
     */
    int updateSelective(MeetingRoom room);

    /**
     * 删除会议室（接口 #11 DELETE）。
     *
     * <p>★ 有预约挂着的会议室外键会拒绝删除（{@code ON DELETE RESTRICT}），
     * Service 先在事务里用 {@code ReservationMapper.countByRoomId} 前置判一次，
     * 好给用户中文提示；数据库外键仍是最后一道闸。</p>
     */
    int deleteById(@Param("id") Long id);
}

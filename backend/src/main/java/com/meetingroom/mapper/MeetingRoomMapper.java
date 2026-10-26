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

    /**
     * 按ID查会议室（第 9 周详情页用）。
     *
     * @param id 会议室ID
     * @return 会议室；不存在时返回 {@code null}
     */
    MeetingRoom selectById(@Param("id") Long id);

    /**
     * 查某会议室某天的占用时段（<b>只读版</b>，供详情页展示）。
     *
     * <p>这是快照读，不锁行。冲突检测<b>不能</b>用这个——见
     * {@link #selectRoomOccupiedSlotsForUpdate}。</p>
     *
     * @param roomId 会议室ID
     * @param date   日期
     * @return 占用行（已排除 CANCELLED —— 已取消的预约时段已释放）
     */
    List<OccupiedSlotDTO> selectRoomOccupiedSlots(@Param("roomId") Long roomId,
                                                  @Param("date") LocalDate date);

    /**
     * ★ 查某会议室某天的占用时段（<b>锁定读</b>版，专供冲突检测）。
     *
     * <p><b>为什么冲突检测必须用锁定读而不是普通 SELECT：</b>
     * MySQL 默认隔离级别 REPEATABLE READ 下，普通 SELECT 是<b>快照读</b>，
     * 读的是事务开始那一刻的快照，<b>看不到别的事务刚提交的记录</b>；
     * {@code SELECT ... FOR UPDATE} 是<b>当前读</b>，读的是最新已提交数据。
     *
     * <p>第 9 周并发自测实际打出来的问题：冲突检测原本用普通 SELECT，
     * 结果 10 个并发请求<b>全都通过了冲突检测</b>，产生 10 条有效预约（超卖）。
     * 只锁 {@code meeting_room} 行<b>不够</b>——它只保证"同一会议室的请求排队"，
     * 但排队后每个事务仍读到各自的旧快照，于是全都判"无冲突"。
     * 改成当前读后，后到的请求能看到先到者刚提交的预约 → 正确判冲突。</p>
     *
     * <p>★ 必须在事务内调用，否则 {@code FOR UPDATE} 立即释放，等于没锁。
     * ★ 锁顺序固定：先 {@link #lockById}（meeting_room 行），再此处（reservation 行范围），
     * 所有并发请求顺序一致 → 不会死锁。</p>
     *
     * @param roomId 会议室ID
     * @param date   日期
     * @return 占用行
     */
    List<OccupiedSlotDTO> selectRoomOccupiedSlotsForUpdate(@Param("roomId") Long roomId,
                                                            @Param("date") LocalDate date);

    /**
     * ★ 锁定会议室行（第 9 周并发防超卖的关键，FR-09 + NF-07）。
     *
     * <p><b>为什么锁 {@code meeting_room} 行而不是 {@code reservation} 行：</b>
     * 并发提交时，冲突方那条记录<strong>还不存在</strong>，锁不到它；
     * 而会议室行一定存在，锁它就能把"同一会议室"的并发请求串行化。
     * 这也是《周推进计划_第10周》§4.2.2 定的方案。</p>
     *
     * <p><b>必须与后续的冲突查询、INSERT 在同一个事务里</b>，
     * 否则锁一放就失效，等于没锁。</p>
     *
     * @param id 会议室ID
     * @return 会议室行（用于确认存在）
     */
    MeetingRoom lockById(@Param("id") Long id);
}

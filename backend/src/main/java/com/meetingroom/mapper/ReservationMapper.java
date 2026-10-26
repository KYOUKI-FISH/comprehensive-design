package com.meetingroom.mapper;

import com.meetingroom.entity.Reservation;
import org.apache.ibatis.annotations.Param;

/**
 * 预约表（D3）数据访问。SQL 写在 {@code resources/mapper/ReservationMapper.xml}。
 */
public interface ReservationMapper {

    /**
     * 插入一条预约（第 9 周；FR-07 提交预约）。
     *
     * <p>★ 编号生成：{@code reservation_no} 先用<strong>临时占位值</strong>插入
     * （由 {@code insertGeneratedNo} 的 {@code useGeneratedKeys} 拿到自增主键后回填）。
     * 不用"当日流水号"，因为并发下流水号会撞唯一键 {@code uk_reservation_no}，
     * 导致本该成功的请求报 500（详见《周推进计划_第10周》§4.2.2）。</p>
     *
     * @param reservation 预约记录（roomId／userId／时间／字段已填好）
     * @return 插入后的自增主键ID
     */
    int insert(Reservation reservation);

    /**
     * 插入后按自增主键回填预约编号。
     *
     * <p>规则：{@code MR} + {@code yyyyMMdd} + 左补零的自增主键，形如 {@code MR20261012001}。
     * 自增主键全局唯一且递增，天然不会撞——这是并发安全的关键。</p>
     *
     * @param id             预约主键
     * @param reservationNo  预约编号
     * @return 受影响行数
     */
    int updateReservationNo(@Param("id") Long id, @Param("reservationNo") String reservationNo);

    /**
     * 按ID查预约。
     *
     * @param id 预约ID
     * @return 预约；不存在时返回 {@code null}
     */
    Reservation selectById(@Param("id") Long id);
}

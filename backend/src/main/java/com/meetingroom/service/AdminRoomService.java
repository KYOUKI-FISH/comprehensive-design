package com.meetingroom.service;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.common.BusinessException;
import com.meetingroom.common.CurrentUser;
import com.meetingroom.dto.RoomVO;
import com.meetingroom.entity.MeetingRoom;
import com.meetingroom.mapper.MeetingRoomMapper;
import com.meetingroom.mapper.ReservationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端会议室管理服务（接口 #11，FR-03 会议室信息管理、FR-04 启用停用）。
 *
 * <p>四个方法一组：{@code list}｜{@code create}｜{@code update}｜{@code delete}。</p>
 *
 * <p><b>★ 为什么和 {@link MeetingRoomService} 分开：</b>
 * 后者是前台只读查询（FR-05／FR-06），被普通用户页面直接调用；
 * 本类是管理端写操作，每个方法都要写操作日志（NF-14）。混在一起会让
 * "哪些方法写日志"变得靠注释维持，日后加方法容易漏。分开后边界是类型级别的。</p>
 *
 * <p><b>★ 每个写方法都标 {@code @Transactional}：</b>业务变更与操作日志必须同事务。
 * "会议室建成功了但日志没写"就是 NF-14 的审计缺口，且事后无法补记。</p>
 */
@Service
public class AdminRoomService {

    private static final Logger log = LoggerFactory.getLogger(AdminRoomService.class);

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_LOCATION_LENGTH = 100;

    private final MeetingRoomMapper meetingRoomMapper;
    private final ReservationMapper reservationMapper;
    private final OperationLogService operationLogService;

    public AdminRoomService(MeetingRoomMapper meetingRoomMapper,
                            ReservationMapper reservationMapper,
                            OperationLogService operationLogService) {
        this.meetingRoomMapper = meetingRoomMapper;
        this.reservationMapper = reservationMapper;
        this.operationLogService = operationLogService;
    }

    /**
     * 会议室列表（接口 #11 GET，与 #3 同口径）。
     *
     * <p>★ 与前台列表的差别：管理端<b>不过滤停用</b>——管理员要能看到并重新启用
     * 被停用的会议室，这正是 FR-04 的用途。前台查询则建议传 {@code status=ENABLED}。</p>
     */
    public List<RoomVO> list(String location, Integer minCapacity, String status) {
        List<MeetingRoom> rooms = meetingRoomMapper.selectByCondition(location, minCapacity, status);
        return rooms.stream().map(RoomVO::from).collect(Collectors.toList());
    }

    /**
     * 新增会议室（接口 #11 POST，FR-03）。
     *
     * <p>同名拒绝由两处保证：这里先查一次给中文提示，{@code uk_room_name} 兜最后的底
     * （两个管理员同时建同名时，前置查询都会通过）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public RoomVO create(Map<String, Object> body) {
        Long operatorId = requireOperator();
        String operatorUsername = CurrentUser.username();

        String name = requireText(body, "name", "请填写会议室名称", MAX_NAME_LENGTH);
        String location = requireText(body, "location", "请填写会议室位置", MAX_LOCATION_LENGTH);
        Integer capacity = parseCapacity(body.get("capacity"));
        String equipment = optionalText(body.get("equipment"), 255);
        String remark = optionalText(body.get("remark"), 255);
        String status = parseStatus(body.get("status"), STATUS_ENABLED);

        MeetingRoom exists = meetingRoomMapper.selectByName(name);
        if (exists != null) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "会议室名称已存在");
        }

        MeetingRoom room = new MeetingRoom();
        room.setName(name);
        room.setLocation(location);
        room.setCapacity(capacity);
        room.setEquipment(equipment == null ? "" : equipment);
        room.setRemark(remark);
        room.setStatus(status);
        try {
            meetingRoomMapper.insert(room);
        } catch (DataIntegrityViolationException e) {
            // 并发下前置查询都通过，唯一键在这里挡住后到的那个
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "会议室名称已存在");
        }

        operationLogService.log(operatorId, operatorUsername,
                OperationLogService.ROOM_CREATE, "ROOM", room.getId(),
                null,
                "{\"name\":\"" + escape(name) + "\",\"status\":\"" + escape(status) + "\"}",
                "管理员新增会议室：" + name);

        log.info("新增会议室：id={}, name={}, operator={}", room.getId(), name, operatorUsername);
        return RoomVO.from(meetingRoomMapper.selectById(room.getId()));
    }

    /**
     * 更新会议室（接口 #11 PUT，FR-03／FR-04）。
     *
     * <p>只更新传了的字段。{@code status} 也走这里：需求清单里"启用／停用"
     * 不是一个独立接口，是 PUT 的一个字段（第 8 周定稿口径）。</p>
     *
     * <p>★ 日志类型按实际变更内容选：只改状态记 {@code ROOM_STATUS}，
     * 改其他字段记 {@code ROOM_UPDATE}。NF-14 要求"四类敏感操作全留痕"时，
     * 停用会议室要能在日志里按类型筛出来，所以两者不能合并成一种。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public RoomVO update(Long id, Map<String, Object> body) {
        Long operatorId = requireOperator();
        String operatorUsername = CurrentUser.username();

        MeetingRoom before = meetingRoomMapper.selectById(id);
        if (before == null) {
            throw new BusinessException(ApiResponse.CODE_NOT_FOUND, "会议室不存在");
        }

        MeetingRoom patch = new MeetingRoom();
        patch.setId(id);
        boolean changed = false;

        if (body != null) {
            if (body.get("name") != null) {
                String name = requireText(body, "name", "请填写会议室名称", MAX_NAME_LENGTH);
                if (!name.equals(before.getName())) {
                    MeetingRoom sameName = meetingRoomMapper.selectByName(name);
                    if (sameName != null && !sameName.getId().equals(id)) {
                        throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "会议室名称已存在");
                    }
                }
                patch.setName(name);
                changed = true;
            }
            if (body.get("location") != null) {
                patch.setLocation(requireText(body, "location", "请填写会议室位置", MAX_LOCATION_LENGTH));
                changed = true;
            }
            if (body.get("capacity") != null) {
                patch.setCapacity(parseCapacity(body.get("capacity")));
                changed = true;
            }
            if (body.get("equipment") != null) {
                // ★ equipment 列 NOT NULL DEFAULT ''：显式传空串表示"清空设备描述"，
                //   不能转成 null，否则 UPDATE 会撞 NOT NULL 约束
                String equipment = optionalText(body.get("equipment"), 255);
                patch.setEquipment(equipment == null ? "" : equipment);
                changed = true;
            }
            if (body.get("remark") != null) {
                patch.setRemark(optionalText(body.get("remark"), 255));
                changed = true;
            }
            if (body.get("status") != null) {
                patch.setStatus(parseStatus(body.get("status"), null));
                changed = true;
            }
        }

        if (!changed) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "请至少提供一个要修改的字段（name／location／capacity／equipment／remark／status）");
        }

        try {
            meetingRoomMapper.updateSelective(patch);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "会议室名称已存在");
        }

        boolean onlyStatusChanged = patch.getStatus() != null
                && patch.getName() == null && patch.getLocation() == null
                && patch.getCapacity() == null && patch.getEquipment() == null
                && patch.getRemark() == null;
        String logType = onlyStatusChanged
                ? OperationLogService.ROOM_STATUS
                : OperationLogService.ROOM_UPDATE;

        operationLogService.log(operatorId, operatorUsername,
                logType, "ROOM", id,
                "{\"name\":\"" + escape(before.getName()) + "\",\"status\":\"" + escape(before.getStatus()) + "\"}",
                "{\"name\":\"" + escape(patch.getName() == null ? before.getName() : patch.getName())
                        + "\",\"status\":\"" + escape(patch.getStatus() == null ? before.getStatus() : patch.getStatus()) + "\"}",
                onlyStatusChanged
                        ? "管理员" + statusLabel(patch.getStatus()) + "会议室：" + before.getName()
                        : "管理员修改会议室信息：" + before.getName());

        log.info("更新会议室：id={}, operator={}, onlyStatus={}", id, operatorUsername, onlyStatusChanged);
        return RoomVO.from(meetingRoomMapper.selectById(id));
    }

    /**
     * 删除会议室（接口 #11 DELETE，FR-03）。
     *
     * <p><b>★ 有预约记录时必须拒绝：</b>返回 409，提示照任务书 §5 卡点 8 的口径
     * "该会议室存在有效预约，请先处理预约"。</p>
     *
     * <p>两道闸：① 这里先数一次，给用户一句能看懂的提示；
     * ② 数据库 {@code FK ... ON DELETE RESTRICT} 兜底——并发下"数完刚好有人插入"仍可能漏。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long operatorId = requireOperator();
        String operatorUsername = CurrentUser.username();

        MeetingRoom room = meetingRoomMapper.selectById(id);
        if (room == null) {
            throw new BusinessException(ApiResponse.CODE_NOT_FOUND, "会议室不存在");
        }

        long reservationCount = reservationMapper.countByRoomId(id);
        if (reservationCount > 0) {
            throw new BusinessException(ApiResponse.CODE_CONFLICT,
                    "该会议室存在有效预约，请先处理预约（当前挂着 " + reservationCount + " 条记录）");
        }

        try {
            meetingRoomMapper.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            // 前置校验之后仍有人插入了预约，外键挡住
            throw new BusinessException(ApiResponse.CODE_CONFLICT,
                    "该会议室存在有效预约，请先处理预约");
        }

        operationLogService.log(operatorId, operatorUsername,
                OperationLogService.ROOM_DELETE, "ROOM", id,
                "{\"name\":\"" + escape(room.getName()) + "\",\"status\":\"" + escape(room.getStatus()) + "\"}",
                null,
                "管理员删除会议室：" + room.getName());

        log.info("删除会议室：id={}, name={}, operator={}", id, room.getName(), operatorUsername);
    }

    /** 取当前管理员ID；拿不到即未登录（正常流程由拦截器保证，这里是兜底） */
    private Long requireOperator() {
        Long operatorId = CurrentUser.userId();
        if (operatorId == null || CurrentUser.username() == null) {
            throw new BusinessException(ApiResponse.CODE_UNAUTHORIZED, "未登录");
        }
        return operatorId;
    }

    /** 必填文本字段：去空格、非空、不超长 */
    private String requireText(Map<String, Object> body, String key, String emptyMessage, int maxLength) {
        Object raw = body == null ? null : body.get(key);
        String value = raw == null ? "" : raw.toString().trim();
        if (value.isEmpty()) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, emptyMessage);
        }
        if (value.length() > maxLength) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    emptyMessage.replace("请填写", "") + "不能超过 " + maxLength + " 个字符");
        }
        return value;
    }

    /** 选填文本字段：null／空白统一转 null，不传长度限制则按 255 */
    private String optionalText(Object raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String value = raw.toString().trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "内容不能超过 " + maxLength + " 个字符");
        }
        return value;
    }

    /** 容量：必须是大于 0 的整数（ck_room_capacity 兜底，这里给中文提示） */
    private Integer parseCapacity(Object raw) {
        if (raw == null) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "请填写可容纳人数");
        }
        int capacity;
        try {
            capacity = raw instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "可容纳人数必须是大于 0 的整数");
        }
        if (capacity <= 0) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR, "可容纳人数必须大于 0");
        }
        return capacity;
    }

    /** 状态枚举校验；传 null 时用 defaultValue，defaultValue 也为 null 则报错 */
    private String parseStatus(Object raw, String defaultValue) {
        if (raw == null || raw.toString().isBlank()) {
            if (defaultValue != null) {
                return defaultValue;
            }
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "会议室状态只能是 ENABLED（可用）或 DISABLED（停用）");
        }
        String status = raw.toString().trim().toUpperCase();
        if (!STATUS_ENABLED.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new BusinessException(ApiResponse.CODE_PARAM_ERROR,
                    "会议室状态只能是 ENABLED（可用）或 DISABLED（停用）");
        }
        return status;
    }

    /** 状态中文名（NF-10） */
    private String statusLabel(String status) {
        if (STATUS_ENABLED.equals(status)) {
            return "启用";
        }
        if (STATUS_DISABLED.equals(status)) {
            return "停用";
        }
        return "修改状态";
    }

    /** JSON 转义（与 AdminBookingService 同口径，避免用户输入破坏 JSON 列） */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

package com.meetingroom.dto;

/**
 * 取消预约的请求体（{@code DELETE /api/bookings/{id}} 可选入参，第 10 周接口 #8）。
 *
 * <p>★ 用户自行取消的缘由是<b>选填</b>：FR-13 只要求"用户可以取消自己的预约"，
 * "必填原因"是 FR-16 对<b>管理员强制取消</b>的要求（接口 #10，组长实现），
 * 两者不要混淆。传了就存 {@code cancel_reason}，方便管理员在全部预约页看到。</p>
 */
public class CancelRequest {

    /** 取消原因（选填，最长 255 字符） */
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

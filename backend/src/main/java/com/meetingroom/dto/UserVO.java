package com.meetingroom.dto;

import com.meetingroom.entity.User;

import java.time.LocalDateTime;

/**
 * 用户视图（接口 #12 出参元素）。
 *
 * <p><b>★ 本类刻意不包含 {@code password} 字段。</b>接口清单 §#12 写明
 * "GET 出参 UserVO[]（不含 password 字段）"，这里从类型层面就让它不可能被带出去——
 * 比"记得在 Service 里 setPassword(null)"可靠：漏一句赋值就是口令摘要进响应体，
 * 而 BCrypt 摘要虽然不可直读，落到前端和日志里仍属 NF-05 不能接受的信息外泄。</p>
 */
public class UserVO {

    private Long id;
    private String username;
    private String realName;
    /** USER／ADMIN */
    private String role;
    private String phone;
    /** ACTIVE／DISABLED */
    private String status;
    private LocalDateTime createdAt;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRole(user.getRole());
        vo.setPhone(user.getPhone());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

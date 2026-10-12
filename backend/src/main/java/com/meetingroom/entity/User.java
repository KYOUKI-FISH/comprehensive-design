package com.meetingroom.entity;

import java.time.LocalDateTime;

/**
 * 用户实体 —— 对应建表脚本 {@code user} 表（DFD 数据存储 D1 用户信息）。
 */
public class User {

    /** 用户ID */
    private Long id;
    /** 登录账号（唯一） */
    private String username;
    /** 登录密码。★骨架阶段明文，第 9～10 周替换为 BCrypt 摘要 */
    private String password;
    /** 姓名 */
    private String realName;
    /** 角色：USER=普通用户 / ADMIN=管理员（FR-02） */
    private String role;
    /** 联系电话 */
    private String phone;
    /** 账号状态：ACTIVE=正常 / DISABLED=停用（FR-14） */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

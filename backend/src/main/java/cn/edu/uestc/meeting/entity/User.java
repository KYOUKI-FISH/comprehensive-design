package cn.edu.uestc.meeting.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

/**
 * 用户实体 —— 对应 DFD 数据存储 <b>D1 用户信息</b>，库表 {@code user}。
 *
 * <p>用途：P1 验证登录、P5 管理用户。
 */
public class User {

    private Long id;

    /** 登录账号（唯一） */
    private String username;

    /**
     * 密码密文（BCrypt，60 字符）。
     * <p>加 {@code @JsonIgnore}：即使将来不小心把 User 直接当出参返回，
     * 密文也不会被序列化到响应里——这是骨架阶段就该定下的安全底线（NF 安全类要求）。
     */
    @JsonIgnore
    private String password;

    /** 姓名 */
    private String realName;

    /** 角色：{@code USER}=普通用户，{@code ADMIN}=管理员（FR-02 角色区分） */
    private String role;

    /** 账号状态：1=启用，0=停用（FR-14 管理用户账号） */
    private Integer status;

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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
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

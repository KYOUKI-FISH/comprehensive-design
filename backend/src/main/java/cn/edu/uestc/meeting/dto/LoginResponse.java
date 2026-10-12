package cn.edu.uestc.meeting.dto;

/**
 * 登录成功返回体（P1 验证登录的输出数据流："登录结果（成功／失败）、角色"）。
 *
 * <p><b>骨架阶段刻意不返回令牌／会话</b>：本周只验证"账号密码对不对、能不能拿到角色"，
 * 令牌签发、请求鉴权属第 9～10 周范围；现在发一个假令牌反而会在第 7 周接口设计时推翻。
 * 前端目前把登录结果存在内存中，刷新即失效——这是已知且被接受的临时状态。
 */
public class LoginResponse {

    private Long userId;

    private String username;

    private String realName;

    /** 角色：{@code USER} 普通用户 / {@code ADMIN} 管理员（FR-02 角色区分） */
    private String role;

    public LoginResponse() {
    }

    public LoginResponse(Long userId, String username, String realName, String role) {
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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
}

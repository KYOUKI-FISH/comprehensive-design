package com.meetingroom.config;

import com.meetingroom.common.ApiResponse;
import com.meetingroom.common.CurrentUser;
import com.meetingroom.entity.User;
import com.meetingroom.mapper.UserMapper;
import com.meetingroom.service.TokenStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器（第 9 周；对应 FR-02 角色区分与权限控制、NF-03 权限隔离、
 * NF-04 会话安全、NF-05 账号安全）。
 *
 * <p><b>职责：</b>
 * <ol>
 *   <li>公开接口（登录）放行；</li>
 *   <li>其余接口校验 {@code Authorization: Bearer <token>}；</li>
 *   <li>每次请求<strong>回查账号状态</strong>：{@code DISABLED} 立即拒并作废该用户全部会话
 *       （NF-05"停用账号后活跃会话立刻失效"）——令牌本身不查库不行，
 *       否则管理员停用账号后，旧令牌在有效期内仍能继续操作；</li>
 *   <li>把身份写入 {@link CurrentUser} 供业务层取用。</li>
 * </ol>
 *
 * <p><b>★ 权限边界的正确位置：</b>前端隐藏菜单<strong>不是</strong>安全边界
 * （前端守卫可被绕过），真正的边界在这里。因此"普通用户调管理接口被拒"（NF-03）
 * 必须由后端保证——{@link #checkAdminPath} 单独处理 {@code /api/admin/**}。
 *
 * <p><b>异常提示：</b>一律给面向使用者的中文（NF-10），
 * 不把"令牌无效""权限不足"等技术说法丢给普通用户。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 公开接口白名单：登录本身不需要令牌 */
    private static final String[] PUBLIC_PATHS = {
            "/api/auth/login",
            // 健康检查：README「判断是否跑通」的第一步就是打这个接口，
            // 它用于排查"后端起没起、库连没连"，若被鉴权拦住就没法排查了
            "/api/health"
    };

    /** 管理员专属路径前缀（FR-02、NF-03） */
    private static final String ADMIN_PREFIX = "/api/admin/";

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenStore tokenStore;
    private final UserMapper userMapper;

    public AuthInterceptor(TokenStore tokenStore, UserMapper userMapper) {
        this.tokenStore = tokenStore;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();

        // ★ BUG-07（第 12 周）必须放行的一种请求：CORS 预检（OPTIONS）不携带令牌，
        //   由 Spring MVC 的预检处理器直接应答 CORS 头，不会执行任何 Controller 方法。
        //   修复前 writeJson 写 HTTP 200，预检恰好能过；改成 401 后若继续在这里拦，
        //   浏览器会判定预检失败，所有带 Authorization 的跨域业务请求都发不出去（页面级全废）。
        //   放行只影响 OPTIONS，不放宽 GET/POST/PUT/DELETE 的鉴权边界。
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 公开接口放行
        for (String publicPath : PUBLIC_PATHS) {
            if (publicPath.equals(path)) {
                return true;
            }
        }

        String token = extractToken(request);
        TokenStore.Session session = tokenStore.validate(token);
        if (session == null) {
            // 令牌不存在／已过期／已登出 —— 统一提示"未登录或登录已过期"，不区分具体原因
            writeUnauthorized(response, "未登录或登录已过期，请重新登录");
            return false;
        }

        // ★ NF-05：每次请求回查账号状态。管理员停用账号后，旧令牌立刻失效。
        User user = userMapper.selectById(session.getUserId());
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            tokenStore.revokeAllOf(session.getUserId());
            writeForbidden(response, "该账号已被停用，请联系管理员");
            return false;
        }

        // ★ NF-03：管理员专属路径，普通用户一律拒绝
        if (path.startsWith(ADMIN_PREFIX) && !"ADMIN".equals(session.getRole())) {
            writeForbidden(response, "无权访问该功能");
            return false;
        }

        CurrentUser.set(session);
        // ★ 与预约模块的衔接点：虞业宸的 CurrentUserResolver 优先读 request 属性 "userId"。
        //   少了这一行，POST /api/bookings 一律返回 401「未登录」——合并时最容易漏的一处。
        request.setAttribute("userId", session.getUserId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // ★ 必须清理：线程池会复用线程，不清理会串号（A 的身份被 B 读到）
        CurrentUser.clear();
    }

    /** 从 Authorization 头取 Bearer 令牌；也兼容 query 参数（便于 curl 调试） */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return request.getParameter("token");
    }

    private void writeUnauthorized(HttpServletResponse response, String message) {
        writeJson(response, ApiResponse.CODE_UNAUTHORIZED, message);
    }

    private void writeForbidden(HttpServletResponse response, String message) {
        writeJson(response, ApiResponse.CODE_FORBIDDEN, message);
    }

    /** 直接写出统一返回体（与 {@code ApiResponse} 结构一致）。
     *  ★ BUG-07（第 12 周）：HTTP 状态行与 body.code 双写——业务码数值与 HTTP 状态码一致，
     *    401/403 在传输层也可见。body 结构与提示文案不变。 */
    private void writeJson(HttpServletResponse response, int code, String message) {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        try {
            String json = "{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}";
            response.getWriter().write(json);
            response.getWriter().flush();
        } catch (Exception e) {
            // 写响应失败只能记日志，此处不应再抛异常掩盖原始问题
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}

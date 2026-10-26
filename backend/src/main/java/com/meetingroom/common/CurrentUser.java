package com.meetingroom.common;

import com.meetingroom.service.TokenStore;

/**
 * 当前登录用户上下文（第 9 周）。
 *
 * <p><b>为什么用 ThreadLocal 而不是方法参数层层传：</b>
 * 鉴权在拦截器里完成，Controller／Service 不必每个方法都带一个 token 参数，
 * 代码读起来更接近业务语义（"当前用户"是个横切关注点）。
 *
 * <p><b>⚠️ 必须 remember 清理：</b>线程池会复用线程，ThreadLocal 不清理会串号
 * （A 用户的请求结束后，B 用户复用同一线程时读到 A 的身份）。
 * 因此 {@link AuthInterceptor} 在 {@code afterCompletion} 里<strong>必须</strong>调用
 * {@link #clear()}，这是本类的关键约束，改动时务必保留。
 */
public final class CurrentUser {

    /** 当前用户ID */
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    /** 当前登录账号 */
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    /** 当前角色：USER / ADMIN */
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();

    private CurrentUser() {
    }

    /** 写入当前请求的登录者身份 */
    public static void set(TokenStore.Session session) {
        USER_ID.set(session.getUserId());
        USERNAME.set(session.getUsername());
        ROLE.set(session.getRole());
    }

    /** 清理当前线程的登录者身份（拦截器 afterCompletion 必须调用） */
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        ROLE.remove();
    }

    /** 当前用户ID；未登录时返回 {@code null} */
    public static Long userId() {
        return USER_ID.get();
    }

    /** 当前登录账号；未登录时返回 {@code null} */
    public static String username() {
        return USERNAME.get();
    }

    /** 当前角色；未登录时返回 {@code null} */
    public static String role() {
        return ROLE.get();
    }

    /** 当前是否是管理员 */
    public static boolean isAdmin() {
        return "ADMIN".equals(ROLE.get());
    }
}

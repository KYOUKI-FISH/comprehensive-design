package com.meetingroom.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 取"当前登录用户ID"的唯一入口。
 *
 * <p>★ 为什么单独抽出来：第 9 周令牌机制由组长并行开发（服务端随机串 + 内存失效标记），
 * 预约模块不能等它落地才能开工，也不能自己再写一套令牌——两套会打架。</p>
 *
 * <p>取值来源只有一个：{@code request.getAttribute("userId")}，
 * 由 {@code AuthInterceptor} 校验令牌通过后写入。</p>
 *
 * <p><b>★ 第 9 周融合时已删除过渡期的 {@code X-User-Id} 请求头分支。</b>
 * 那个分支原本用于"令牌模块尚未合入"时联调，它<b>不是安全边界</b>：
 * 任何持有自己令牌的人只要改一下这个头，就能以别人的身份提交预约（越权）。
 * 令牌合入后它的存在价值为零、风险却实实在在，因此按原注释的约定移除。
 * 详见 {@code docs/week09/01_接口变更记录.md}。</p>
 *
 * <p>没有该属性时返回 {@code null}，由调用方抛 401「未登录」。</p>
 */
@Component
public class CurrentUserResolver {

    /** 令牌拦截器写入的属性名 */
    public static final String ATTRIBUTE_USER_ID = "userId";

    public Long resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object attribute = request.getAttribute(ATTRIBUTE_USER_ID);
        if (attribute instanceof Long userId) {
            return userId;
        }
        if (attribute instanceof Number number) {
            return number.longValue();
        }
        if (attribute != null) {
            try {
                return Long.parseLong(attribute.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}

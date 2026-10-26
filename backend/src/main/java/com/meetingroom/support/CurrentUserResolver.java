package com.meetingroom.support;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 取"当前登录用户ID"的唯一入口。
 *
 * <p>★ 为什么单独抽出来：第 9 周令牌机制由组长并行开发（服务端随机串 + 内存失效标记），
 * 预约模块不能等它落地才能开工，也不能自己再写一套令牌——两套会打架。约定按这个顺序取：</p>
 *
 * <ol>
 *   <li>{@code request.getAttribute("userId")} —— 令牌拦截器校验通过后写入的**正式通路**；
 *       组长的令牌模块合入后，只需在拦截器里 setAttribute，本模块零改动生效。</li>
 *   <li>请求头 {@code X-User-Id} —— **过渡期兼容**（令牌模块尚未合入时，联调与自测用）。
 *       它只是开发期的临时通道，不是安全边界；令牌合入后应删掉这一分支。</li>
 * </ol>
 *
 * <p>两处都没有时返回 {@code null}，由调用方抛 401「未登录」。</p>
 */
@Component
public class CurrentUserResolver {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserResolver.class);

    /** 过渡期请求头名 */
    public static final String HEADER_USER_ID = "X-User-Id";

    public Long resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object attribute = request.getAttribute("userId");
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
                // 落到过渡期通道
            }
        }

        String header = request.getHeader(HEADER_USER_ID);
        if (header != null && !header.isBlank()) {
            try {
                Long userId = Long.parseLong(header.trim());
                log.debug("使用过渡期通道 {} 识别用户：userId={}（令牌模块合入后应移除）", HEADER_USER_ID, userId);
                return userId;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}

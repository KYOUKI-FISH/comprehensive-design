package com.meetingroom.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 统一返回体的最小单元测试。
 *
 * <p>★ 刻意不依赖 Spring 上下文与数据库：骨架阶段本机可能还没装 MySQL，
 * 这类测试必须"零环境"也能跑通（{@code mvn test}）。涉及数据库的集成测试在第 7 周测试计划里安排。</p>
 */
class ApiResponseTest {

    @Test
    @DisplayName("success(data)：code=200、message=ok、data 原样带回")
    void successWithData() {
        ApiResponse<String> response = ApiResponse.success("hello");

        assertEquals(ApiResponse.CODE_OK, response.getCode());
        assertEquals("ok", response.getMessage());
        assertEquals("hello", response.getData());
    }

    @Test
    @DisplayName("success()：无数据时 data 为 null")
    void successWithoutData() {
        ApiResponse<Void> response = ApiResponse.success();

        assertEquals(ApiResponse.CODE_OK, response.getCode());
        assertNull(response.getData());
    }

    @Test
    @DisplayName("error(code, message)：业务失败也走 HTTP 200，靠 code 区分")
    void error() {
        ApiResponse<Void> response = ApiResponse.error(ApiResponse.CODE_UNAUTHORIZED, "账号或密码错误");

        assertEquals(401, response.getCode());
        assertEquals("账号或密码错误", response.getMessage());
        assertNull(response.getData());
    }
}

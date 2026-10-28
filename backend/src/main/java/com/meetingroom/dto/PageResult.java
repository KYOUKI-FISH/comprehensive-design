package com.meetingroom.dto;

import java.util.List;

/**
 * 分页结果包装（接口 #13 明确要求外层包 {@code total}／{@code pageNum}／{@code pageSize}）。
 *
 * <p>管理端列表（#9 全部预约、#13 操作日志）都用它：前端拿到 {@code total} 才能算总页数，
 * 只给一个数组的话，翻页控件无从下手。</p>
 *
 * <p>★ 为什么不直接返回 MyBatis 的 {@code Page} 或 PageHelper 的对象：
 * 两者都会把框架类带进响应体（多出 {@code pages}／{@code size} 等一堆字段），
 * 而接口清单只规定了三个字段。多给字段看似无害，但前端按文档写的解析代码
 * 遇到没预期的字段容易在联调时反复确认"这个能不能用"，不如照清单给。</p>
 *
 * @param <T> 列表元素类型
 */
public class PageResult<T> {

    private long total;
    private int pageNum;
    private int pageSize;
    private List<T> list;

    public PageResult() {
    }

    public PageResult(long total, int pageNum, int pageSize, List<T> list) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.list = list;
    }

    public static <T> PageResult<T> of(long total, int pageNum, int pageSize, List<T> list) {
        return new PageResult<>(total, pageNum, pageSize, list);
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }
}

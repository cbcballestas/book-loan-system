package com.cballestas.dto;

import java.util.List;

public class PaginatedResponseDto<T> {
    public List<T> content;
    public int pageIndex;
    public int pageSize;
    public int totalPages;
    public long totalElements;

    public PaginatedResponseDto(List<T> content, int pageIndex, int pageSize, int totalPages, long totalElements) {
        this.content = content;
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
    }
}

package tz.co.hmy.pis.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A stable pagination envelope.
 *
 * Returning Spring's Page directly serialises internal structure that changes
 * between versions and leaks Pageable into the contract. This is the shape the
 * API promises.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}

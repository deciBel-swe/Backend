package software.decibel.dtos.admin;

import jakarta.validation.constraints.Min;

/**
 * Query parameters for GET /admin/reports.
 * Defaults match the OpenAPI spec: page=0, size=20.
 */
public record ListReportsRequest(
    @Min(value = 0, message = "Page must be 0 or greater")
    int page,

    @Min(value = 1, message = "Size must be at least 1")
    int size
) {
    public static ListReportsRequest withDefaults(Integer page, Integer size) {
        return new ListReportsRequest(
            page != null ? page : 0,
            size != null ? size : 20
        );
    }
}

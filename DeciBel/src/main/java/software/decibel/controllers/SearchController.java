package software.decibel.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import software.decibel.dtos.search.SearchResponse;
import software.decibel.services.search.SearchService;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@org.springframework.validation.annotation.Validated
public class SearchController {

    private final SearchService searchService;

    /**
     * Global search endpoint.
     * UPDATED: Added @NotBlank and @Size validation for the query 'q'.
     */
    @GetMapping
    public ResponseEntity<SearchResponse> search(
            @RequestParam @NotBlank @Size(min = 2) String q,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(searchService.search(q, type, page, size));
    }
}

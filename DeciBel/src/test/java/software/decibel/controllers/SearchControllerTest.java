package software.decibel.controllers;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import software.decibel.dtos.search.SearchResponse;
import software.decibel.services.search.SearchService;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SearchService searchService;

    @InjectMocks
    private SearchController searchController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(searchController).build();
    }

    @Test
    void search_withValidQuery_returnsOk() throws Exception {
        SearchResponse response = new SearchResponse(Collections.emptyList(), 0, 10, 0, 0, true);
        when(searchService.search(eq("test"), eq(null), anyInt(), anyInt())).thenReturn(response);

        mockMvc.perform(get("/search").param("q", "test"))
                .andExpect(status().isOk());
    }

    @Test
    void search_withShortQuery_returnsBadRequest() throws Exception {
        when(searchService.search(eq("t"), eq(null), anyInt(), anyInt()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query must be at least 2 characters"));

        mockMvc.perform(get("/search").param("q", "t"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_withInvalidType_returnsBadRequest() throws Exception {
        when(searchService.search(anyString(), eq("invalid"), anyInt(), anyInt()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid search type"));

        mockMvc.perform(get("/search").param("q", "test").param("type", "invalid"))
                .andExpect(status().isBadRequest());
    }
}

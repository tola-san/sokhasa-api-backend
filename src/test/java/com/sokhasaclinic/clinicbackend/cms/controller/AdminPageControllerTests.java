package com.sokhasaclinic.clinicbackend.cms.controller;

import com.sokhasaclinic.clinicbackend.cms.dto.PageDetailResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.PageListingResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.PaginationDto;
import com.sokhasaclinic.clinicbackend.cms.dto.SavePageRequest;
import com.sokhasaclinic.clinicbackend.cms.dto.TranslationDto;
import com.sokhasaclinic.clinicbackend.cms.dto.VersionedPageResponse;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsExceptionHandler;
import com.sokhasaclinic.clinicbackend.cms.service.PageService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminPageControllerTests {

    private PageService pageService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        pageService = mock(PageService.class);
        AdminPageController controller = new AdminPageController(pageService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new CmsExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/admin/pages should return 200 with paginated listings and Cache-Control: no-store")
    void listPagesReturns200() throws Exception {
        PageListingResponse mockResponse = new PageListingResponse(
                List.of(),
                new PaginationDto(0, 20, 0, 0, false, false)
        );
        when(pageService.listPages(anyInt(), anyInt(), any(), any(), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/admin/pages"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.pagination.totalElements").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/admin/pages should return 201 Created with ETag and Location")
    void createPageReturns201() throws Exception {
        UUID pageId = UUID.randomUUID();
        PageDetailResponse detail = new PageDetailResponse(
                pageId,
                "about-us",
                "DRAFT",
                List.of(new TranslationDto("en", "About", "<p>About Sokhasa</p>", null)),
                List.of(),
                null,
                Instant.now(),
                Instant.now()
        );
        when(pageService.createPage(any(SavePageRequest.class)))
                .thenReturn(new VersionedPageResponse(detail, 1));

        String requestJson = """
                {
                    "slug": "about-us",
                    "translations": [
                        {
                            "languageCode": "en",
                            "title": "About",
                            "content": "<p>About Sokhasa</p>"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/admin/pages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(header().string("Location", "/api/v1/admin/pages/" + pageId))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.slug").value("about-us"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/pages/{id} should return 204 No Content")
    void deletePageReturns204() throws Exception {
        UUID pageId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/admin/pages/" + pageId)
                        .header("If-Match", "\"1\""))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));

        verify(pageService).deletePage(pageId, "\"1\"");
    }

    @Test
    @DisplayName("POST /api/v1/admin/pages with invalid slug returns 400 with VALIDATION_ERROR problem details")
    void invalidSlugReturns400() throws Exception {
        String invalidJson = """
                {
                    "slug": "INVALID SLUG",
                    "translations": [
                        {
                            "languageCode": "en",
                            "title": "Title",
                            "content": "<p>Content</p>"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/admin/pages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));
    }
}

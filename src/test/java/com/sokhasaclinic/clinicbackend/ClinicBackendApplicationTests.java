package com.sokhasaclinic.clinicbackend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sokhasaclinic.clinicbackend.cms.dto.SavePageRequest;
import com.sokhasaclinic.clinicbackend.cms.dto.TranslationDto;
import com.sokhasaclinic.clinicbackend.cms.exception.PageNotFoundException;
import com.sokhasaclinic.clinicbackend.cms.service.PageService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ClinicBackendApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void createdDraftAppearsInAdminListing(@Autowired PageService service) {
        String slug = "cms-test-" + UUID.randomUUID();
        var draft = service.createPage(new SavePageRequest(
                slug, List.of(new TranslationDto("en", "CMS test", "Draft content", null))));

        try {
            var pages = service.listPages(0, 20, "DRAFT", slug, "updatedAt,desc").data();
            assertEquals(1, pages.size());
            assertEquals(draft.data().id(), pages.getFirst().id());
            assertEquals("DRAFT", pages.getFirst().status());
            assertThrows(PageNotFoundException.class, () -> service.getPublishedPage(slug, List.of("en")));
        } finally {
            service.deletePage(draft.data().id(), "\"" + draft.revision() + "\"");
        }
    }

}

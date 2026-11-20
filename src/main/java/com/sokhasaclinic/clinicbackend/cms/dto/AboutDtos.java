package com.sokhasaclinic.clinicbackend.cms.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** API values for the single, immediately published About page (AB-01 to AB-14). */
public final class AboutDtos {
    private AboutDtos() { }
    public record PageText(String languageCode, String heroTitle, String heroSubtitle,
            String storyTitle, String storyBody) { }
    public record PillarText(String languageCode, String title, String text, String note) { }
    public record Pillar(String type, List<PillarText> translations) { }
    public record FigureText(String languageCode, String label) { }
    public record Figure(String value, List<FigureText> translations) { }
    public record FounderWrite(UUID doctorId) { }
    public record Founder(UUID doctorId, String fullName,
            @com.fasterxml.jackson.annotation.JsonProperty("public") boolean publicProfile) { }
    public record FacilityText(String languageCode, String title, String description, String badge,
            String imageAltText) { }
    public record FacilityWrite(UUID id, List<FacilityText> translations) { }
    public record Facility(UUID id, String imageUrl, List<FacilityText> translations) { }
    public record Update(List<PageText> translations, List<Pillar> pillars, List<Figure> figures,
            List<FounderWrite> founders, List<FacilityWrite> facilities) { }
    public record Admin(long version, List<PageText> translations, List<Pillar> pillars, List<Figure> figures,
            List<Founder> founders, List<Facility> facilities, Instant updatedAt) { }
    public record Versioned(Admin data, long version) { }
    public record PublicPillar(String type, String title, String text, String note) { }
    public record PublicFigure(String value, String label) { }
    public record PublicFounder(String slug, String fullName, String title, String bio, List<String> tags,
            String imageUrl, String imageAltText) { }
    public record PublicFacility(String title, String description, String badge, String imageUrl,
            String imageAltText) { }
    public record PublicPage(String heroTitle, String heroSubtitle, String storyTitle, List<String> storyParagraphs,
            List<PublicPillar> pillars, List<PublicFigure> figures, List<PublicFounder> founders,
            List<PublicFacility> facilities) { }
    public record AltText(String languageCode, String imageAltText) { }
}

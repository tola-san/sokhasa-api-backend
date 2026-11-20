package com.sokhasaclinic.clinicbackend.cms.repository;

import com.sokhasaclinic.clinicbackend.cms.dto.AboutDtos.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Relational About aggregate; writers serialize on the singleton page row. */
@Repository
public class AboutRepository {
    private final JdbcTemplate jdbc;
    public AboutRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record State(long version, Instant updatedAt) { }
    public record FacilityRow(UUID id, UUID mediaId) { }
    public State state(boolean lock) {
        return jdbc.queryForObject("select version, updated_at from clinic.about_page where id=1"
                + (lock ? " for update" : ""), (r, n) -> new State(r.getLong(1), r.getTimestamp(2).toInstant()));
    }
    public void touch() {
        jdbc.update("update clinic.about_page set version=version+1, updated_at=current_timestamp where id=1");
    }
    public List<PageText> texts() {
        return jdbc.query("select * from clinic.about_page_translation order by language_code", (r,n) ->
                new PageText(r.getString("language_code"), r.getString("hero_title"), r.getString("hero_subtitle"),
                        r.getString("story_title"), r.getString("story_body")));
    }
    public List<Pillar> pillars() {
        return List.of("MISSION", "VISION", "VALUES").stream().map(type -> new Pillar(type,
                jdbc.query("select * from clinic.about_pillar_translation where type=? order by language_code",
                        (r,n) -> new PillarText(r.getString("language_code"), r.getString("title"),
                                r.getString("text"), r.getString("note")), type))).toList();
    }
    public List<Figure> figures() {
        return jdbc.query("select * from clinic.about_figure order by sort_order", (r,n) -> new Figure(
                r.getString("value"), jdbc.query("select * from clinic.about_figure_translation where figure_id=? order by language_code",
                        (t,i) -> new FigureText(t.getString("language_code"), t.getString("label")),
                        r.getObject("id", UUID.class))));
    }
    public List<Founder> founders() {
        return jdbc.query("select d.id, d.full_name, d.status='ACTIVE' and d.slug is not null and "
                + "(select count(*) from clinic.doctor_translation t where t.doctor_id=d.id)=3 as visible "
                + "from clinic.about_founder f join clinic.doctor d on d.id=f.doctor_id order by f.sort_order",
                (r,n) -> new Founder(r.getObject(1, UUID.class), r.getString(2), r.getBoolean(3)));
    }
    public List<FacilityRow> facilities() {
        return jdbc.query("select id, image_media_id from clinic.about_facility order by sort_order",
                (r,n) -> new FacilityRow(r.getObject(1, UUID.class), r.getObject(2, UUID.class)));
    }
    public List<FacilityText> facilityTexts(UUID id) {
        return jdbc.query("select * from clinic.about_facility_translation where facility_id=? order by language_code",
                (r,n) -> new FacilityText(r.getString("language_code"), r.getString("title"),
                        r.getString("description"), r.getString("badge"), r.getString("image_alt_text")), id);
    }
    public void replace(Update update) {
        jdbc.update("delete from clinic.about_page_translation");
        for (PageText t : update.translations()) jdbc.update("insert into clinic.about_page_translation values (?, ?, ?, ?, ?)",
                t.languageCode(), t.heroTitle(), t.heroSubtitle(), t.storyTitle(), t.storyBody());
        jdbc.update("delete from clinic.about_pillar_translation");
        for (Pillar p : update.pillars()) for (PillarText t : p.translations())
            jdbc.update("insert into clinic.about_pillar_translation values (?, ?, ?, ?, ?)", p.type(),
                    t.languageCode(), t.title(), t.text(), t.note());
        jdbc.update("delete from clinic.about_figure");
        for (int i=0; i<update.figures().size(); i++) {
            Figure f=update.figures().get(i); UUID id=UUID.randomUUID();
            jdbc.update("insert into clinic.about_figure values (?, ?, ?)", id, i, f.value());
            for (FigureText t : f.translations()) jdbc.update("insert into clinic.about_figure_translation values (?, ?, ?)",
                    id, t.languageCode(), t.label());
        }
        jdbc.update("delete from clinic.about_founder");
        for (int i=0; i<update.founders().size(); i++)
            jdbc.update("insert into clinic.about_founder values (?, ?)", update.founders().get(i).doctorId(), i);
        List<UUID> kept = update.facilities().stream().map(FacilityWrite::id).toList();
        for (FacilityRow f : facilities()) if (!kept.contains(f.id()))
            jdbc.update("delete from clinic.about_facility where id=?", f.id());
        for (int i=0; i<update.facilities().size(); i++) {
            FacilityWrite f=update.facilities().get(i);
            jdbc.update("insert into clinic.about_facility (id, sort_order) values (?, ?) "
                    + "on conflict (id) do update set sort_order=excluded.sort_order", f.id(), i);
            jdbc.update("delete from clinic.about_facility_translation where facility_id=?", f.id());
            for (FacilityText t : f.translations()) jdbc.update("insert into clinic.about_facility_translation values (?, ?, ?, ?, ?, ?)",
                    f.id(), t.languageCode(), t.title(), t.description(), t.badge(), t.imageAltText());
        }
    }
    public void image(UUID facility, UUID media, List<AltText> alts) {
        jdbc.update("update clinic.about_facility set image_media_id=? where id=?", media, facility);
        jdbc.update("update clinic.about_facility_translation set image_alt_text=null where facility_id=?", facility);
        for (AltText t : alts) jdbc.update("update clinic.about_facility_translation set image_alt_text=? "
                + "where facility_id=? and language_code=?", t.imageAltText(), facility, t.languageCode());
    }
}

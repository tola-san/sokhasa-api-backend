package com.sokhasaclinic.clinicbackend.cms.service;

import com.sokhasaclinic.clinicbackend.cms.dto.AboutDtos.*;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.model.CmsMedia;
import com.sokhasaclinic.clinicbackend.cms.repository.AboutRepository;
import com.sokhasaclinic.clinicbackend.cms.repository.AboutRepository.FacilityRow;
import com.sokhasaclinic.clinicbackend.cms.repository.MediaRepository;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import com.sokhasaclinic.clinicbackend.doctor.repository.DoctorRepository;
import com.sokhasaclinic.clinicbackend.doctor.service.DoctorProfileService;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AboutService {
    private final AboutRepository pages;
    private final DoctorRepository doctors;
    private final DoctorProfileService profiles;
    private final MediaRepository media;
    private final MediaStorage storage;
    private final MediaService images;
    private final MediaCleanupService cleanup;
    private final TransactionTemplate transactions;
    public AboutService(AboutRepository pages, DoctorRepository doctors, DoctorProfileService profiles,
            MediaRepository media, MediaStorage storage, MediaService images, MediaCleanupService cleanup,
            TransactionTemplate transactions) {
        this.pages=pages; this.doctors=doctors; this.profiles=profiles; this.media=media;
        this.storage=storage; this.images=images; this.cleanup=cleanup; this.transactions=transactions;
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Versioned admin() { return snapshot(); }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public PublicPage publicPage(Language lang) {
        Admin a=snapshot().data(); String code=lang.code();
        PageText t=translation(a.translations(), PageText::languageCode, code);
        var publicDoctors=profiles.publicDoctors(lang);
        var founders=a.founders().stream().filter(Founder::publicProfile).flatMap(f -> {
            String slug=doctors.findById(f.doctorId()).orElseThrow().getSlug();
            return publicDoctors.stream().filter(d -> d.slug().equals(slug)).map(d -> new PublicFounder(
                    d.slug(), d.fullName(), d.title(), d.bio(), d.tags(), d.imageUrl(), d.imageAltText()));
        }).toList();
        return new PublicPage(t.heroTitle(), t.heroSubtitle(), t.storyTitle(), paragraphs(t.storyBody()),
                a.pillars().stream().map(p -> {
                    PillarText text=translation(p.translations(), PillarText::languageCode, code);
                    return new PublicPillar(p.type(), text.title(), text.text(), text.note());
                }).toList(), a.figures().stream().map(f -> new PublicFigure(f.value(),
                        translation(f.translations(), FigureText::languageCode, code).label())).toList(), founders,
                a.facilities().stream().map(f -> {
                    FacilityText text=translation(f.translations(), FacilityText::languageCode, code);
                    return new PublicFacility(text.title(), text.description(), text.badge(), f.imageUrl(),
                            f.imageUrl()==null ? null : text.imageAltText());
                }).toList());
    }

    public Versioned save(String etag, Update sent) {
        long version=parseVersion(etag);
        List<String> removed=new ArrayList<>();
        Versioned result=transactions.execute(status -> {
            lock(version);
            Update clean=validate(sent);
            List<UUID> kept=clean.facilities().stream().map(FacilityWrite::id).toList();
            List<UUID> oldMedia=pages.facilities().stream().filter(f -> !kept.contains(f.id()))
                    .map(FacilityRow::mediaId).filter(Objects::nonNull).toList();
            pages.replace(clean);
            for (UUID id : oldMedia) removed.add(removeMedia(id));
            pages.touch();
            return snapshot();
        });
        removed.stream().filter(Objects::nonNull).forEach(images::deleteUnreferenced);
        return result;
    }

    public Versioned replaceImage(UUID id, String etag, MultipartFile image, List<AltText> sent) {
        long version=parseVersion(etag);
        List<AltText> alts;
        try {
            alts=translations(sent, AltText::languageCode, t -> new AltText(t.languageCode(),
                    required(t.imageAltText(),200,"altTexts.imageAltText")), "altTexts");
        } catch (CmsException e) {
            if (e.getStatus()==409) throw CmsException.invalid("altTexts needs exactly one entry each for km, en and zh.");
            throw e;
        }
        transactions.executeWithoutResult(status -> { lock(version); facility(id); });
        CmsMedia uploaded=images.store(image);
        ImageChange change;
        try {
            change=transactions.execute(status -> {
                lock(version); FacilityRow old=facility(id);
                media.saveAndFlush(uploaded);
                pages.image(id, uploaded.getId(), alts);
                String key=removeMedia(old.mediaId());
                pages.touch();
                return new ImageChange(snapshot(),key);
            });
        } catch (RuntimeException e) { images.discard(uploaded); throw e; }
        cleanup.complete(uploaded.getObjectKey());
        if (change.key()!=null) images.deleteUnreferenced(change.key());
        return change.page();
    }

    public Versioned removeImage(UUID id, String etag) {
        long version=parseVersion(etag);
        ImageChange change=transactions.execute(status -> {
            lock(version); FacilityRow old=facility(id);
            if (old.mediaId()==null) return new ImageChange(snapshot(),null);
            pages.image(id,null,List.of()); String key=removeMedia(old.mediaId()); pages.touch();
            return new ImageChange(snapshot(),key);
        });
        if (change.key()!=null) images.deleteUnreferenced(change.key());
        return change.page();
    }
    private record ImageChange(Versioned page, String key) { }
    private void lock(long version) {
        if (pages.state(true).version()!=version)
            throw new CmsException(412,"VERSION_MISMATCH","Reload the About page before saving.");
    }
    private FacilityRow facility(UUID id) {
        return pages.facilities().stream().filter(f -> f.id().equals(id)).findFirst()
                .orElseThrow(() -> new CmsException(404,"NOT_FOUND","Facility not found."));
    }
    private String removeMedia(UUID id) {
        if (id==null) return null;
        CmsMedia old=media.findById(id).orElseThrow();
        media.delete(old); media.flush(); cleanup.scheduleNow(old.getObjectKey()); return old.getObjectKey();
    }
    private Versioned snapshot() {
        var state=pages.state(false);
        List<Facility> facilities=pages.facilities().stream().map(f -> new Facility(f.id(),
                f.mediaId()==null ? null : storage.signedUrl(media.findById(f.mediaId()).orElseThrow().getObjectKey()),
                pages.facilityTexts(f.id()))).toList();
        return new Versioned(new Admin(state.version(),pages.texts(),pages.pillars(),pages.figures(),
                pages.founders(),facilities,state.updatedAt()),state.version());
    }

    private Update validate(Update u) {
        if (u==null) throw CmsException.invalid("An About page is required.");
        List<PageText> texts=translations(u.translations(),PageText::languageCode, t -> {
            String body=requiredText(t.storyBody(),4000,"storyBody");
            if (paragraphs(body).size()>8) throw CmsException.invalid("storyBody allows at most 8 paragraphs.");
            return new PageText(t.languageCode(),requiredText(t.heroTitle(),120,"heroTitle"),
                    requiredText(t.heroSubtitle(),300,"heroSubtitle"),requiredText(t.storyTitle(),120,"storyTitle"),body);
        },"translations");
        if (u.pillars()==null || u.pillars().size()!=3) throw CmsException.invalid("Exactly three pillars are required.");
        List<Pillar> pillars=new ArrayList<>();
        var types=List.of("MISSION","VISION","VALUES");
        for (int i=0;i<3;i++) {
            Pillar p=u.pillars().get(i);
            if (p==null || !types.get(i).equals(p.type())) throw CmsException.invalid("Pillars must be MISSION, VISION, VALUES in order.");
            pillars.add(new Pillar(p.type(),translations(p.translations(),PillarText::languageCode,
                    t -> new PillarText(t.languageCode(),requiredText(t.title(),80,"pillar.title"),
                            requiredText(t.text(),500,"pillar.text"),optional(t.note(),120,"pillar.note")),"pillars.translations")));
        }
        List<Figure> figures=new ArrayList<>();
        for (Figure f : list(u.figures(),6,"figures")) figures.add(new Figure(required(f.value(),20,"figure.value"),
                translations(f.translations(),FigureText::languageCode,
                        t -> new FigureText(t.languageCode(),requiredText(t.label(),60,"figure.label")),"figures.translations")));
        List<FounderWrite> founders=list(u.founders(),4,"founders"); Set<UUID> ids=new HashSet<>();
        for (int i=0;i<founders.size();i++) {
            UUID id=founders.get(i).doctorId();
            if (id==null || !ids.add(id)) throw CmsException.invalid("Founder doctor ids must be distinct.");
            var doctor=doctors.findForUpdate(id).orElse(null);
            if (doctor==null || doctor.getSlug()==null || !profiles.publicDoctorId(doctor.getSlug()).filter(id::equals).isPresent())
                throw new CmsException(404,"DOCTOR_NOT_FOUND","Choose an active public doctor.",
                        List.of(Map.of("field","founders["+i+"]")));
        }
        Map<UUID,FacilityRow> existing=new HashMap<>(); pages.facilities().forEach(f -> existing.put(f.id(),f));
        Set<UUID> seen=new HashSet<>(); List<FacilityWrite> facilities=new ArrayList<>();
        for (FacilityWrite f : list(u.facilities(),8,"facilities")) {
            if (f.id()!=null && (!existing.containsKey(f.id()) || !seen.add(f.id())))
                throw CmsException.invalid("Facility ids must be distinct existing ids.");
            boolean hasImage=f.id()!=null && existing.get(f.id()).mediaId()!=null;
            facilities.add(new FacilityWrite(f.id()==null ? UUID.randomUUID() : f.id(),
                    translations(f.translations(),FacilityText::languageCode,t -> {
                        String alt=optional(t.imageAltText(),200,"facilities.translations.imageAltText");
                        if (hasImage && alt==null || !hasImage && alt!=null)
                            throw CmsException.invalid("facilities.translations: alt text is required only while there is an image.");
                        return new FacilityText(t.languageCode(),requiredText(t.title(),120,"facility.title"),
                                requiredText(t.description(),500,"facility.description"),optional(t.badge(),40,"facility.badge"),alt);
                    },"facilities.translations")));
        }
        return new Update(texts,pillars,figures,founders,facilities);
    }
    static List<String> paragraphs(String text) { return Arrays.stream(text.split("\\R[\\t ]*\\R(?:[\\t ]*\\R)*")).map(String::strip).filter(s -> !s.isEmpty()).toList(); }
    private static <T> List<T> list(List<T> items,int max,String field) {
        if (items==null || items.size()>max || items.stream().anyMatch(Objects::isNull))
            throw CmsException.invalid(field+" is required and allows at most "+max+" items.");
        return List.copyOf(items);
    }
    private static <T> List<T> translations(List<T> input, Function<T,String> code, Function<T,T> clean,String field) {
        Map<Language,T> values=new EnumMap<>(Language.class);
        if (input!=null) for (T t : input) {
            if (t==null) throw CmsException.invalid(field+" must not contain null entries.");
            Language lang=Language.fromCode(code.apply(t)).orElseThrow(() -> CmsException.invalid("Invalid languageCode."));
            if (values.put(lang,t)!=null) throw CmsException.invalid("Duplicate languageCode.");
        }
        Set<Language> missing=EnumSet.allOf(Language.class); missing.removeAll(values.keySet());
        if (!missing.isEmpty()) throw new CmsException(409,"TRANSLATION_MISSING","Add every language before saving.",
                missing.stream().<Map<String,Object>>map(l -> Map.of("field",field,"languageCode",l.code())).toList());
        return values.values().stream().map(clean).toList();
    }
    private static <T> T translation(List<T> list,Function<T,String> code,String lang) {
        return list.stream().filter(t -> lang.equals(code.apply(t))).findFirst().orElseThrow();
    }
    private static String required(String text,int max,String field) {
        String s=text==null ? "" : text.strip();
        if (s.isEmpty() || s.codePointCount(0,s.length())>max) throw CmsException.invalid(field+" must be 1-"+max+" characters.");
        return s;
    }
    private static String optional(String s,int max,String field) { return s==null || s.isBlank() ? null : required(s,max,field); }
    private static String requiredText(String text,int max,String field) {
        if (text==null || text.isBlank()) throw new CmsException(409,"TRANSLATION_MISSING",
                "Add every language before saving.",List.of(Map.of("field",field)));
        return required(text,max,field);
    }
    private static long parseVersion(String etag) {
        if (etag==null || etag.isBlank()) throw CmsException.versionRequired();
        if (!etag.trim().matches("\"(0|[1-9][0-9]{0,17})\"")) throw CmsException.invalid("Invalid If-Match.");
        return Long.parseLong(etag.trim().replace("\"",""));
    }
}

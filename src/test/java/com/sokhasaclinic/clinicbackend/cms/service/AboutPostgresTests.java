package com.sokhasaclinic.clinicbackend.cms.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.sokhasaclinic.clinicbackend.cms.dto.AboutDtos.*;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="scheduling.enabled=false")
@EnabledIfEnvironmentVariable(named="CLINIC_TEST_DATABASE_URL",matches="jdbc:postgresql:.+")
@Transactional
class AboutPostgresTests {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->System.getenv("CLINIC_TEST_DATABASE_URL"));
        r.add("spring.datasource.username",()->System.getenv("CLINIC_TEST_DATABASE_USERNAME"));
        r.add("spring.datasource.password",()->System.getenv("CLINIC_TEST_DATABASE_PASSWORD"));
    }
    @Autowired AboutService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MediaStorage storage;
    @BeforeEach void setup() {
        reset(storage);
        when(storage.signedUrl(anyString())).thenAnswer(c -> "https://r2.test/"+c.getArgument(0));
    }
    private String etag() { return "\""+service.admin().version()+"\""; }
    private Update update() {
        Admin a=service.admin().data();
        return new Update(a.translations(),a.pillars(),a.figures(),
                a.founders().stream().map(f -> new FounderWrite(f.doctorId())).toList(),
                a.facilities().stream().map(f -> new FacilityWrite(f.id(),f.translations())).toList());
    }
    private void error(int status,String type,Runnable action) {
        CmsException e=assertThrows(CmsException.class,action::run); assertEquals(status,e.getStatus()); assertEquals(type,e.getType());
    }
    @Test void seedsEveryLanguageAndReleaseDecisions() {
        for (Language l : Language.values()) {
            PublicPage page=service.publicPage(l);
            assertFalse(page.heroTitle().isBlank()); assertEquals(3,page.storyParagraphs().size());
            assertEquals(3,page.pillars().size()); assertEquals(4,page.figures().size());
            assertEquals(4,page.facilities().size()); assertTrue(page.founders().isEmpty());
            assertTrue(page.facilities().stream().allMatch(f -> f.imageUrl()==null && f.imageAltText()==null));
        }
        assertEquals("Advancing Healthcare, Improving Lives.",service.publicPage(Language.EN).heroTitle());
    }
    @Test void savesAllLanguagesAtomicallyAndSplitsParagraphs() {
        Update u=update(); String old=etag();
        var texts=u.translations().stream().map(t -> new PageText(t.languageCode()," Updated ",t.heroSubtitle(),
                t.storyTitle()," First paragraph\r\n \r\nSecond paragraph ")).toList();
        Versioned result=service.save(old,new Update(texts,u.pillars(),u.figures(),u.founders(),u.facilities()));
        assertEquals(result.version(),result.data().version()); assertNotEquals(old,etag());
        assertEquals("Updated",service.publicPage(Language.ZH).heroTitle());
        assertEquals(List.of("First paragraph","Second paragraph"),service.publicPage(Language.EN).storyParagraphs());
        error(412,"VERSION_MISMATCH",()->service.save(old,u));
        error(428,"VERSION_REQUIRED",()->service.save(null,u));
    }
    @Test void missingLanguageChangesNothing() {
        Update u=update(); String old=etag();
        error(409,"TRANSLATION_MISSING",()->service.save(old,new Update(u.translations().subList(0,2),u.pillars(),u.figures(),u.founders(),u.facilities())));
        assertEquals(old,etag());
        var texts=new ArrayList<>(u.translations()); PageText t=texts.getFirst();
        texts.set(0,new PageText(t.languageCode(),t.heroTitle(),t.heroSubtitle(),t.storyTitle()," "));
        error(409,"TRANSLATION_MISSING",()->service.save(old,new Update(texts,u.pillars(),u.figures(),u.founders(),u.facilities())));
        assertEquals(old,etag());
    }
    @Test void rejectsInvalidCollectionsAndForeignFacilityIds() {
        Update u=update();
        error(400,"VALIDATION_ERROR",()->service.save(etag(),new Update(u.translations(),u.pillars().reversed(),u.figures(),u.founders(),u.facilities())));
        error(400,"VALIDATION_ERROR",()->service.save(etag(),new Update(u.translations(),u.pillars(),u.figures(),u.founders(),List.of(new FacilityWrite(UUID.randomUUID(),u.facilities().getFirst().translations())))));
        UUID id=UUID.randomUUID();
        error(404,"DOCTOR_NOT_FOUND",()->service.save(etag(),new Update(u.translations(),u.pillars(),u.figures(),List.of(new FounderWrite(id)),u.facilities())));
    }
    @Test void foundersFollowProfileVisibilityAndOrder() {
        UUID first=doctor("First"),second=doctor("Second"); Update u=update();
        service.save(etag(),new Update(u.translations(),u.pillars(),u.figures(),List.of(new FounderWrite(second),new FounderWrite(first)),u.facilities()));
        assertEquals(List.of("Second","First"),service.publicPage(Language.EN).founders().stream().map(PublicFounder::fullName).toList());
        jdbc.update("update clinic.doctor set status='INACTIVE' where id=?",second);
        assertEquals(List.of("First"),service.publicPage(Language.KM).founders().stream().map(PublicFounder::fullName).toList());
        assertFalse(service.admin().data().founders().getFirst().publicProfile());
        CmsException e=assertThrows(CmsException.class,()->service.save(etag(),update()));
        assertEquals("founders[0]",e.getErrors().getFirst().get("field"));
    }
    @Test void addsReordersAndRemovesFacilities() {
        Update u=update(); var facilities=new ArrayList<>(u.facilities());
        facilities.addFirst(new FacilityWrite(null,u.facilities().getFirst().translations()));
        Versioned saved=service.save(etag(),new Update(u.translations(),u.pillars(),List.of(),List.of(),facilities));
        UUID id=saved.data().facilities().getFirst().id(); assertNotNull(id);
        assertEquals(5,saved.data().facilities().size());
        u=update();
        service.save(etag(),new Update(u.translations(),u.pillars(),u.figures(),u.founders(),u.facilities().subList(1,5)));
        assertFalse(service.admin().data().facilities().stream().anyMatch(f -> f.id().equals(id)));
    }
    @Test void imagesRequireAllAltsAndDeleteWithTheirFacility() throws Exception {
        UUID id=service.admin().data().facilities().getFirst().id();
        var alts=List.of(new AltText("km","មន្ទីរពេទ្យ"),new AltText("en","Clinic"),new AltText("zh","诊所"));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);
        var image=new MockMultipartFile("image","clinic.png","image/png",bytes.toByteArray());
        error(400,"VALIDATION_ERROR",()->service.replaceImage(id,etag(),image,alts.subList(0,2)));
        service.replaceImage(id,etag(),image,alts);
        assertEquals("Clinic",service.publicPage(Language.EN).facilities().getFirst().imageAltText());
        Update u=update();
        service.save(etag(),new Update(u.translations(),u.pillars(),u.figures(),u.founders(),u.facilities().subList(1,u.facilities().size())));
        verify(storage).delete(anyString());
        error(404,"NOT_FOUND",()->service.removeImage(id,etag()));
    }
    @Test void rejectsOversizedAndUnsupportedImages() {
        UUID id=service.admin().data().facilities().getFirst().id();
        var alts=List.of(new AltText("km","Photo"),new AltText("en","Photo"),new AltText("zh","Photo"));
        error(413,"IMAGE_TOO_LARGE",()->service.replaceImage(id,etag(),new MockMultipartFile("image","x.png","image/png",new byte[5*1024*1024+1]),alts));
        error(415,"UNSUPPORTED_IMAGE",()->service.replaceImage(id,etag(),new MockMultipartFile("image","x.png","image/png",new byte[]{1,2,3}),alts));
        verify(storage,never()).upload(anyString(),anyString(),any());
    }
    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentSavesCannotOverwriteEachOther() throws Exception {
        Update original=update(); String version=etag();
        var start=new java.util.concurrent.CountDownLatch(1);
        try (var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> save=()-> {
                start.await();
                try { service.save(version,original); return 200; }
                catch (CmsException e) { return e.getStatus(); }
            };
            var first=pool.submit(save); var second=pool.submit(save); start.countDown();
            assertEquals(List.of(200,412),java.util.stream.Stream.of(
                    first.get(15,java.util.concurrent.TimeUnit.SECONDS),
                    second.get(15,java.util.concurrent.TimeUnit.SECONDS)).sorted().toList());
        }
    }
    private UUID doctor(String name) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into clinic.doctor (id,full_name,status,sort_order,slug) values (?,?,'ACTIVE',0,?)",id,name,"about-"+id);
        for (String lang : List.of("km","en","zh")) jdbc.update("insert into clinic.doctor_translation (doctor_id,language_code,title) values (?,?,?)",id,lang,"Founder "+name);
        return id;
    }
}

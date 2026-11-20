-- About page CMS (AB-01 to AB-14), approved November 20, 2026.
CREATE TABLE clinic.about_page (id smallint PRIMARY KEY CHECK (id=1), version bigint NOT NULL DEFAULT 0, updated_at timestamptz NOT NULL DEFAULT now());
INSERT INTO clinic.about_page (id) VALUES (1);
CREATE TABLE clinic.about_page_translation (
 language_code varchar(2) PRIMARY KEY CHECK (language_code IN ('km','en','zh')),
 hero_title varchar(120) NOT NULL, hero_subtitle varchar(300) NOT NULL,
 story_title varchar(120) NOT NULL, story_body varchar(4000) NOT NULL);
CREATE TABLE clinic.about_pillar_translation (
 type varchar(7) NOT NULL CHECK (type IN ('MISSION','VISION','VALUES')),
 language_code varchar(2) NOT NULL CHECK (language_code IN ('km','en','zh')),
 title varchar(80) NOT NULL, text varchar(500) NOT NULL, note varchar(120), PRIMARY KEY(type,language_code));
CREATE TABLE clinic.about_figure (id uuid PRIMARY KEY, sort_order int NOT NULL, value varchar(20) NOT NULL);
CREATE TABLE clinic.about_figure_translation (
 figure_id uuid NOT NULL REFERENCES clinic.about_figure ON DELETE CASCADE,
 language_code varchar(2) NOT NULL CHECK (language_code IN ('km','en','zh')),
 label varchar(60) NOT NULL, PRIMARY KEY(figure_id,language_code));
CREATE TABLE clinic.about_founder (doctor_id uuid PRIMARY KEY REFERENCES clinic.doctor ON DELETE RESTRICT, sort_order int NOT NULL);
CREATE TABLE clinic.about_facility (id uuid PRIMARY KEY, sort_order int NOT NULL, image_media_id uuid REFERENCES clinic.cms_media);
CREATE INDEX about_facility_image_idx ON clinic.about_facility(image_media_id);
CREATE TABLE clinic.about_facility_translation (
 facility_id uuid NOT NULL REFERENCES clinic.about_facility ON DELETE CASCADE,
 language_code varchar(2) NOT NULL CHECK (language_code IN ('km','en','zh')),
 title varchar(120) NOT NULL, description varchar(500) NOT NULL, badge varchar(40), image_alt_text varchar(200),
 PRIMARY KEY(facility_id,language_code));
-- Founders and photos are configured by the admin after release.
INSERT INTO clinic.about_page_translation VALUES ('km', 'លើកកម្ពស់វិស័យសុខាភិបាល លើកកម្ពស់គុណភាពជីវិត', 'ក្រុមហ៊ុន សុខាសា ហេលស៍ ខូអិលធីឌី ប្តេជ្ញាផ្លាស់ប្តូរ និងលើកកម្ពស់ឧត្តមភាពវេជ្ជសាស្ត្រ ឱសថកោសិកាច្បាស់លាស់ សុខភាពមាតា និងការព្យាបាលអាយុយឺនយូរនៅកម្ពុជា។', 'ការផ្លាស់ប្តូរវិស័យសុខាភិបាលនៅកម្ពុជា តាមរយៈនវានុវត្តន៍ និងការយកចិត្តទុកដាក់', 'ក្រុមហ៊ុន សុខាសា ហេលស៍ ខូអិលធីឌី (Sokhasa Health Co., Ltd.) ត្រូវបានបង្កើតឡើងដោយ វេជ្ជបណ្ឌិត ផេង បូរ៉ា និង វេជ្ជបណ្ឌិត យ៉ង ម៉ាលីសដែត ក្នុងគោលបំណងលើកកម្ពស់ស្តង់ដារវេជ្ជសាស្ត្រនៅកម្ពុជា តាមរយៈការរួមបញ្ចូលគ្នានូវបច្ចេកវិទ្យាជីវវេជ្ជសាស្ត្រទំនើប ឱសថកែប្រែហ្សែន និងការព្យាបាលប្រកបដោយគុណភាពខ្ពស់។

ក្រោមឆ័ត្ររបស់ សុខាសា យើងដំណើរការមន្ទីរសម្រាកព្យាបាលឯកទេសជាច្រើននៅរាជធានីភ្នំពេញ រួមមាន មន្ទីរសម្រាកព្យាបាលនិងសម្ភព សុខាសា (Sokhasa Longevity Clinic & Maternity) មជ្ឈមណ្ឌលជំងឺឈាម និងមហារីក (Hemato-Onco Clinic) និងផ្នែកវេជ្ជសាស្ត្របង្កើតឡើងវិញ (Regenerative Clinic)។

បាវចនារបស់យើង "ផ្តល់ជូនអ្នកនូវវេជ្ជបណ្ឌិតល្អបំផុត សម្រាប់ការថែទាំល្អបំផុត" គឺជាត្រីវិស័យដែលដឹកនាំគ្រប់ការពិគ្រោះយោបល់ ការធ្វើតេស្តមន្ទីរពិសោធន៍ និងការព្យាបាលនីមួយៗ ស្របតាមស្តង់ដារគុណភាពអន្តរជាតិ។');
INSERT INTO clinic.about_pillar_translation VALUES ('MISSION', 'km', 'បេសកកម្មរបស់យើង', 'ប្តេជ្ញាផ្លាស់ប្តូរវិស័យសុខាភិបាលនៅកម្ពុជា តាមរយៈដំណោះស្រាយឱសថទំនើបៗ ការព្យាបាលដោយកោសិកាកែប្រែហ្សែន និងការធ្វើរោគវិនិច្ឆ័យច្បាស់លាស់។', 'Modern Therapeutics & Cellular Protocols');
INSERT INTO clinic.about_pillar_translation VALUES ('VISION', 'km', 'ចក្ខុវិស័យរបស់យើង', 'ក្លាយជាស្ថាប័នគំរូនៅអាស៊ីអាគ្នេយ៍ លើផ្នែកសុខុមាលភាពកោសិកា ឱសថបង្កើតឡើងវិញ និងការពន្យារអាយុយឺនយូរ ផ្តល់ការថែទាំស្តង់ដារពិភពលោកនៅកម្ពុជា។', 'Regional Benchmark in Precision Longevity');
INSERT INTO clinic.about_pillar_translation VALUES ('VALUES', 'km', 'គុណតម្លៃរបស់យើង', 'ប្រកាន់ខ្ជាប់នូវភាពច្បាស់លាស់ខាងវេជ្ជសាស្ត្រ ការយកចិត្តទុកដាក់ចំពោះអ្នកជំងឺ វិទ្យាសាស្ត្រម៉ត់ចត់ និងស្តង់ដារសុវត្ថិភាពខ្ពស់បំផុត។', 'Zero Compromise on Patient Safety');
INSERT INTO clinic.about_page_translation VALUES ('en', 'Advancing Healthcare, Improving Lives.', 'Sokhasa Health Co., Ltd. is dedicated to transforming clinical excellence, precision cellular medicine, maternal health, and longevity therapies in Cambodia.', 'Transforming Healthcare in Cambodia Through Innovation & Care', 'Founded by Dr. Bora Pheng and Dr. Malisadet Yong, Sokhasa Health Co., Ltd. was established to revolutionize modern clinical healthcare in Cambodia. Recognizing that Cambodian patients deserved seamless access to world-class precision diagnostics and advanced therapies, the founders created an integrated clinical ecosystem dedicated to excellence.

Operating specialized centers across Phnom Penh—including Sokhasa Longevity Clinic & Maternity, our Hemato Onco Cancer Center, and specialized Regenerative Clinic facilities—Sokhasa bridges laboratory science with compassionate bedside care.

Our guiding motto, "Providing you with the best doctors for the best care," underscores our uncompromising commitment to scientific precision, medical ethics, patient dignity, and clinical results.');
INSERT INTO clinic.about_pillar_translation VALUES ('MISSION', 'en', 'Our Mission', 'Dedicated to transforming healthcare in Cambodia through cutting-edge pharmaceutical solutions, gene-modified cell therapies, and precision clinical diagnostics.', 'Modern Therapeutics & Cellular Protocols');
INSERT INTO clinic.about_pillar_translation VALUES ('VISION', 'en', 'Our Vision', 'To serve as Southeast Asia’s benchmark institution in cellular wellness, regenerative medicine, and preventative longevity—delivering world-class healthcare right here in Cambodia.', 'Regional Benchmark in Precision Longevity');
INSERT INTO clinic.about_pillar_translation VALUES ('VALUES', 'en', 'Our Values', 'Guided by clinical precision, patient-first empathy, scientific rigor, and uncompromising safety standards across every consultation and procedure.', 'Zero Compromise on Patient Safety');
INSERT INTO clinic.about_page_translation VALUES ('zh', '推动医疗进步，赋能生命健康', 'Sokhasa Health Co., Ltd. 致力于在柬埔寨推动临床卓越质控、精准细胞医学、母婴健康与个体化抗衰长寿前沿疗法。', '以创新生物医学与医者仁心，重塑柬埔寨高标准医疗体验', 'Sokhasa Health Co., Ltd.（索卡萨健康有限公司）由 Dr. PHENG Bora 与 Dr. YONG Malisadet 共同创立。创立初心源自于对柬埔寨本土医疗水平升级的深切使命感：通过引进国际尖端基因修饰细胞技术、前沿药物管线与现代母胎医学，改变以往患者重大疾病只能远赴海外就医的困境。

在索卡萨健康生态体系下，机构在金边设立并运营多个重点专科临床分支，包括：索卡萨长寿医学与母产中心（Sokhasa Longevity Clinic & Maternity）、血液与实体肿瘤医学中心（Hemato Onco Cancer Clinic）以及细胞再生医学中心（Regenerative Clinic）。

正如我们的核心院训所昭示——“汇聚良医，守护至珍生命”（Providing you with the best doctors for the best care），索卡萨全体医护以ISO 15189国际标准检验为依据，全心守护每一个家庭的健康福祉。');
INSERT INTO clinic.about_pillar_translation VALUES ('MISSION', 'zh', '我们的使命', '致力于通过前沿药物解决方案、基因修饰细胞疗法与高精密度临床检验，持续提升柬埔寨整体医疗健康服务水平。', 'Modern Therapeutics & Cellular Protocols');
INSERT INTO clinic.about_pillar_translation VALUES ('VISION', 'zh', '我们的愿景', '打造东南亚细胞抗衰健康、再生医学与预防性长寿医学的典范机构，让国际一流医疗创新在柬埔寨触手可及。', 'Regional Benchmark in Precision Longevity');
INSERT INTO clinic.about_pillar_translation VALUES ('VALUES', 'zh', '核心价值观', '以精准医学为根基、以患者福祉为中心，秉持严谨求实与极致安全标准，深耕每一位患者的全程诊疗。', 'Zero Compromise on Patient Safety');
INSERT INTO clinic.about_figure VALUES ('ab000000-0000-4000-8000-000000000001', '0', '6');
INSERT INTO clinic.about_figure_translation VALUES ('ab000000-0000-4000-8000-000000000001', 'km', 'Clinical specialties');
INSERT INTO clinic.about_figure_translation VALUES ('ab000000-0000-4000-8000-000000000001', 'en', 'Clinical specialties');
INSERT INTO clinic.about_figure_translation VALUES ('ab000000-0000-4000-8000-000000000001', 'zh', 'Clinical specialties');
INSERT INTO clinic.about_figure VALUES ('ab000000-0000-4000-8000-000000000002', '1', '2');
INSERT INTO clinic.about_figure_translation VALUES ('ab000000-0000-4000-8000-000000000002', 'km', 'Branches in Phnom Penh');
INSERT INTO clinic.about_figure_translation VALUES ('ab000000-0000-4000-8000-000000000002', 'en', 'Branches in Phnom Penh');
INSERT INTO clinic.about_figure_translation VALUES ('ab000000-0000-4000-8000-000000000002', 'zh', 'Branches in Phnom Penh');
INSERT INTO clinic.about_figure VALUES ('ab000000-0000-4000-8000-000000000003', '2', '20K+');
INSERT INTO clinic.about_figure_translation VALUES ('ab000000-0000-4000-8000-000000000003', 'km', 'Patients cared for');
INSERT INTO clinic.about_figure_translation VALUES ('ab000000-0000-4000-8000-000000000003', 'en', 'Patients cared for');
INSERT INTO clinic.about_figure_translation VALUES ('ab000000-0000-4000-8000-000000000003', 'zh', 'Patients cared for');
INSERT INTO clinic.about_figure VALUES ('ab000000-0000-4000-8000-000000000004', '3', 'ISO 15189');
INSERT INTO clinic.about_figure_translation VALUES ('ab000000-0000-4000-8000-000000000004', 'km', 'Certified laboratory');
INSERT INTO clinic.about_figure_translation VALUES ('ab000000-0000-4000-8000-000000000004', 'en', 'Certified laboratory');
INSERT INTO clinic.about_figure_translation VALUES ('ab000000-0000-4000-8000-000000000004', 'zh', 'Certified laboratory');
INSERT INTO clinic.about_facility VALUES ('ab100000-0000-4000-8000-000000000001', '0', NULL);
INSERT INTO clinic.about_facility_translation VALUES ('ab100000-0000-4000-8000-000000000001', 'km', 'មន្ទីរសម្រាកព្យាបាល និងសម្ភព សុខាសា', 'អគារវេជ្ជសាស្ត្រទំនើបនៅខណ្ឌសែនសុខ បំពាក់ដោយបន្ទប់ពិគ្រោះយោបល់ឯកជន បន្ទប់សម្រាក និងកន្លែងរង់ចាំប្រកបដោយផាសុកភាព។', 'ទីស្នាក់ការកណ្តាល', NULL);
INSERT INTO clinic.about_facility_translation VALUES ('ab100000-0000-4000-8000-000000000001', 'en', 'Sokhasa Longevity Clinic & Maternity Center', 'Modern multi-story medical facility in Sen Sok featuring independent outpatient consultation zones, patient privacy suites, and comfortable waiting lounges.', 'Main Headquarters', NULL);
INSERT INTO clinic.about_facility_translation VALUES ('ab100000-0000-4000-8000-000000000001', 'zh', '索卡萨高端综合门诊与妇产中心', '坐落于金边森速区，拥有独立门诊问诊区、温馨私密病房以及舒适便捷的患者就诊动线。', '核心院区', NULL);
INSERT INTO clinic.about_facility VALUES ('ab100000-0000-4000-8000-000000000002', '1', NULL);
INSERT INTO clinic.about_facility_translation VALUES ('ab100000-0000-4000-8000-000000000002', 'km', 'មន្ទីរពិសោធន៍វេជ្ជសាស្ត្រស្តង់ដារ ISO 15189', 'បំពាក់ដោយម៉ាស៊ីនវិភាគឈាមស្វ័យប្រវត្ត ប្រព័ន្ធតេស្ត PCR និងការគ្រប់គ្រងសំណាកជីវសាស្ត្រតាមស្តង់ដារអន្តរជាតិ។', 'មន្ទីរពិសោធន៍', NULL);
INSERT INTO clinic.about_facility_translation VALUES ('ab100000-0000-4000-8000-000000000002', 'en', 'ISO 15189 Certified Pathology & Genetic Laboratory', 'Equipped with automated hematology analyzers, real-time PCR suites, flow cytometry, and strict temperature-controlled biological sample archives.', 'Diagnostic Laboratory', NULL);
INSERT INTO clinic.about_facility_translation VALUES ('ab100000-0000-4000-8000-000000000002', 'zh', 'ISO 15189认可临床病理与分子遗传实验室', '配置全自动血液细胞分析仪、实时荧光定量PCR仪、流式细胞仪与严格温控的生物样本冷链保存体系。', '精准检验科', NULL);
INSERT INTO clinic.about_facility VALUES ('ab100000-0000-4000-8000-000000000003', '2', NULL);
INSERT INTO clinic.about_facility_translation VALUES ('ab100000-0000-4000-8000-000000000003', 'km', 'បន្ទប់អេកូសាស្ត្រទំនើបកម្រិត 4D', 'ម៉ាស៊ីនអេកូទំនើបកម្រិតច្បាស់ខ្ពស់ សម្រាប់ការពិនិត្យគភ៌ លក្ខខណ្ឌរោគស្ត្រី និងសរីរាង្គក្នុងពោះយ៉ាងលម្អិត។', 'រូបភាពវេជ្ជសាស្ត្រ', NULL);
INSERT INTO clinic.about_facility_translation VALUES ('ab100000-0000-4000-8000-000000000003', 'en', 'High-Resolution 4D Ultrasound & Imaging Suite', 'Advanced Voluson HD multi-probe ultrasound for high-definition fetal anomaly screening, gynecological doppler, and abdominal organ mapping.', 'Imaging & Diagnostics', NULL);
INSERT INTO clinic.about_facility_translation VALUES ('ab100000-0000-4000-8000-000000000003', 'zh', '高清4D彩色多普勒超声影像诊断中心', '配备高端多探头超声影像系统，全面支持早孕NT排畸、高清胎儿结构四维筛查、妇科血流动力学及脏器扫描。', '影像超声科', NULL);
INSERT INTO clinic.about_facility VALUES ('ab100000-0000-4000-8000-000000000004', '3', NULL);
INSERT INTO clinic.about_facility_translation VALUES ('ab100000-0000-4000-8000-000000000004', 'km', 'បន្ទប់ព្យាបាលដោយការចាក់បញ្ចូលកោសិកា និងគីមី', 'រចនាឡើងជាមួយប្រព័ន្ធចម្រោះខ្យល់ HEPA ឧបករណ៍តាមដានបេះដូង និងកៅអីសម្រាកប្រកបដោយផាសុកភាពនិងសុវត្ថិភាព។', 'បន្ទប់ចាក់បញ្ចូល', NULL);
INSERT INTO clinic.about_facility_translation VALUES ('ab100000-0000-4000-8000-000000000004', 'en', 'Sterile Day-Stay Chemotherapy & Cellular Infusion Suite', 'Engineered with positive pressure HEPA filtration, emergency cardiac monitoring, and ergonomic recliners for patient dignity and safety.', 'Sterile Infusion Suite', NULL);
INSERT INTO clinic.about_facility_translation VALUES ('ab100000-0000-4000-8000-000000000004', 'zh', '无菌日间细胞输注与化学治疗中心', '配备正压洁净空气流净化系统、动态心电监护设备及人体工学输注躺椅，为患者提供有尊严、安全的输注环境。', '治疗输注中心', NULL);

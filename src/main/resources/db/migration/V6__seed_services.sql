-- Seed the website's service categories and services (decisions SV-01 and SV-10).
-- Prices and durations are the website's placeholder values until the clinic confirms them.
-- No images are seeded: the website keeps its own images by slug until the service editor exists.

INSERT INTO clinic.service_category (id, slug, sort_order) VALUES (gen_random_uuid(), 'general-internal-medicine', 1);
INSERT INTO clinic.service_category_translation (category_id, language_code, name)
SELECT id, v.language_code, v.name FROM clinic.service_category, (VALUES
    ('km', 'វេជ្ជសាស្ត្រទូទៅ និងជំងឺរ៉ាំរ៉ៃ'),
    ('en', 'General & Internal Medicine'),
    ('zh', '全科与综合内科门诊')
) AS v(language_code, name) WHERE slug = 'general-internal-medicine';

INSERT INTO clinic.service_category (id, slug, sort_order) VALUES (gen_random_uuid(), 'diagnostic-imaging', 2);
INSERT INTO clinic.service_category_translation (category_id, language_code, name)
SELECT id, v.language_code, v.name FROM clinic.service_category, (VALUES
    ('km', 'រូបភាពវេជ្ជសាស្ត្រ និងការធ្វើរោគវិនិច្ឆ័យ'),
    ('en', 'Diagnostic & Imaging'),
    ('zh', '医学影像与超声诊断')
) AS v(language_code, name) WHERE slug = 'diagnostic-imaging';

INSERT INTO clinic.service_category (id, slug, sort_order) VALUES (gen_random_uuid(), 'womens-health-surgery', 3);
INSERT INTO clinic.service_category_translation (category_id, language_code, name)
SELECT id, v.language_code, v.name FROM clinic.service_category, (VALUES
    ('km', 'សុខភាពស្ត្រី និងការវះកាត់តូច'),
    ('en', 'Women''s Health & Surgery'),
    ('zh', '妇科健康与微创手术中心')
) AS v(language_code, name) WHERE slug = 'womens-health-surgery';

INSERT INTO clinic.service_category (id, slug, sort_order) VALUES (gen_random_uuid(), 'cancer-blood-disorders', 4);
INSERT INTO clinic.service_category_translation (category_id, language_code, name)
SELECT id, v.language_code, v.name FROM clinic.service_category, (VALUES
    ('km', 'ជំងឺមហារីក និងជំងឺឈាម'),
    ('en', 'Cancer & Blood Disorders'),
    ('zh', '肿瘤早筛与血液系统专科')
) AS v(language_code, name) WHERE slug = 'cancer-blood-disorders';

INSERT INTO clinic.service_category (id, slug, sort_order) VALUES (gen_random_uuid(), 'laboratory-genetic-testing', 5);
INSERT INTO clinic.service_category_translation (category_id, language_code, name)
SELECT id, v.language_code, v.name FROM clinic.service_category, (VALUES
    ('km', 'មន្ទីរពិសោធន៍ និងការវិភាគហ្សែន'),
    ('en', 'Laboratory & Genetic Testing'),
    ('zh', '医学检验与基因组学中心')
) AS v(language_code, name) WHERE slug = 'laboratory-genetic-testing';

INSERT INTO clinic.service_category (id, slug, sort_order) VALUES (gen_random_uuid(), 'health-checkup-wellness', 6);
INSERT INTO clinic.service_category_translation (category_id, language_code, name)
SELECT id, v.language_code, v.name FROM clinic.service_category, (VALUES
    ('km', 'ការពិនិត្យសុខភាពទូទៅ និងសុខុមាលភាព'),
    ('en', 'Health Check-up & Wellness'),
    ('zh', '贵宾健康体检与长寿医学')
) AS v(language_code, name) WHERE slug = 'health-checkup-wellness';

INSERT INTO clinic.service (id, slug, category_id, duration_minutes, buffer_minutes, price_usd, original_price_usd, status, sort_order)
SELECT gen_random_uuid(), 'chronic-disease-management', id, 30, 0, 35.00, NULL, 'ACTIVE', 1
FROM clinic.service_category WHERE slug = 'general-internal-medicine';
INSERT INTO clinic.service_translation (service_id, language_code, name, short_description)
SELECT id, v.language_code, v.name, v.short_description FROM clinic.service, (VALUES
    ('km', 'ការគ្រប់គ្រងជំងឺរ៉ាំរ៉ៃ និងវេជ្ជសាស្ត្រទូទៅ', 'ការព្យាបាល និងគ្រប់គ្រងជំងឺលើសឈាម ទឹកនោមផ្អែម ខ្លាញ់ក្នុងឈាម និងបញ្ហាមេតាប៉ូលីស។'),
    ('en', 'Chronic Disease Management & Internal Care', 'Targeted protocols for hypertension, type 2 diabetes, hyperlipidemia, and metabolic health.'),
    ('zh', '慢性病综合防治与内科长程管理', '针对高血压、2型糖尿病、高脂血症及代谢综合征的系统化精准调控。')
) AS v(language_code, name, short_description) WHERE slug = 'chronic-disease-management';

INSERT INTO clinic.service (id, slug, category_id, duration_minutes, buffer_minutes, price_usd, original_price_usd, status, sort_order)
SELECT gen_random_uuid(), 'high-resolution-ultrasound', id, 30, 0, 45.00, NULL, 'ACTIVE', 2
FROM clinic.service_category WHERE slug = 'diagnostic-imaging';
INSERT INTO clinic.service_translation (service_id, language_code, name, short_description)
SELECT id, v.language_code, v.name, v.short_description FROM clinic.service, (VALUES
    ('km', 'អេកូសាស្ត្រឌីជីថលកម្រិតច្បាស់ខ្ពស់ ពិនិត្យសរីរាង្គខាងក្នុង', 'ការពិនិត្យពោះ ថ្លើម តម្រងនោម ប្រមាត់ ក្រពេញទីរ៉ូអ៊ីត និងសរីរាង្គអាងត្រគាកដោយសុវត្ថិភាព គ្មានកាំរស្មី។'),
    ('en', 'High-Resolution Digital Ultrasound & Organ Imaging', 'Non-invasive, radiation-free evaluation of abdomen, liver, kidneys, gallbladder, thyroid, and pelvic anatomy.'),
    ('zh', '高分辨数字化腹腔脏器彩超及浅表器官影像', '无辐射无创精细探查：涵盖肝胆脾胰肾、甲状腺微结节、盆腔及大血管病变。')
) AS v(language_code, name, short_description) WHERE slug = 'high-resolution-ultrasound';

INSERT INTO clinic.service (id, slug, category_id, duration_minutes, buffer_minutes, price_usd, original_price_usd, status, sort_order)
SELECT gen_random_uuid(), 'cervical-cancer-hpv-screening', id, 30, 0, 59.00, NULL, 'ACTIVE', 3
FROM clinic.service_category WHERE slug = 'womens-health-surgery';
INSERT INTO clinic.service_translation (service_id, language_code, name, short_description)
SELECT id, v.language_code, v.name, v.short_description FROM clinic.service, (VALUES
    ('km', 'ការពិនិត្យមហារីកមាត់ស្បូន៖ Liquid Pap Smear & HPV DNA', 'ការរួមបញ្ចូលគ្នានៃតេស្តកោសិកា ThinPrep និងការពិនិត្យហ្សែនមេរោគ HPV ហានិភ័យខ្ពស់ ដើម្បីទប់ស្កាត់ជំងឺមហារីកមាត់ស្បូន។'),
    ('en', 'Cervical Cancer Screening: Liquid Pap Smear & HPV DNA', 'Gold-standard co-testing combining ThinPrep liquid cytology and high-risk HPV DNA genotyping to prevent cervical malignancy.'),
    ('zh', '宫颈癌联合筛查：液基细胞学TCT与高危HPV分型', '国际公认金标准双联筛查：超薄液基细胞学检查联合高危型人乳头瘤病毒分型基因检测。')
) AS v(language_code, name, short_description) WHERE slug = 'cervical-cancer-hpv-screening';

INSERT INTO clinic.service (id, slug, category_id, duration_minutes, buffer_minutes, price_usd, original_price_usd, status, sort_order)
SELECT gen_random_uuid(), 'mced-liquid-biopsy', id, 30, 0, 950.00, NULL, 'ACTIVE', 4
FROM clinic.service_category WHERE slug = 'cancer-blood-disorders';
INSERT INTO clinic.service_translation (service_id, language_code, name, short_description)
SELECT id, v.language_code, v.name, v.short_description FROM clinic.service, (VALUES
    ('km', 'តេស្តឈាមស្វែងរកមហារីកដំណាក់កាលដំបូង ctDNA Liquid Biopsy', 'បច្ចេកវិទ្យាតេស្តឈាមទំនើប NGS ពិនិត្យកោសិកា ctDNA ស្វែងរកហានិភ័យមហារីកជាង ៥០ ប្រភេទ ត្រឹមតែបូមឈាមម្តងប៉ុណ្ណោះ។'),
    ('en', 'Multi-Cancer Early Detection (MCED) & ctDNA Liquid Biopsy', 'Next-Generation Sequencing assay detecting circulating tumor DNA (ctDNA) methylation across 50+ cancer types from a single blood draw.'),
    ('zh', '多癌超早期筛查（MCED）与游离DNA液体活检', '抽取一管静脉外周血，通过高通量测序技术深度捕获循环肿瘤游离DNA异常甲基化，早期预警50余种恶性肿瘤。')
) AS v(language_code, name, short_description) WHERE slug = 'mced-liquid-biopsy';

INSERT INTO clinic.service (id, slug, category_id, duration_minutes, buffer_minutes, price_usd, original_price_usd, status, sort_order)
SELECT gen_random_uuid(), 'cellular-mitochondrial-health', id, 45, 0, 250.00, NULL, 'ACTIVE', 5
FROM clinic.service_category WHERE slug = 'laboratory-genetic-testing';
INSERT INTO clinic.service_translation (service_id, language_code, name, short_description)
SELECT id, v.language_code, v.name, v.short_description FROM clinic.service, (VALUES
    ('km', 'តេស្តសុខភាពកោសិកា សមត្ថភាពមីតូកុងឌ្រី ATP និងអាយុកោសិកា Telomere', 'វាស់ថាមពលកោសិកា ATP ប្រវែងកន្ទុយក្រូម៉ូសូម Telomere និងការខូចខាត DNA ដើម្បីគណនាអាយុពិតនៃកោសិកា។'),
    ('en', 'Cellular Health & Mitochondrial ATP / Telomere Assay', 'Quantifies intracellular ATP bioenergetics, telomere repeat length, and oxidative DNA damage to calculate true biological age.'),
    ('zh', '线粒体能量代谢（ATP生成率）与端粒生物学生物钟测定', '定量分析单细胞线粒体三磷酸腺苷（ATP）产能活性、端粒DNA重复序列长度，真实还原机体生物学年龄。')
) AS v(language_code, name, short_description) WHERE slug = 'cellular-mitochondrial-health';

INSERT INTO clinic.service (id, slug, category_id, duration_minutes, buffer_minutes, price_usd, original_price_usd, status, sort_order)
SELECT gen_random_uuid(), 'executive-wellness-checkup', id, 120, 0, 155.00, 199.00, 'ACTIVE', 6
FROM clinic.service_category WHERE slug = 'health-checkup-wellness';
INSERT INTO clinic.service_translation (service_id, language_code, name, short_description)
SELECT id, v.language_code, v.name, v.short_description FROM clinic.service, (VALUES
    ('km', 'កញ្ចប់ពិនិត្យសុខភាពទូទៅកម្រិតខ្ពស់ Executive Check-up', 'ការពិនិត្យសុខភាពគ្រប់ជ្រុងជ្រោយជាង ១២០ តេស្ត រួមទាំងអេកូសរីរាង្គ អេកូបេះដូង និងសញ្ញាសម្គាល់មហារីក។'),
    ('en', 'Comprehensive Executive Health Check-up Package', 'All-inclusive preventive medical assessment spanning 120+ clinical tests, high-definition organ ultrasound, ECG, and oncology markers.'),
    ('zh', '至尊领袖级全方位深度健康体检套餐', '涵盖120余项临床精密检验、多器官高分辨彩超、心电图、肿瘤全谱标志物及专科医师深度闭环评估。')
) AS v(language_code, name, short_description) WHERE slug = 'executive-wellness-checkup';

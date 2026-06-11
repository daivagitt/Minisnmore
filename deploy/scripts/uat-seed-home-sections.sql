-- Idempotent seed for canonical homepage blocks (matches Liquibase 20260528130000 + 20260529120000).
-- payload_json must be plain text (not PostgreSQL OID); use NULL for empty payloads when catalog
-- JAR still maps @Lob as OID, or JSON strings after HomeSection entity uses columnDefinition text.
INSERT INTO home_section (id, section_key, title, subtitle, enabled, sort_order, payload_json)
VALUES
    (nextval('sequence_generator'), 'hero', 'Minis today, memories forever.', 'Heirloom-quality clothing for the first 24 months.', true, 10, NULL),
    (nextval('sequence_generator'), 'by_age', 'Shop by age', 'Sized to baby, not to a guess.', true, 20, NULL),
    (nextval('sequence_generator'), 'best_sellers', 'Best sellers', 'Loved by our families.', true, 30, NULL),
    (nextval('sequence_generator'), 'trending', 'Trending now', 'What''s catching eyes this week.', true, 40, NULL),
    (nextval('sequence_generator'), 'new_arrivals', 'New arrivals', 'Fresh in this season.', true, 50, NULL),
    (nextval('sequence_generator'), 'featured', 'Featured', 'Editor''s picks.', true, 60, NULL),
    (nextval('sequence_generator'), 'watch_shop', 'Watch & Shop', null, true, 55, NULL),
    (nextval('sequence_generator'), 'fabric_story', 'Fabric story', null, true, 65, NULL),
    (nextval('sequence_generator'), 'brand_story', 'Our story', null, true, 75, NULL),
    (nextval('sequence_generator'), 'reviews', 'Reviews', null, true, 85, NULL),
    (nextval('sequence_generator'), 'journal_strip', 'Blog', 'Soft mornings, gentle reads.', true, 70, NULL),
    (nextval('sequence_generator'), 'gifts', 'Gifts', null, true, 80, NULL)
ON CONFLICT (section_key) DO NOTHING;

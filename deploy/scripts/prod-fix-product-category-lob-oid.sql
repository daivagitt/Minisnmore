-- Convert product/category text columns stored as PostgreSQL large-object OIDs (from @Lob) into plain text.

UPDATE product
SET description = convert_from(lo_get(description::bigint), 'UTF8')
WHERE description IS NOT NULL
  AND description ~ '^[0-9]+$'
  AND length(description) < 12;

UPDATE product
SET sizes_json = convert_from(lo_get(sizes_json::bigint), 'UTF8')
WHERE sizes_json IS NOT NULL
  AND sizes_json ~ '^[0-9]+$'
  AND length(sizes_json) < 12;

UPDATE product
SET colors_json = convert_from(lo_get(colors_json::bigint), 'UTF8')
WHERE colors_json IS NOT NULL
  AND colors_json ~ '^[0-9]+$'
  AND length(colors_json) < 12;

UPDATE product
SET images_json = convert_from(lo_get(images_json::bigint), 'UTF8')
WHERE images_json IS NOT NULL
  AND images_json ~ '^[0-9]+$'
  AND length(images_json) < 12;

UPDATE product
SET story = convert_from(lo_get(story::bigint), 'UTF8')
WHERE story IS NOT NULL
  AND story ~ '^[0-9]+$'
  AND length(story) < 12;

UPDATE category
SET description = convert_from(lo_get(description::bigint), 'UTF8')
WHERE description IS NOT NULL
  AND description ~ '^[0-9]+$'
  AND length(description) < 12;

UPDATE blog_post
SET excerpt = convert_from(lo_get(excerpt::bigint), 'UTF8')
WHERE excerpt IS NOT NULL
  AND excerpt ~ '^[0-9]+$'
  AND length(excerpt) < 12;

UPDATE blog_post
SET content = convert_from(lo_get(content::bigint), 'UTF8')
WHERE content IS NOT NULL
  AND content ~ '^[0-9]+$'
  AND length(content) < 12;

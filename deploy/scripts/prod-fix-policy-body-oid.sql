-- Convert policy.body values stored as PostgreSQL large-object OIDs (from @Lob) into plain text.
UPDATE policy
SET body = convert_from(lo_get(body::bigint), 'UTF8')
WHERE body IS NOT NULL
  AND body ~ '^[0-9]+$'
  AND length(body) < 12;

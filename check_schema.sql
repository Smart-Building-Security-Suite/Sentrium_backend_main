-- Check if device table exists and what columns it has
SELECT
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_name = 'device'
ORDER BY ordinal_position;

-- Check zone table
SELECT
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_name = 'zone'
ORDER BY ordinal_position;

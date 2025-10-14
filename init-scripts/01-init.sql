-- =====================================================================================
-- PocketWardrobe Database Initialization Script
-- =====================================================================================
-- This script runs automatically when PostgreSQL container starts for the first time
-- =====================================================================================

-- Enable UUID extension for generating unique identifiers
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Set default encoding and locale
SET client_encoding = 'UTF8';

-- Log successful initialization
DO $$
BEGIN
    RAISE NOTICE 'PocketWardrobe database initialized successfully';
    RAISE NOTICE 'UUID extension enabled';
    RAISE NOTICE 'Ready for application schema creation by Exposed ORM';
END $$;

-- =====================================================================================
-- Optional: Add seed data below if needed
-- =====================================================================================
-- Example:
-- INSERT INTO example_table (id, name) VALUES (uuid_generate_v4(), 'Example');

-- =====================================================================================
-- Note: Application tables (UserTable, ClotheTable, etc.) will be automatically
-- created by Exposed ORM on first application startup via SchemaUtils.create()
-- =====================================================================================

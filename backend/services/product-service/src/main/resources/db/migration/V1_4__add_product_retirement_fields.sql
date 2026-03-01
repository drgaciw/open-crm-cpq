-- Add retirement tracking fields to products table
ALTER TABLE products ADD COLUMN IF NOT EXISTS retired_at TIMESTAMP;
ALTER TABLE products ADD COLUMN IF NOT EXISTS retirement_reason VARCHAR(500);

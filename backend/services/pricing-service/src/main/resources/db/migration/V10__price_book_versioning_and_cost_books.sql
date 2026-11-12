SET search_path TO pricing;

-- DRG-431 / BC-03-01: price book versioning + cost book linkage.
-- pricing-service exclusively owns pricing schema migrations, including price-book tables (see V1 comment);
-- pricebook-service validates against this schema with ddl-auto=validate.

-- Price books: explicit DRAFT/PUBLISHED lifecycle, a business version number distinct from the optimistic-lock
-- "version" column, and an optional link to the cost book that supplies this price book's costs.
ALTER TABLE price_books ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE price_books ADD COLUMN version_number INTEGER NOT NULL DEFAULT 1;
ALTER TABLE price_books ADD COLUMN published_at TIMESTAMPTZ;
ALTER TABLE price_books ADD COLUMN cost_book_id BIGINT;

-- Backfill: a price book whose effective date has already arrived was, under the pre-existing date-based
-- immutability check (PriceBookService#ensureNotPublished), already being treated as published.
UPDATE price_books
   SET status = 'PUBLISHED', published_at = COALESCE(updated_at, created_at)
 WHERE effective_date IS NOT NULL AND effective_date <= CURRENT_DATE;

-- Price book entries: key them by SKU (product_external_id), currency, unit of measure and an effective date
-- range, so multiple dated versions of the same SKU's price can coexist in one price book.
ALTER TABLE price_book_entries ADD COLUMN unit_of_measure VARCHAR(20) NOT NULL DEFAULT 'EACH';
ALTER TABLE price_book_entries ADD COLUMN effective_start_date DATE;
ALTER TABLE price_book_entries ADD COLUMN effective_end_date DATE;

-- Cost books: same lifecycle shape as price books, kept as a separate aggregate so costs are versioned and
-- published independently of list prices (AC: "list prices and costs are versioned and separable").
CREATE TABLE cost_books (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    region VARCHAR(30),
    currency VARCHAR(3) DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version_number INTEGER NOT NULL DEFAULT 1,
    effective_date DATE,
    expiration_date DATE,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE cost_book_entries (
    id BIGSERIAL PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    cost_book_id BIGINT NOT NULL REFERENCES cost_books(id),
    sku VARCHAR(255) NOT NULL,
    cost DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    unit_of_measure VARCHAR(20) NOT NULL DEFAULT 'EACH',
    effective_start_date DATE,
    effective_end_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT false
);

ALTER TABLE price_books
    ADD CONSTRAINT fk_price_book_cost_book FOREIGN KEY (cost_book_id) REFERENCES cost_books(id);

-- Indexes
CREATE INDEX idx_price_book_status ON price_books(status);
CREATE INDEX idx_price_book_cost_book ON price_books(cost_book_id);

CREATE INDEX idx_price_book_entry_resolution
    ON price_book_entries(price_book_id, product_external_id, currency, unit_of_measure);

CREATE INDEX idx_cost_book_region ON cost_books(region);
CREATE INDEX idx_cost_book_status ON cost_books(status);
CREATE INDEX idx_cost_book_not_deleted ON cost_books(is_deleted) WHERE is_deleted = false;

CREATE INDEX idx_cost_book_entry_book ON cost_book_entries(cost_book_id);
CREATE INDEX idx_cost_book_entry_resolution
    ON cost_book_entries(cost_book_id, sku, currency, unit_of_measure);

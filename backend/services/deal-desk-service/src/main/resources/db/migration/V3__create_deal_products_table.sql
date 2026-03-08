CREATE TABLE deal_desk.deal_products (
    deal_id BIGINT NOT NULL REFERENCES deal_desk.deals(id) ON DELETE CASCADE,
    product_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (deal_id, product_id)
);

CREATE INDEX idx_deal_products_product_id ON deal_desk.deal_products(product_id);

ALTER TABLE payment ADD COLUMN provider_transaction_no VARCHAR(100) NULL;
CREATE UNIQUE INDEX uk_payment_provider_transaction ON payment(channel, provider_transaction_no);

CREATE TABLE customer_billing (
  id                  SERIAL PRIMARY KEY NOT NULL,
  user_key            UUID NOT NULL,
  billing_customer_id VARCHAR(255),
  subscription_id     VARCHAR(255),
  expiration          TIMESTAMP,

  CONSTRAINT userkey_uniq UNIQUE(user_key)
);

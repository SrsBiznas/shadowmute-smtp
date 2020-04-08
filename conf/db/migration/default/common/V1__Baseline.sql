CREATE TABLE users (
  id            SERIAL PRIMARY KEY NOT NULL,
  user_key      UUID NOT NULL,
  username      VARCHAR(255),
  hash          VARCHAR(255),
  salt          VARCHAR(32),
  totp_secret   BYTEA,
  disabled_on   DATE,

  first_name    VARCHAR(255),
  last_name     VARCHAR(255),
  email_address VARCHAR(255),

  CONSTRAINT username_uniq UNIQUE(username)
);

INSERT INTO users (
  id,
  user_key,
  username,
  hash,
  salt,
  first_name,
  last_name,
  email_address
) VALUES (
 0,
 '00000000-0000-0000-0000-000000000000'::uuid,
 'root',
 '','','','',''
);

CREATE TABLE recipients (
  id              SERIAL PRIMARY KEY NOT NULL,
  mailbox         UUID NULL,
  created         TIMESTAMP NOT NULL DEFAULT NOW(),
  owner_id        INTEGER NOT NULL,
  associated_site TEXT,
  notes           TEXT
);

CREATE TABLE invitations (
  id              SERIAL PRIMARY KEY NOT NULL,
  referrer        INTEGER NOT NULL,
  invitation_code UUID NOT NULL,
  redeemed_by     INTEGER,
  redeemed_on     TIMESTAMP,
  created         TIMESTAMP NOT NULL DEFAULT NOW(),

  CONSTRAINT invitation_uniq UNIQUE(invitation_code)
);

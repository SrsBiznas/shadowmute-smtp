CREATE TABLE oauth_tokens (
  id            SERIAL PRIMARY KEY NOT NULL,
  bearer_token  CHAR(64) NOT NULL,
  created       TIMESTAMP NOT NULL,
  scopes        TEXT NOT NULL,
  owner_id      INTEGER NOT NULL,
  client_id     TEXT NOT NULL,
  last_seen     TIMESTAMP NOT NULL,

  CONSTRAINT bearer_token_uniq UNIQUE(bearer_token)
);

CREATE INDEX bearer_token_idx ON oauth_tokens (bearer_token);

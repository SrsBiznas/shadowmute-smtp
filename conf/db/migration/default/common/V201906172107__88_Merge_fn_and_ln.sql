ALTER TABLE users ADD COLUMN real_name VARCHAR(255);

UPDATE users set real_name=concat(first_name, ' ', last_name);

/* ALTER TABLE users DROP COLUMN first_name;
ALTER TABLE users DROP COLUMN last_name; */

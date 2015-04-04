ALTER TABLE restaurant ADD COLUMN place_id TEXT;
CREATE UNIQUE INDEX restaurant_place_id ON restaurant (place_id);

DROP INDEX review_restaurant_id_type_id;
CREATE INDEX review_restaurant_id ON review (restaurant_id, type_id, written_on);

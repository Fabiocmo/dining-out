ALTER TABLE restaurant ADD COLUMN refreshed_on TEXT;
CREATE INDEX restaurant_refreshed_on ON restaurant (refreshed_on);


CREATE TABLE open_hour_type (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL
);
INSERT INTO open_hour_type (name) VALUES ('open'), ('close');


CREATE TABLE open_hour (
    restaurant_id INTEGER NOT NULL,
    type_id INTEGER NOT NULL,
    day INTEGER NOT NULL,
    time INTEGER NOT NULL,
    inserted_on TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (restaurant_id, day, time)
);


CREATE TABLE open_day (
    restaurant_id INTEGER NOT NULL,
    day INTEGER NOT NULL,
    hours TEXT NOT NULL,
    inserted_on TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (restaurant_id, day)
);

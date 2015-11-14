CREATE TABLE status (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL
);
INSERT INTO status (name) VALUES ('active'), ('inactive'), ('deleted'), ('merged');


CREATE TABLE contact (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    global_id INTEGER UNIQUE,
    android_lookup_key TEXT,
    android_id INTEGER,
    name TEXT COLLATE LOCALIZED,
    normalised_name TEXT COLLATE LOCALIZED,
    email TEXT,
    email_hash TEXT NOT NULL UNIQUE, -- SHA-512 + base 64
    following INTEGER NOT NULL DEFAULT 0,
    color INTEGER, -- most prominent in photo
    status_id INTEGER NOT NULL DEFAULT 1,
    dirty INTEGER NOT NULL DEFAULT 1,
    version INTEGER NOT NULL DEFAULT 0,
    inserted_on TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_on TEXT
);
CREATE INDEX contact_android_id ON contact (android_id);
CREATE INDEX contact_dirty ON contact (dirty);

CREATE TRIGGER contact_version AFTER UPDATE OF dirty ON contact WHEN NEW.dirty = 1
BEGIN
    UPDATE contact SET version = version + 1 WHERE _id = OLD._id;--
END;

CREATE TRIGGER contact_updated AFTER UPDATE ON contact
BEGIN
    UPDATE contact SET updated_on = CURRENT_TIMESTAMP WHERE _id = OLD._id;--
END;


CREATE TABLE restaurant (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    global_id INTEGER UNIQUE,
    place_id TEXT UNIQUE, -- future: remove unique, create index
    google_url TEXT,
    name TEXT NOT NULL COLLATE LOCALIZED,
    normalised_name TEXT NOT NULL COLLATE LOCALIZED,
    address TEXT COLLATE LOCALIZED,
    vicinity TEXT COLLATE LOCALIZED, -- address up to city
    latitude REAL,
    longitude REAL,
    longitude_cos REAL, -- cos(latitude/57.295779579), used in distance calc, should be latitude_cos
    intl_phone TEXT, -- includes prefixed country code
    local_phone TEXT, -- in local format
    url TEXT,
    price INTEGER, -- 1-4
    rating REAL, -- 1.0-5.0, average of all reviews
    color INTEGER, -- most prominent in photo
    notes TEXT COLLATE LOCALIZED,
    geofence_notifications INTEGER NOT NULL DEFAULT 1,
    visiting INTEGER NOT NULL DEFAULT 0,
    last_visit_on TEXT, -- cached from review
    refreshed_on TEXT,
    status_id INTEGER NOT NULL DEFAULT 1,
    merged_into_id INTEGER,
    dirty INTEGER NOT NULL DEFAULT 1,
    version INTEGER NOT NULL DEFAULT 0,
    inserted_on TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_on TEXT
);
CREATE INDEX restaurant_refreshed_on ON restaurant (refreshed_on);
CREATE INDEX restaurant_dirty ON restaurant (dirty);

CREATE TRIGGER restaurant_version AFTER UPDATE OF dirty ON restaurant WHEN NEW.dirty = 1
BEGIN
    UPDATE restaurant SET version = version + 1 WHERE _id = OLD._id;--
END;

CREATE TRIGGER restaurant_updated AFTER UPDATE ON restaurant
BEGIN
    UPDATE restaurant SET updated_on = CURRENT_TIMESTAMP WHERE _id = OLD._id;--
END;


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


CREATE TABLE restaurant_photo (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    restaurant_id INTEGER NOT NULL,
    google_reference TEXT NOT NULL,
    width INTEGER NOT NULL, -- maximum on the server, local dependent on screen size
    height INTEGER NOT NULL,
    etag TEXT,
    inserted_on TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_on TEXT
);
CREATE INDEX restaurant_photo_restaurant_id ON restaurant_photo (restaurant_id);

CREATE TRIGGER restaurant_photo_updated AFTER UPDATE ON restaurant_photo
BEGIN
    UPDATE restaurant_photo SET updated_on = CURRENT_TIMESTAMP WHERE _id = OLD._id;--
END;


CREATE TABLE review_type (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL
);
INSERT INTO review_type (name) VALUES ('private'), ('google');


CREATE TABLE review (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    global_id INTEGER UNIQUE,
    restaurant_id INTEGER NOT NULL,
    type_id INTEGER NOT NULL,
    contact_id INTEGER, -- friend that wrote the review
    author_name TEXT COLLATE LOCALIZED, -- public review author
    comments TEXT NOT NULL COLLATE LOCALIZED,
    rating INTEGER, -- 1-5
    written_on TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status_id INTEGER NOT NULL DEFAULT 1,
    dirty INTEGER NOT NULL DEFAULT 1,
    version INTEGER NOT NULL DEFAULT 0,
    inserted_on TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_on TEXT
);
CREATE INDEX review_restaurant_id ON review (restaurant_id, type_id, written_on);
CREATE INDEX review_dirty ON review (dirty);

CREATE TRIGGER review_version AFTER UPDATE OF dirty ON review WHEN NEW.dirty = 1
BEGIN
    UPDATE review SET version = version + 1 WHERE _id = OLD._id;--
END;

CREATE TRIGGER review_updated AFTER UPDATE ON review
BEGIN
    UPDATE review SET updated_on = CURRENT_TIMESTAMP WHERE _id = OLD._id;--
END;


CREATE TABLE review_draft (
    restaurant_id INTEGER PRIMARY KEY,
    comments TEXT COLLATE LOCALIZED,
    rating INTEGER,
    status_id INTEGER NOT NULL DEFAULT 1,
    dirty INTEGER NOT NULL DEFAULT 1,
    version INTEGER NOT NULL DEFAULT 0,
    inserted_on TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_on TEXT
);
CREATE INDEX review_draft_dirty ON review_draft (dirty);

CREATE TRIGGER review_draft_version AFTER UPDATE OF dirty ON review_draft WHEN NEW.dirty = 1
BEGIN
    UPDATE review_draft SET version = version + 1 WHERE restaurant_id = OLD.restaurant_id;--
END;

CREATE TRIGGER review_draft_updated AFTER UPDATE ON review_draft
BEGIN
    UPDATE review_draft SET updated_on = CURRENT_TIMESTAMP
    WHERE restaurant_id = OLD.restaurant_id;--
END;


CREATE TABLE object_type (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL
);
INSERT INTO object_type (name)
VALUES ('user'), ('restaurant'), ('visit'), ('review'), ('restaurant photo'), ('review draft');


CREATE TABLE object_action (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL
);
INSERT INTO object_action (name) VALUES ('insert'), ('update'), ('delete'), ('merge');


-- remote changes that the user should be notified about
CREATE TABLE sync (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    global_id INTEGER UNIQUE,
    type_id INTEGER NOT NULL,
    object_id INTEGER NOT NULL,
    action_id INTEGER NOT NULL,
    action_on TEXT NOT NULL,
    status_id INTEGER NOT NULL DEFAULT 1, -- unread, read, deleted
    inserted_on TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_on TEXT
);
CREATE INDEX sync_status_id ON sync (status_id);

CREATE TRIGGER sync_updated AFTER UPDATE ON sync
BEGIN
    UPDATE sync SET updated_on = CURRENT_TIMESTAMP WHERE _id = OLD._id;--
END;

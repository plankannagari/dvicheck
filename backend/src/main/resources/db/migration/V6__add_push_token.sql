ALTER TABLE users
    ADD COLUMN push_token VARCHAR(200);

COMMENT ON COLUMN users.push_token
    IS 'Expo push notification token — null if user has not granted permission';

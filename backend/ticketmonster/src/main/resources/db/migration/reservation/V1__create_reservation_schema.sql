CREATE TABLE zone_stock (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    zone_id VARCHAR(255) NOT NULL,
    total_capacity INTEGER NOT NULL,
    available_count INTEGER NOT NULL,
    UNIQUE(event_id, zone_id)
);

CREATE TABLE reservations (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    event_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reservations_user_id ON reservations(user_id);
CREATE INDEX idx_reservations_event_id ON reservations(event_id);
CREATE INDEX idx_reservations_status_expires ON reservations(status, expires_at);

CREATE TABLE reservation_items (
    id BIGSERIAL PRIMARY KEY,
    reservation_id VARCHAR(36) NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    zone_id VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL
);

CREATE INDEX idx_reservation_items_reservation ON reservation_items(reservation_id);

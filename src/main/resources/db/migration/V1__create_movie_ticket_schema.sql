CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(180) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'CUSTOMER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cities (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE
);

CREATE TABLE theaters (
    id BIGSERIAL PRIMARY KEY,
    city_id BIGINT NOT NULL REFERENCES cities(id),
    name VARCHAR(160) NOT NULL,
    address TEXT NOT NULL,
    UNIQUE (city_id, name)
);

CREATE TABLE screens (
    id BIGSERIAL PRIMARY KEY,
    theater_id BIGINT NOT NULL REFERENCES theaters(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    UNIQUE (theater_id, name)
);

CREATE TABLE seats (
    id BIGSERIAL PRIMARY KEY,
    screen_id BIGINT NOT NULL REFERENCES screens(id) ON DELETE CASCADE,
    row_label VARCHAR(8) NOT NULL,
    seat_number INT NOT NULL CHECK (seat_number > 0),
    seat_tier VARCHAR(20) NOT NULL CHECK (seat_tier IN ('REGULAR', 'PREMIUM')),
    UNIQUE (screen_id, row_label, seat_number)
);

CREATE TABLE movies (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    language VARCHAR(60) NOT NULL,
    duration_minutes INT NOT NULL CHECK (duration_minutes > 0),
    certificate VARCHAR(20) NOT NULL
);

CREATE TABLE pricing_tiers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    regular_price NUMERIC(10,2) NOT NULL CHECK (regular_price >= 0),
    premium_price NUMERIC(10,2) NOT NULL CHECK (premium_price >= 0),
    weekend_multiplier NUMERIC(6,2) NOT NULL DEFAULT 1.00 CHECK (weekend_multiplier >= 1.00)
);

CREATE TABLE refund_policies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    cutoff_minutes_before_show INT NOT NULL CHECK (cutoff_minutes_before_show >= 0),
    refund_percent NUMERIC(5,2) NOT NULL CHECK (refund_percent >= 0 AND refund_percent <= 100)
);

CREATE TABLE discount_codes (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    percent_off NUMERIC(5,2) NOT NULL CHECK (percent_off > 0 AND percent_off <= 100),
    active BOOLEAN NOT NULL DEFAULT true,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    max_uses INT,
    uses_count INT NOT NULL DEFAULT 0,
    CHECK (valid_until > valid_from),
    CHECK (max_uses IS NULL OR max_uses > 0)
);

CREATE TABLE shows (
    id BIGSERIAL PRIMARY KEY,
    movie_id BIGINT NOT NULL REFERENCES movies(id),
    screen_id BIGINT NOT NULL REFERENCES screens(id),
    starts_at TIMESTAMPTZ NOT NULL,
    pricing_tier_id BIGINT NOT NULL REFERENCES pricing_tiers(id),
    refund_policy_id BIGINT NOT NULL REFERENCES refund_policies(id),
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    UNIQUE (screen_id, starts_at)
);

CREATE TABLE show_seats (
    id BIGSERIAL PRIMARY KEY,
    show_id BIGINT NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    seat_id BIGINT NOT NULL REFERENCES seats(id),
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED')),
    hold_expires_at TIMESTAMPTZ,
    hold_token UUID,
    held_by_user_id BIGINT REFERENCES app_users(id),
    booked_by_booking_id BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (show_id, seat_id)
);

CREATE INDEX idx_show_seats_show_status ON show_seats(show_id, status);
CREATE INDEX idx_show_seats_expiry ON show_seats(hold_expires_at) WHERE status = 'HELD';

CREATE TABLE seat_holds (
    id BIGSERIAL PRIMARY KEY,
    hold_token UUID NOT NULL UNIQUE,
    show_id BIGINT NOT NULL REFERENCES shows(id),
    user_id BIGINT NOT NULL REFERENCES app_users(id),
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CONFIRMED', 'EXPIRED', 'RELEASED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,
    booking_reference VARCHAR(40) NOT NULL UNIQUE,
    show_id BIGINT NOT NULL REFERENCES shows(id),
    user_id BIGINT NOT NULL REFERENCES app_users(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    subtotal NUMERIC(10,2) NOT NULL,
    discount_amount NUMERIC(10,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(10,2) NOT NULL,
    discount_code_id BIGINT REFERENCES discount_codes(id),
    refund_amount NUMERIC(10,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_at TIMESTAMPTZ
);

ALTER TABLE show_seats
    ADD CONSTRAINT fk_show_seats_booking
    FOREIGN KEY (booked_by_booking_id) REFERENCES bookings(id);

CREATE TABLE booking_seats (
    booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    show_seat_id BIGINT NOT NULL REFERENCES show_seats(id),
    price NUMERIC(10,2) NOT NULL,
    PRIMARY KEY (booking_id, show_seat_id)
);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id),
    provider_reference VARCHAR(120) NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('AUTHORIZED', 'CAPTURED', 'REFUNDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id),
    booking_id BIGINT REFERENCES bookings(id),
    type VARCHAR(40) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED', 'SENT', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ
);

INSERT INTO app_users (name, email, role) VALUES
('Admin User', 'admin@example.com', 'ADMIN'),
('Customer One', 'customer@example.com', 'CUSTOMER');

INSERT INTO pricing_tiers (name, regular_price, premium_price, weekend_multiplier) VALUES
('Standard', 180.00, 280.00, 1.25);

INSERT INTO refund_policies (name, cutoff_minutes_before_show, refund_percent) VALUES
('Default full refund until two hours before show', 120, 100.00);

INSERT INTO discount_codes (code, percent_off, active, valid_from, valid_until, max_uses) VALUES
('WELCOME10', 10.00, true, now() - interval '1 day', now() + interval '365 days', 1000);

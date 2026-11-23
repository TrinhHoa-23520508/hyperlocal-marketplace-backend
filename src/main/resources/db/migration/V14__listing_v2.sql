-- Module: Listing + Discovery, rebuilt to the SRS data dictionary (section 4.2, module listing; OI-03).
-- The V3 tables followed an earlier design and were never used by any code, so they hold no data.

CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- unaccent() is only STABLE; generated columns and indexes need an IMMUTABLE function.
CREATE FUNCTION f_unaccent(text) RETURNS text
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
    STRICT
AS
$$
SELECT public.unaccent('public.unaccent'::regdictionary, $1)
$$;

DROP TABLE favorites;
DROP TABLE listing_images;
DROP TABLE listings;
DROP TABLE categories;

CREATE TABLE categories
(
    id         SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(30)  NOT NULL
        CONSTRAINT categories_code_uq UNIQUE,
    name       VARCHAR(60)  NOT NULL,
    icon_key   VARCHAR(200),
    sort_order SMALLINT     NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version    BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE listings
(
    id                      UUID PRIMARY KEY,
    seller_id               UUID                   NOT NULL, -- logical -> users
    category_id             SMALLINT               NOT NULL REFERENCES categories (id),
    area_id                 UUID                   NOT NULL, -- logical -> user_areas
    title                   VARCHAR(100)           NOT NULL
        CONSTRAINT listings_title_length_chk CHECK (char_length(title) BETWEEN 5 AND 100),
    description             VARCHAR(2000),
    condition               VARCHAR(10)            NOT NULL
        CONSTRAINT listings_condition_chk CHECK (condition IN ('NEW', 'LIKE_NEW', 'USED', 'WORN')),
    price                   BIGINT                 NOT NULL
        CONSTRAINT listings_price_chk CHECK (price BETWEEN 0 AND 1000000000),
    is_free                 BOOLEAN GENERATED ALWAYS AS (price = 0) STORED,
    accept_offers           BOOLEAN                NOT NULL DEFAULT TRUE,
    status                  VARCHAR(15)            NOT NULL
        CONSTRAINT listings_status_chk CHECK (status IN ('PENDING_REVIEW', 'ACTIVE', 'RESERVED', 'SOLD', 'HIDDEN',
                                                         'DELETED', 'REMOVED')),
    hidden_reason           VARCHAR(15)
        CONSTRAINT listings_hidden_reason_chk CHECK (hidden_reason IN ('OWNER', 'AREA_REMOVED', 'SANCTION')),
    exact_location          geography(Point, 4326) NOT NULL, -- never returned by a public API (FR-LS-03)
    public_location         geography(Point, 4326) NOT NULL, -- center of the listing.blur_m grid cell
    locality_name           VARCHAR(100),
    meeting_spot_text       VARCHAR(100),
    bumped_at               TIMESTAMPTZ,
    bump_count              SMALLINT               NOT NULL DEFAULT 0
        CONSTRAINT listings_bump_count_chk CHECK (bump_count >= 0),
    view_count              INT                    NOT NULL DEFAULT 0
        CONSTRAINT listings_view_count_chk CHECK (view_count >= 0),
    favorite_count          INT                    NOT NULL DEFAULT 0
        CONSTRAINT listings_favorite_count_chk CHECK (favorite_count >= 0),
    chat_count              INT                    NOT NULL DEFAULT 0
        CONSTRAINT listings_chat_count_chk CHECK (chat_count >= 0),
    reserved_transaction_id UUID,                            -- logical -> transactions
    seller_score            NUMERIC(5, 2),                   -- copy pushed by the reputation module
    seller_tier             VARCHAR(15)
        CONSTRAINT listings_seller_tier_chk CHECK (seller_tier IN ('NEW', 'CAUTION', 'STANDARD', 'TRUSTED',
                                                                   'HIGHLY_TRUSTED')),
    review_reason           VARCHAR(200),
    removal_reason          VARCHAR(200),
    removed_by              UUID,                            -- logical -> users
    published_at            TIMESTAMPTZ,
    sold_at                 TIMESTAMPTZ,
    deleted_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ            NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ            NOT NULL DEFAULT now(),
    version                 BIGINT                 NOT NULL DEFAULT 0,
    feed_at                 TIMESTAMPTZ GENERATED ALWAYS AS (greatest(created_at, bumped_at)) STORED,
    search_vector           TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', f_unaccent(title)), 'A') ||
        setweight(to_tsvector('simple', coalesce(f_unaccent(description), '')), 'B')
        ) STORED,
    CONSTRAINT listings_hidden_reason_when_hidden_chk CHECK ((status = 'HIDDEN') = (hidden_reason IS NOT NULL)),
    CONSTRAINT listings_reserved_transaction_chk CHECK ((status = 'RESERVED') = (reserved_transaction_id IS NOT NULL)),
    CONSTRAINT listings_removal_reason_chk CHECK (status <> 'REMOVED' OR removal_reason IS NOT NULL)
);
CREATE INDEX listings_public_location_gix ON listings USING gist (public_location);
CREATE INDEX listings_status_feed_idx ON listings (status, feed_at DESC, id DESC);
CREATE INDEX listings_search_vector_gin ON listings USING gin (search_vector);
CREATE INDEX listings_title_trgm_gin ON listings USING gin (f_unaccent(lower(title)) gin_trgm_ops);
CREATE INDEX listings_seller_status_idx ON listings (seller_id, status);
CREATE INDEX listings_area_id_idx ON listings (area_id);

CREATE TABLE listing_images
(
    id                UUID PRIMARY KEY,
    listing_id        UUID REFERENCES listings (id) ON DELETE CASCADE,
    owner_id          UUID         NOT NULL, -- logical -> users
    original_key      VARCHAR(200) NOT NULL, -- private incoming/ prefix, never served (FR-LS-02)
    thumb_key         VARCHAR(200),          -- 320 px WebP without metadata
    large_key         VARCHAR(200),          -- 1080 px WebP without metadata
    position          SMALLINT
        CONSTRAINT listing_images_position_chk CHECK (position BETWEEN 1 AND 10),
    mime_type         VARCHAR(20)
        CONSTRAINT listing_images_mime_type_chk CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp',
                                                                     'image/heic')),
    size_bytes        INT
        CONSTRAINT listing_images_size_chk CHECK (size_bytes BETWEEN 1 AND 5242880),
    width             INT,
    height            INT,
    processing_status VARCHAR(10)  NOT NULL
        CONSTRAINT listing_images_processing_status_chk CHECK (processing_status IN ('UPLOADING', 'READY', 'REJECTED')),
    reject_reason     VARCHAR(50),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at      TIMESTAMPTZ,
    CONSTRAINT listing_images_position_when_attached_chk CHECK (listing_id IS NULL OR position IS NOT NULL),
    CONSTRAINT listing_images_ready_has_derivatives_chk
        CHECK (processing_status <> 'READY' OR (thumb_key IS NOT NULL AND large_key IS NOT NULL)),
    CONSTRAINT listing_images_listing_position_uq UNIQUE (listing_id, position)
);
CREATE INDEX listing_images_owner_id_idx ON listing_images (owner_id);
CREATE INDEX listing_images_orphan_idx ON listing_images (created_at) WHERE listing_id IS NULL;

CREATE TABLE favorites
(
    user_id    UUID        NOT NULL, -- logical -> users
    listing_id UUID        NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, listing_id)
);
CREATE INDEX favorites_listing_id_idx ON favorites (listing_id);
CREATE INDEX favorites_user_created_idx ON favorites (user_id, created_at DESC);

INSERT INTO categories (code, name, sort_order)
VALUES ('ELECTRONICS', 'Đồ điện tử', 1),
       ('HOUSEHOLD', 'Đồ gia dụng', 2),
       ('BOOKS', 'Sách', 3),
       ('FASHION', 'Thời trang', 4),
       ('VEHICLES', 'Xe cộ', 5),
       ('BABY', 'Mẹ và bé', 6),
       ('SPORTS', 'Thể thao', 7),
       ('FREE_SHARING', 'Cho tặng miễn phí', 8),
       ('OTHER', 'Khác', 9);

-- Parameters per SRS table tab:params; listing.blur_m replaces the V8 key location.blur_meters
DELETE FROM app_parameters
WHERE key = 'location.blur_meters';

INSERT INTO app_parameters (key, value, description)
VALUES ('listing.blur_m', '300', 'Side (Web Mercator meters) of the grid cell a public listing location is snapped to'),
       ('listing.max_images', '10', 'Maximum number of images per listing'),
       ('listing.max_open_per_user', '30', 'Maximum open listings (ACTIVE, RESERVED, PENDING_REVIEW) per seller'),
       ('listing.new_account_daily_limit', '3', 'New listings per day before the seller''s first completed transaction'),
       ('listing.bump_cooldown_hours', '48', 'Hours between two bumps of the same listing'),
       ('listing.bump_max_per_listing', '10', 'Maximum number of bumps per listing'),
       ('upload.max_image_mb', '5', 'Maximum image size (MB); image.max_bytes holds the same limit in bytes'),
       ('upload.orphan_ttl_hours', '24', 'Hours after which an uploaded image not attached to a listing is deleted'),
       ('search.max_radius_km', '20', 'Maximum search radius (km); larger values are clamped'),
       ('search.page_size', '20', 'Listings per feed or search page'),
       ('search.rank_weights', '0.5,0.3,0.2', 'Weights of distance, seller reputation and freshness in relevant sort');

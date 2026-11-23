-- Module: Chat + Notify, rebuilt to the SRS data dictionary (section 4.2, modules chat and notification).
-- The V7 tables followed an earlier design and were never used by any feature, so they hold no data.
-- Not partitioned in R1 (DEC-CH-3): the primary keys keep created_at so monthly partitioning can be added later.

DROP TABLE messages;
DROP TABLE conversations;
DROP TABLE notifications;
DROP TABLE device_tokens;

CREATE TABLE conversations
(
    id                   UUID PRIMARY KEY,
    listing_id           UUID         NOT NULL, -- logical -> listings
    buyer_id             UUID         NOT NULL, -- logical -> users
    seller_id            UUID         NOT NULL, -- logical -> users
    last_message_id      BIGINT,                -- logical -> messages
    last_message_at      TIMESTAMPTZ,
    last_message_preview VARCHAR(100),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT conversations_listing_buyer_uq UNIQUE (listing_id, buyer_id),
    CONSTRAINT conversations_seller_not_buyer_chk CHECK (seller_id <> buyer_id)
);
CREATE INDEX conversations_buyer_last_msg_idx ON conversations (buyer_id, last_message_at DESC NULLS LAST);
CREATE INDEX conversations_seller_last_msg_idx ON conversations (seller_id, last_message_at DESC NULLS LAST);
CREATE INDEX conversations_listing_idx ON conversations (listing_id);

CREATE TABLE conversation_participants
(
    conversation_id      UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id              UUID        NOT NULL, -- logical -> users
    role                 VARCHAR(6)  NOT NULL
        CONSTRAINT conversation_participants_role_chk CHECK (role IN ('BUYER', 'SELLER')),
    last_read_message_id BIGINT      NOT NULL DEFAULT 0,
    unread_count         INTEGER     NOT NULL DEFAULT 0
        CONSTRAINT conversation_participants_unread_chk CHECK (unread_count >= 0),
    muted                BOOLEAN     NOT NULL DEFAULT FALSE, -- FR-CH-09, R2
    left_at              TIMESTAMPTZ,                        -- FR-CH-09, R2
    joined_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, user_id)
);
CREATE INDEX conversation_participants_user_idx ON conversation_participants (user_id);

CREATE SEQUENCE messages_id_seq;

CREATE TABLE messages
(
    id              BIGINT       NOT NULL DEFAULT nextval('messages_id_seq'), -- monotonically increasing sync checkpoint
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    conversation_id UUID         NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id       UUID,                                                     -- logical -> users; NULL only for SYSTEM
    type            VARCHAR(10)  NOT NULL
        CONSTRAINT messages_type_chk CHECK (type IN ('TEXT', 'IMAGE', 'LOCATION', 'SYSTEM')),
    body            VARCHAR(1000),
    payload         JSONB,
    client_msg_id   UUID,
    risk_flag       BOOLEAN      NOT NULL DEFAULT FALSE,                      -- FR-CH-10, R2: always false in R1
    PRIMARY KEY (id, created_at),
    CONSTRAINT messages_text_body_chk CHECK (type <> 'TEXT' OR body IS NOT NULL),
    CONSTRAINT messages_payload_chk CHECK (type = 'TEXT' OR payload IS NOT NULL),
    CONSTRAINT messages_sender_chk CHECK (type = 'SYSTEM' OR sender_id IS NOT NULL),
    CONSTRAINT messages_client_msg_id_chk CHECK (type = 'SYSTEM' OR client_msg_id IS NOT NULL),
    -- DEC-CH-4: the repeat of a send is recognised per sender (FR-CH-02, UC-CH-02 A3)
    CONSTRAINT messages_sender_client_msg_uq UNIQUE (sender_id, client_msg_id)
);
ALTER SEQUENCE messages_id_seq OWNED BY messages.id;
CREATE INDEX messages_conversation_id_idx ON messages (conversation_id, id);

-- Not in the data dictionary (DEC-CH-8): tracks an uploaded chat image from upload to the message that uses it.
CREATE TABLE chat_images
(
    id                UUID PRIMARY KEY,
    owner_id          UUID        NOT NULL, -- logical -> users
    conversation_id   UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    original_key      VARCHAR(200) NOT NULL,
    thumb_key         VARCHAR(200),
    large_key         VARCHAR(200),
    width             INTEGER,
    height            INTEGER,
    processing_status VARCHAR(10) NOT NULL DEFAULT 'UPLOADING'
        CONSTRAINT chat_images_status_chk CHECK (processing_status IN ('UPLOADING', 'READY', 'REJECTED')),
    reject_reason     VARCHAR(30),
    message_id        BIGINT,               -- logical -> messages; set when attached
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at      TIMESTAMPTZ
);
CREATE INDEX chat_images_owner_conversation_idx ON chat_images (owner_id, conversation_id);
CREATE INDEX chat_images_orphan_idx ON chat_images (created_at) WHERE message_id IS NULL;

CREATE TABLE notifications
(
    id              UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    recipient_id    UUID         NOT NULL, -- logical -> users
    category        VARCHAR(15)  NOT NULL
        CONSTRAINT notifications_category_chk CHECK (category IN ('CHAT', 'TRADE', 'KEYWORD_ALERT', 'MARKETING', 'SYSTEM')),
    type            VARCHAR(40)  NOT NULL,
    title           VARCHAR(120) NOT NULL,
    body            VARCHAR(300) NOT NULL,
    deep_link       VARCHAR(200),
    payload         JSONB,
    source_event_id UUID         NOT NULL,
    push_status     VARCHAR(8)   NOT NULL DEFAULT 'PENDING'
        CONSTRAINT notifications_push_status_chk CHECK (push_status IN ('PENDING', 'SENT', 'SKIPPED', 'FAILED')),
    pushed_at       TIMESTAMPTZ,
    read_at         TIMESTAMPTZ,
    PRIMARY KEY (id, created_at),
    -- FR-NT-04: a replayed event creates no second notification
    CONSTRAINT notifications_source_recipient_uq UNIQUE (source_event_id, recipient_id)
);
CREATE INDEX notifications_recipient_created_idx ON notifications (recipient_id, created_at DESC, id DESC);
CREATE INDEX notifications_recipient_unread_idx ON notifications (recipient_id) WHERE read_at IS NULL;
CREATE INDEX notifications_created_idx ON notifications (created_at);

CREATE TABLE device_tokens
(
    id           UUID PRIMARY KEY,
    user_id      UUID         NOT NULL, -- logical -> users
    device_id    VARCHAR(64)  NOT NULL,
    fcm_token    VARCHAR(255) NOT NULL
        CONSTRAINT device_tokens_fcm_token_uq UNIQUE,
    platform     VARCHAR(10)  NOT NULL DEFAULT 'ANDROID'
        CONSTRAINT device_tokens_platform_chk CHECK (platform IN ('ANDROID', 'WEB')),
    app_version  VARCHAR(20),
    last_seen_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- FR-NT-02: one token per device
    CONSTRAINT device_tokens_user_device_uq UNIQUE (user_id, device_id)
);

CREATE TABLE notification_preferences
(
    user_id            UUID PRIMARY KEY, -- logical -> users
    chat_push          BOOLEAN      NOT NULL DEFAULT TRUE,
    trade_push         BOOLEAN      NOT NULL DEFAULT TRUE,
    keyword_alert_push BOOLEAN      NOT NULL DEFAULT TRUE, -- from R2
    marketing_push     BOOLEAN      NOT NULL DEFAULT FALSE,
    dnd_enabled        BOOLEAN      NOT NULL DEFAULT FALSE,
    dnd_start          TIME         NOT NULL DEFAULT '22:00',
    dnd_end            TIME         NOT NULL DEFAULT '07:00',
    time_zone          VARCHAR(40)  NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0
);

-- Parameters per SRS table tab:params
INSERT INTO app_parameters (key, value, description)
VALUES ('chat.max_message_chars', '1000', 'Maximum characters of a text message (FR-CH-03)'),
       ('chat.rate_limit_per_min', '30', 'Chat messages per user per minute (FR-CH-03)'),
       ('notification.retention_months', '6', 'Months a notification is kept (FR-NT-05)');

-- FR-CH-08: a domain event posts exactly one SYSTEM message into a conversation, even when the
-- event is delivered twice. The event id is stored as client_msg_id; (sender_id, client_msg_id)
-- cannot catch it because a SYSTEM message has no sender.
CREATE UNIQUE INDEX messages_system_event_uq ON messages (conversation_id, client_msg_id)
    WHERE type = 'SYSTEM' AND client_msg_id IS NOT NULL;

# Chat over STOMP: frame reference for the Android app

Real-time part of [`chat-api.md`](chat-api.md) (FR-CH-02, FR-CH-06, FR-CH-07, FR-NT-06). **REST is the source of truth; the socket only pushes and accepts the same send and read as REST.** If the socket is down the app works over REST and syncs when it is back.

## Connect

| Item | Value |
|---|---|
| URL | `wss://<host>/ws` (`ws://<host>:8080/ws` locally; emulator host `10.0.2.2`) |
| Protocol | STOMP 1.2 over a plain WebSocket (no SockJS) |
| Authentication | STOMP `CONNECT` header `Authorization: Bearer <accessToken>`. A missing, tampered or **expired** token gets an `ERROR` frame and the connection closes (UC-CH-02 E6): refresh the token, reconnect, send the unconfirmed messages again |
| Heartbeat | Offer `heart-beat:10000,10000`; reconnect with backoff (1 s, 2 s, 4 s, up to 30 s) when it fails |
| One user, many sessions | Every session of the user receives everything, so a phone and a tablet stay in step |

## Destinations

Subscribe to all five right after `CONNECTED`. The `/user` prefix is added by the app; the server routes by the authenticated user.

| Subscribe to | What arrives | Payload |
|---|---|---|
| `/user/queue/messages` | A new message in any of your conversations, including the ones you sent from another device | Message (below) |
| `/user/queue/acks` | The server stored a message you sent over STOMP | `{"clientMsgId": "…", "id": 1042, "createdAt": "2026-11-23T10:00:03Z"}` |
| `/user/queue/read-receipts` | The counterpart read your messages | `{"conversationId": "…", "readerId": "…", "lastReadMessageId": 1042}` |
| `/user/queue/badges` | Your unread counts changed | `{"chatUnread": 3, "notificationUnread": 1}` |
| `/user/queue/errors` | A STOMP send or read failed | Problem body (below) |

| Send to | Payload | Same as REST |
|---|---|---|
| `/app/conversations/{conversationId}/messages` | Send body of `POST /conversations/{id}/messages` | Yes: same rules and codes |
| `/app/conversations/{conversationId}/read` | `{"lastReadMessageId": 1042}` | `PUT /conversations/{id}/read` |

Set `content-type:application/json` on every `SEND`.

## Frames

Send a text message:

```
SEND
destination:/app/conversations/0b1c0f4e-6f0a-4a43-9d2f-8c4f3b0a1e55/messages
content-type:application/json

{"clientMsgId":"5c1e7a54-1d0e-4a3b-9a0e-6f1f4d2b9c10","type":"TEXT","body":"Còn hàng không bạn?"}
```

Send a meeting point (`type: "LOCATION"`) or images (`type: "IMAGE"`, `imageIds` of READY images):

```json
{"clientMsgId":"…","type":"LOCATION","point":{"latitude":10.7769,"longitude":106.7009,"placeName":"Công viên 23/9"}}
{"clientMsgId":"…","type":"IMAGE","imageIds":["…","…"]}
```

Acknowledgement to you on `/user/queue/acks`; the message to both members on `/user/queue/messages`:

```
MESSAGE
destination:/user/queue/acks
content-type:application/json

{"clientMsgId":"5c1e7a54-1d0e-4a3b-9a0e-6f1f4d2b9c10","id":1042,"createdAt":"2026-11-23T10:00:03Z"}
```

```json
{"id":1042,"conversationId":"0b1c…","senderId":"3f2a…","type":"TEXT","body":"Còn hàng không bạn?","clientMsgId":"5c1e…","createdAt":"2026-11-23T10:00:03Z"}
```

Match a delivered message to the one you sent by `clientMsgId`. Sending the same `clientMsgId` again returns an ack with the **same** `id`, and nothing is sent twice.

### Errors

A failure arrives on `/user/queue/errors` as the REST error body plus the `clientMsgId` of the message it belongs to:

```json
{
  "type": "https://nearby.uit.edu.vn/problems/message-too-long",
  "title": "Unprocessable Content",
  "status": 422,
  "detail": "A message can have at most 1000 characters.",
  "code": "MESSAGE_TOO_LONG",
  "clientMsgId": "5c1e7a54-1d0e-4a3b-9a0e-6f1f4d2b9c10"
}
```

Branch on `code` exactly as for REST: `MESSAGE_TOO_LONG`, `VALIDATION_FAILED` (with `errors[]`), `CONVERSATION_CLOSED`, `CONVERSATION_NOT_FOUND`, `IMAGE_INVALID`, `CHAT_RATE_LIMITED` (with `retryAfterSeconds`). Mark the message with that `clientMsgId` as failed and offer "Thử lại".

## Reconnect algorithm (FR-CH-07)

1. Keep `lastId`: the highest message `id` received (from the REST list, the sync call, `/user/queue/messages` or an ack).
2. When the socket is back and subscribed: `GET /conversations/sync?afterMessageId=<lastId>`. Repeat while `hasMore` is true, with the last `id` returned.
3. Show the messages by `id` (not by arrival time) and drop any `id` already shown, because a message can come both from sync and from the queue.
4. Send again every message you have no ack for, with its original `clientMsgId`.
5. On `400 INVALID_CURSOR` reload the newest page of each open conversation.

A message you send from the REST fallback is also pushed to the other member's socket, and to your own other sessions.

## Push and the socket

While you have a STOMP session open the server treats you as online and creates **no** notification for new messages. When all sessions are gone, a message becomes an in-app notification and an FCM push whose data has `deepLink` (`app://conversations/{id}`). Presence lapses 90 seconds after a session dies without a clean disconnect, so a push can follow shortly after the app is killed.

## Try it by hand

Local backend and RabbitMQ are described in [`../setup-local.md`](../setup-local.md#9-rabbitmq-chat-relay-and-firebase-push). With a token from `POST /auth/login`, any STOMP client works, for example `wscat` with a STOMP frame, or the Android emulator. The Postman collection `nearby-chat` covers the REST side.

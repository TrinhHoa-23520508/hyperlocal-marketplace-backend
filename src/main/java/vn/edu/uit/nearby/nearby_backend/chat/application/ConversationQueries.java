package vn.edu.uit.nearby.nearby_backend.chat.application;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.nearby.nearby_backend.chat.application.ConversationView.CounterpartBrief;
import vn.edu.uit.nearby.nearby_backend.chat.application.ConversationView.ListingBrief;
import vn.edu.uit.nearby.nearby_backend.chat.domain.Conversation;
import vn.edu.uit.nearby.nearby_backend.chat.domain.StoredMessage;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.ConversationRepository.ConversationRow;
import vn.edu.uit.nearby.nearby_backend.chat.infrastructure.MessageRepository;
import vn.edu.uit.nearby.nearby_backend.identity.UserSummaries;
import vn.edu.uit.nearby.nearby_backend.identity.UserSummary;
import vn.edu.uit.nearby.nearby_backend.listing.ListingLookup;
import vn.edu.uit.nearby.nearby_backend.listing.ListingSnapshot;
import vn.edu.uit.nearby.nearby_backend.shared.constant.ErrorCodes;
import vn.edu.uit.nearby.nearby_backend.shared.error.InvalidRequestException;
import vn.edu.uit.nearby.nearby_backend.shared.error.ResourceNotFoundException;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage;
import vn.edu.uit.nearby.nearby_backend.shared.web.CursorPage.Cursor;

/**
 * The read side of chat: the conversation list (A17), the chat header (A18), a
 * conversation's messages and the sync after a reconnect (FR-CH-06, FR-CH-07, FR-CH-11).
 */
@Service
public class ConversationQueries {

	/**
	 * At most this many missed messages per call; {@code hasMore} tells the app to ask
	 * again (plan reading R4).
	 */
	public static final int SYNC_PAGE_SIZE = 200;

	private static final String CLOSED_NOTICE = "Cuộc trò chuyện này đã đóng, bạn không thể gửi thêm tin nhắn.";

	private final ConversationRepository conversations;

	private final MessageRepository messages;

	private final ListingLookup listings;

	private final UserSummaries users;

	private final SendGuard guard;

	private final MessageViews views;

	ConversationQueries(ConversationRepository conversations, MessageRepository messages, ListingLookup listings,
			UserSummaries users, SendGuard guard, MessageViews views) {
		this.conversations = conversations;
		this.messages = messages;
		this.listings = listings;
		this.users = users;
		this.guard = guard;
		this.views = views;
	}

	/**
	 * @param hasMore true when more messages were missed than this call returns; ask
	 * again with the last id received
	 */
	public record SyncResult(List<MessageView> messages, boolean hasMore) {
	}

	@Transactional(readOnly = true)
	public CursorPage<ConversationView> list(UUID userId, @Nullable String cursor, @Nullable Integer requestedLimit) {
		int limit = CursorPage.clampLimit(requestedLimit);
		List<ConversationRow> fetched = conversations.pageOf(userId, Cursor.decode(cursor).orElse(null), limit + 1);
		CursorPage<ConversationRow> page = CursorPage.of(fetched, limit,
				row -> new Cursor(row.sortAt(), row.conversation().id()));
		List<ConversationView> items = viewsOf(userId, page.items());
		return new CursorPage<>(items, page.nextCursor(), page.hasMore());
	}

	/**
	 * @throws ResourceNotFoundException {@code CONVERSATION_NOT_FOUND} for a stranger or
	 * an unknown id, so a conversation's existence stays private (FR-ID-18)
	 */
	@Transactional(readOnly = true)
	public ConversationView detail(UUID userId, UUID conversationId) {
		ConversationRow row = conversations.findRow(conversationId, userId)
			.orElseThrow(
					() -> new ResourceNotFoundException(ErrorCodes.CONVERSATION_NOT_FOUND, "Conversation not found."));
		return viewsOf(userId, List.of(row)).getFirst();
	}

	/**
	 * Which page of a conversation's messages to read.
	 *
	 * @param before the page before this message id (scrolling up)
	 * @param after the page after this message id (catching up inside one conversation)
	 * @param limit page size; the default when null, never above the maximum
	 */
	public record MessageWindow(@Nullable Long before, @Nullable Long after, @Nullable Integer limit) {

		public static MessageWindow newest() {
			return new MessageWindow(null, null, null);
		}

		public static MessageWindow newest(int limit) {
			return new MessageWindow(null, null, limit);
		}

	}

	/**
	 * Messages of one conversation, oldest first. Neither bound: the newest page;
	 * {@code before}: the page before that id; {@code after}: the page after that id.
	 */
	@Transactional(readOnly = true)
	public List<MessageView> messages(UUID userId, UUID conversationId, MessageWindow window) {
		if (window.before() != null && window.after() != null) {
			throw InvalidRequestException.invalidField("before", "Use either before or after, not both.");
		}
		requireMember(userId, conversationId);
		List<StoredMessage> page = messages.page(conversationId, window.before(), window.after(),
				CursorPage.clampLimit(window.limit()));
		return views.of(page);
	}

	/**
	 * FR-CH-07: everything the caller missed in all their conversations, in id order.
	 * @throws InvalidRequestException {@code INVALID_CURSOR} when the checkpoint is above
	 * the newest message: the app reloads the newest page of each conversation
	 */
	@Transactional(readOnly = true)
	public SyncResult sync(UUID userId, long afterMessageId) {
		if (afterMessageId < 0 || afterMessageId > messages.newestId()) {
			throw new InvalidRequestException(ErrorCodes.INVALID_CURSOR, "The sync checkpoint is unknown.");
		}
		List<StoredMessage> missed = messages.missedBy(userId, afterMessageId, SYNC_PAGE_SIZE + 1);
		boolean hasMore = missed.size() > SYNC_PAGE_SIZE;
		return new SyncResult(views.of(hasMore ? missed.subList(0, SYNC_PAGE_SIZE) : missed), hasMore);
	}

	private Conversation requireMember(UUID userId, UUID conversationId) {
		return conversations.findById(conversationId)
			.filter(candidate -> candidate.isMember(userId))
			.orElseThrow(
					() -> new ResourceNotFoundException(ErrorCodes.CONVERSATION_NOT_FOUND, "Conversation not found."));
	}

	private List<ConversationView> viewsOf(UUID userId, List<ConversationRow> rows) {
		Set<UUID> listingIds = new HashSet<>();
		Set<UUID> counterpartIds = new HashSet<>();
		for (ConversationRow row : rows) {
			listingIds.add(row.conversation().listingId());
			counterpartIds.add(row.conversation().counterpartOf(userId));
		}
		Map<UUID, ListingSnapshot> listingsById = listings.findAll(listingIds);
		Map<UUID, UserSummary> usersById = users.summariesOf(counterpartIds);
		return rows.stream().map(row -> viewOf(userId, row, listingsById, usersById)).toList();
	}

	private ConversationView viewOf(UUID userId, ConversationRow row, Map<UUID, ListingSnapshot> listingsById,
			Map<UUID, UserSummary> usersById) {
		Conversation conversation = row.conversation();
		ListingSnapshot listing = listingsById.get(conversation.listingId());
		UserSummary counterpart = usersById.get(conversation.counterpartOf(userId));
		boolean sendOpen = guard.lockOf(conversation).isEmpty();
		return new ConversationView(conversation.id(), listing == null ? null : listingBriefOf(listing),
				counterpart == null ? null
						: new CounterpartBrief(counterpart.id(), counterpart.displayName(), counterpart.avatarUrl(),
								counterpart.deleted()),
				row.lastMessagePreview(), row.lastMessageAt(), row.unreadCount(), sendOpen,
				sendOpen ? null : CLOSED_NOTICE, row.sortAt());
	}

	private static ListingBrief listingBriefOf(ListingSnapshot listing) {
		return new ListingBrief(listing.id(), listing.title(), listing.status(), listing.priceVnd(),
				listing.coverThumbnailUrl());
	}

}

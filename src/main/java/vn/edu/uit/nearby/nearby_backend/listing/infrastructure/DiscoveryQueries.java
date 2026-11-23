package vn.edu.uit.nearby.nearby_backend.listing.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import vn.edu.uit.nearby.nearby_backend.listing.domain.ItemCondition;
import vn.edu.uit.nearby.nearby_backend.listing.domain.ListingStatus;
import vn.edu.uit.nearby.nearby_backend.listing.domain.RankWeights;
import vn.edu.uit.nearby.nearby_backend.listing.domain.RelevanceScore;
import vn.edu.uit.nearby.nearby_backend.listing.domain.SearchCursor;
import vn.edu.uit.nearby.nearby_backend.listing.domain.SellerTier;
import vn.edu.uit.nearby.nearby_backend.listing.domain.SortOrder;
import vn.edu.uit.nearby.nearby_backend.shared.geo.GeoPoint;

/**
 * Read-only discovery queries (FR-DC-01..07) on {@code listings.public_location} only;
 * the exact pin is never selected (FR-LS-03). Distances use the mean-Earth sphere, as the
 * area rules do. Pages are keyset pages on (rank, {@code feed_at}, {@code id}), so a
 * listing inserted meanwhile never shifts a page (FR-DC-01).
 */
@Repository
public class DiscoveryQueries {

	/**
	 * Map clusters are about this many screen pixels wide (FR-DC-07 "lưới phụ thuộc mức
	 * zoom").
	 */
	static final int CLUSTER_CELL_PX = 60;

	private static final double WEB_MERCATOR_WORLD_METERS = 40_075_016.686;

	private static final int TILE_PX = 256;

	private static final int MAX_CLUSTER_ATTEMPTS = 12;

	private static final String CENTER = "ST_SetSRID(ST_MakePoint(:centerLongitude, :centerLatitude), 4326)::geography";

	private static final String VIEWPORT = "ST_MakeEnvelope(:west, :south, :east, :north, 4326)::geography";

	private final NamedParameterJdbcTemplate jdbc;

	public DiscoveryQueries(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * FR-DC-01 (feed, NEWEST) and FR-DC-02/03 (search, any order), one page of
	 * {@code limit} rows.
	 */
	public List<ListingRow> search(SearchArea area, ListingFilter filter, SortOrder sort, Instant rankedAt,
			@Nullable SearchCursor after, int limit, RelevanceScore relevance) {
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		String base = """
				SELECT l.id, l.seller_id, l.title, l.price, l.status, l.locality_name, l.feed_at, l.seller_tier,
				       l.seller_score, ST_Distance(l.public_location, %s, false) AS distance
				FROM listings l
				WHERE ST_DWithin(l.public_location, %s, :radiusMeters, false) AND %s
				""".formatted(CENTER, CENTER, conditions(filter, parameters));
		parameters.addValue("centerLatitude", area.center().latitude())
			.addValue("centerLongitude", area.center().longitude())
			.addValue("radiusMeters", area.radiusMeters())
			.addValue("limit", limit);
		String ranked = "SELECT b.*, %s AS rank FROM (%s) b"
			.formatted(rankExpression(sort, rankedAt, area, relevance.weights(), parameters), base);
		String sql = "SELECT * FROM (%s) r %s ORDER BY %s LIMIT :limit".formatted(ranked,
				after == null ? "" : "WHERE " + keysetAfter(sort, after, parameters), orderBy(sort));
		return jdbc.query(sql, parameters, DiscoveryQueries::toRow);
	}

	public long countInViewport(ListingFilter filter, Viewport viewport) {
		MapSqlParameterSource parameters = viewportParameters(viewport);
		String sql = "SELECT count(*) FROM listings l WHERE l.public_location && %s AND %s".formatted(VIEWPORT,
				conditions(filter, parameters));
		Long count = jdbc.queryForObject(sql, parameters, Long.class);
		return count == null ? 0 : count;
	}

	/**
	 * FR-DC-07 when the viewport holds few listings: one marker per listing.
	 */
	public List<MapPoint> pointsInViewport(ListingFilter filter, Viewport viewport, int limit) {
		MapSqlParameterSource parameters = viewportParameters(viewport).addValue("limit", limit);
		String sql = """
				SELECT l.id, l.price, l.status, ST_Y(l.public_location::geometry) AS latitude,
				       ST_X(l.public_location::geometry) AS longitude
				FROM listings l WHERE l.public_location && %s AND %s
				ORDER BY l.feed_at DESC, l.id DESC LIMIT :limit
				""".formatted(VIEWPORT, conditions(filter, parameters));
		return jdbc.query(sql, parameters,
				(row, index) -> new MapPoint(row.getObject("id", UUID.class), row.getLong("price"),
						ListingStatus.valueOf(row.getString("status")),
						new GeoPoint(row.getDouble("latitude"), row.getDouble("longitude"))));
	}

	/**
	 * FR-DC-07 otherwise: counts per Web Mercator grid cell sized from the zoom level,
	 * coarser cells until at most {@code maxClusters} remain.
	 */
	public List<MapCluster> clustersInViewport(ListingFilter filter, Viewport viewport, int zoom, int maxClusters) {
		double cellMeters = CLUSTER_CELL_PX * WEB_MERCATOR_WORLD_METERS / (TILE_PX * Math.pow(2, zoom));
		List<MapCluster> clusters = clusters(filter, viewport, cellMeters);
		for (int attempt = 1; clusters.size() > maxClusters && attempt < MAX_CLUSTER_ATTEMPTS; attempt++) {
			cellMeters *= 2;
			clusters = clusters(filter, viewport, cellMeters);
		}
		return clusters.size() > maxClusters ? clusters.subList(0, maxClusters) : clusters;
	}

	private List<MapCluster> clusters(ListingFilter filter, Viewport viewport, double cellMeters) {
		MapSqlParameterSource parameters = viewportParameters(viewport).addValue("cellMeters", cellMeters);
		String sql = """
				SELECT count(*) AS listings,
				       ST_Y(ST_Transform(ST_Centroid(ST_Collect(ST_Transform(l.public_location::geometry, 3857))), 4326))
				         AS latitude,
				       ST_X(ST_Transform(ST_Centroid(ST_Collect(ST_Transform(l.public_location::geometry, 3857))), 4326))
				         AS longitude
				FROM listings l WHERE l.public_location && %s AND %s
				GROUP BY ST_SnapToGrid(ST_Transform(l.public_location::geometry, 3857), :cellMeters)
				ORDER BY listings DESC
				"""
			.formatted(VIEWPORT, conditions(filter, parameters));
		return jdbc.query(sql, parameters, (row, index) -> new MapCluster(row.getLong("listings"),
				new GeoPoint(row.getDouble("latitude"), row.getDouble("longitude"))));
	}

	private static String conditions(ListingFilter filter, MapSqlParameterSource parameters) {
		List<String> conditions = new ArrayList<>();
		conditions.add("l.status IN (:statuses)");
		parameters.addValue("statuses", names(filter.statuses()));
		if (filter.keyword() != null) {
			conditions.add("(l.search_vector @@ plainto_tsquery('simple', f_unaccent(:keyword))"
					+ " OR f_unaccent(lower(:keyword)) <% f_unaccent(lower(l.title)))");
			parameters.addValue("keyword", filter.keyword());
		}
		if (filter.categoryId() != null) {
			conditions.add("l.category_id = :categoryId");
			parameters.addValue("categoryId", filter.categoryId());
		}
		if (filter.minPriceVnd() != null) {
			conditions.add("l.price >= :minPrice");
			parameters.addValue("minPrice", filter.minPriceVnd());
		}
		if (filter.maxPriceVnd() != null) {
			conditions.add("l.price <= :maxPrice");
			parameters.addValue("maxPrice", filter.maxPriceVnd());
		}
		if (!filter.conditions().isEmpty()) {
			conditions.add("l.condition IN (:conditions)");
			parameters.addValue("conditions", names(filter.conditions()));
		}
		if (filter.freeOnly()) {
			conditions.add("l.is_free");
		}
		if (!filter.excludedSellers().isEmpty()) {
			conditions.add("l.seller_id NOT IN (:excludedSellers)");
			parameters.addValue("excludedSellers", filter.excludedSellers());
		}
		return String.join(" AND ", conditions);
	}

	/**
	 * SRS appendix D, with freshness measured at {@code rankedAt} so every page of one
	 * search ranks alike.
	 */
	private static String rankExpression(SortOrder sort, Instant rankedAt, SearchArea area, RankWeights weights,
			MapSqlParameterSource parameters) {
		return switch (sort) {
			case NEWEST -> "0::float8";
			case NEAREST -> "b.distance";
			case RELEVANT -> {
				parameters.addValue("distanceWeight", weights.distance())
					.addValue("reputationWeight", weights.reputation())
					.addValue("freshnessWeight", weights.freshness())
					.addValue("baselineScore", RelevanceScore.BASELINE_SELLER_SCORE)
					.addValue("rankedAt", Timestamp.from(rankedAt))
					.addValue("freshnessSeconds", (double) RelevanceScore.FRESHNESS_SCALE.toSeconds())
					.addValue("rankRadius", (double) area.radiusMeters());
				yield """
						(:distanceWeight * (1 - LEAST(b.distance / :rankRadius, 1))
						 + :reputationWeight * COALESCE(b.seller_score, :baselineScore) / 100
						 + :freshnessWeight * EXP(-GREATEST(EXTRACT(EPOCH FROM (CAST(:rankedAt AS timestamptz) - b.feed_at)), 0)
						                        / :freshnessSeconds))::float8""";
			}
		};
	}

	private static String keysetAfter(SortOrder sort, SearchCursor after, MapSqlParameterSource parameters) {
		parameters.addValue("afterRank", after.rank())
			.addValue("afterFeedAt", Timestamp.from(after.feedAt()))
			.addValue("afterId", after.id());
		String sameRankThenOlder = "(r.feed_at < :afterFeedAt OR (r.feed_at = :afterFeedAt AND r.id < :afterId))";
		return switch (sort) {
			case NEWEST -> sameRankThenOlder;
			case NEAREST -> "(r.rank > :afterRank OR (r.rank = :afterRank AND " + sameRankThenOlder + "))";
			case RELEVANT -> "(r.rank < :afterRank OR (r.rank = :afterRank AND " + sameRankThenOlder + "))";
		};
	}

	private static String orderBy(SortOrder sort) {
		return switch (sort) {
			case NEWEST -> "r.feed_at DESC, r.id DESC";
			case NEAREST -> "r.rank ASC, r.feed_at DESC, r.id DESC";
			case RELEVANT -> "r.rank DESC, r.feed_at DESC, r.id DESC";
		};
	}

	private static MapSqlParameterSource viewportParameters(Viewport viewport) {
		return new MapSqlParameterSource().addValue("west", viewport.west())
			.addValue("south", viewport.south())
			.addValue("east", viewport.east())
			.addValue("north", viewport.north());
	}

	private static ListingRow toRow(ResultSet row, int index) throws SQLException {
		String tier = row.getString("seller_tier");
		return new ListingRow(row.getObject("id", UUID.class), row.getObject("seller_id", UUID.class),
				row.getString("title"), row.getLong("price"), ListingStatus.valueOf(row.getString("status")),
				row.getString("locality_name"), row.getTimestamp("feed_at").toInstant(),
				tier == null ? SellerTier.NEW : SellerTier.valueOf(tier), row.getDouble("distance"),
				row.getDouble("rank"));
	}

	private static List<String> names(Collection<? extends Enum<?>> values) {
		return values.stream().map(Enum::name).toList();
	}

	/**
	 * The circle searched (FR-DC-04): the distance is measured from {@code center}.
	 */
	public record SearchArea(GeoPoint center, int radiusMeters) {
	}

	/**
	 * What to find: listings in {@code statuses} matching every given filter.
	 *
	 * @param keyword matched without accents or case on the title and description
	 * @param excludedSellers sellers the viewer blocked or was blocked by (FR-DC-01)
	 */
	public record ListingFilter(Set<ListingStatus> statuses, @Nullable String keyword, @Nullable Short categoryId,
			@Nullable Long minPriceVnd, @Nullable Long maxPriceVnd, Set<ItemCondition> conditions, boolean freeOnly,
			Set<UUID> excludedSellers) {

		public ListingFilter {
			statuses = Set.copyOf(statuses);
			conditions = Set.copyOf(conditions);
			excludedSellers = Set.copyOf(excludedSellers);
		}

	}

	/**
	 * @param distanceMeters from the search center to the public location
	 * @param rank the sort key of the page cursor (distance, relevance score, or 0)
	 */
	public record ListingRow(UUID id, UUID sellerId, String title, long priceVnd, ListingStatus status,
			@Nullable String localityName, Instant feedAt, SellerTier sellerTier, double distanceMeters, double rank) {
	}

	public record Viewport(double south, double west, double north, double east) {
	}

	public record MapPoint(UUID listingId, long priceVnd, ListingStatus status, GeoPoint publicPoint) {
	}

	public record MapCluster(long listings, GeoPoint center) {
	}

}

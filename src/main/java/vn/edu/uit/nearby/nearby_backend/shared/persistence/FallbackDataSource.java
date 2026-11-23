package vn.edu.uit.nearby.nearby_backend.shared.persistence;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.datasource.AbstractDataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Hands out connections of a preferred data source, and of the fallback one while the
 * preferred one cannot connect (FR-DC-12: "đường đọc không khả dụng: quay về primary").
 */
@Slf4j
class FallbackDataSource extends AbstractDataSource {

	private final DataSource preferred;

	private final DataSource fallback;

	FallbackDataSource(DataSource preferred, DataSource fallback) {
		this.preferred = preferred;
		this.fallback = fallback;
	}

	@Override
	public Connection getConnection() throws SQLException {
		try {
			return preferred.getConnection();
		}
		catch (SQLException ex) {
			logFallback(ex);
			return fallback.getConnection();
		}
	}

	@Override
	public Connection getConnection(@Nullable String username, @Nullable String password) throws SQLException {
		try {
			return preferred.getConnection(username, password);
		}
		catch (SQLException ex) {
			logFallback(ex);
			return fallback.getConnection(username, password);
		}
	}

	private static void logFallback(SQLException ex) {
		log.warn("Read path unavailable, using the primary database: {}", ex.getMessage());
	}

}

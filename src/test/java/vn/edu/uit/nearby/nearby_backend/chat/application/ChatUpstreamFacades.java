package vn.edu.uit.nearby.nearby_backend.chat.application;

import static org.mockito.Mockito.mock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import vn.edu.uit.nearby.nearby_backend.areaverification.InteractionRange;
import vn.edu.uit.nearby.nearby_backend.identity.AccountStanding;
import vn.edu.uit.nearby.nearby_backend.identity.TradingGate;
import vn.edu.uit.nearby.nearby_backend.identity.UserBlocks;
import vn.edu.uit.nearby.nearby_backend.listing.ListingCounters;
import vn.edu.uit.nearby.nearby_backend.listing.ListingLookup;

/**
 * The facades chat asks of other modules, as mocks that win over the real beans where
 * chat injects them. The real beans stay for everyone else, because one bean such as the
 * listing module's implements several of these interfaces. Tests of chat that do not need
 * real users and listings import this class, and share one cached Spring context.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ChatUpstreamFacades {

	@Bean
	@Primary
	ListingLookup listingLookupMock() {
		return mock(ListingLookup.class);
	}

	@Bean
	@Primary
	ListingCounters listingCountersMock() {
		return mock(ListingCounters.class);
	}

	@Bean
	@Primary
	UserBlocks userBlocksMock() {
		return mock(UserBlocks.class);
	}

	@Bean
	@Primary
	AccountStanding accountStandingMock() {
		return mock(AccountStanding.class);
	}

	@Bean
	@Primary
	TradingGate tradingGateMock() {
		return mock(TradingGate.class);
	}

	@Bean
	@Primary
	InteractionRange interactionRangeMock() {
		return mock(InteractionRange.class);
	}

}

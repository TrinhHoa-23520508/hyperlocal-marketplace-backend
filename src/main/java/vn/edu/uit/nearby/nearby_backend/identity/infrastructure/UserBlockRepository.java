package vn.edu.uit.nearby.nearby_backend.identity.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import vn.edu.uit.nearby.nearby_backend.identity.domain.UserBlock;

public interface UserBlockRepository extends JpaRepository<UserBlock, UserBlock.Key> {

	boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

	@Modifying
	@Query("delete from UserBlock b where b.blockerId = :blockerId and b.blockedId = :blockedId")
	int deleteBlock(UUID blockerId, UUID blockedId);

	@Query("""
			select count(b) > 0 from UserBlock b
			where (b.blockerId = :userId and b.blockedId = :otherUserId)
			   or (b.blockerId = :otherUserId and b.blockedId = :userId)
			""")
	boolean existsEitherWay(UUID userId, UUID otherUserId);

	@Query("""
			select case when b.blockerId = :userId then b.blockedId else b.blockerId end
			from UserBlock b where b.blockerId = :userId or b.blockedId = :userId
			""")
	Set<UUID> findCounterpartsOf(UUID userId);

	@Query("""
			select b from UserBlock b where b.blockerId = :blockerId
			order by b.createdAt desc, b.blockedId desc
			""")
	List<UserBlock> findNewestOf(UUID blockerId, Limit limit);

	@Query("""
			select b from UserBlock b where b.blockerId = :blockerId
			  and (b.createdAt < :createdAt or (b.createdAt = :createdAt and b.blockedId < :blockedId))
			order by b.createdAt desc, b.blockedId desc
			""")
	List<UserBlock> findOlderOf(UUID blockerId, Instant createdAt, UUID blockedId, Limit limit);

}

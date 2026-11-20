package com.formasiserver.domain.user.repository;

import com.formasiserver.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByKakaoId(String kakaoId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM users
                WHERE lower(nickname COLLATE "C") = lower(CAST(:nickname AS text) COLLATE "C")
            )
            """, nativeQuery = true)
    boolean existsByNicknameIgnoringCase(@Param("nickname") String nickname);
}

package com.formasiserver.domain.helptime.repository;

import com.formasiserver.domain.helptime.entity.TimeAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeAccountRepository extends JpaRepository<TimeAccount, Long> {

    Optional<TimeAccount> findByUser_UserId(UUID userId);
}

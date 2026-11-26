package com.formasiserver.domain.helptime.repository;

import com.formasiserver.domain.helptime.entity.TimeAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface TimeAccountRepository extends JpaRepository<TimeAccount, Long> {

    Optional<TimeAccount> findByUser_UserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TimeAccount> findForUpdateByUser_UserId(UUID userId);
}

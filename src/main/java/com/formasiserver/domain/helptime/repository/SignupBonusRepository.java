package com.formasiserver.domain.helptime.repository;

import com.formasiserver.domain.helptime.entity.SignupBonus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignupBonusRepository extends JpaRepository<SignupBonus, Long> {

    Optional<SignupBonus> findByUser_UserId(UUID userId);
}

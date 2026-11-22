package com.formasiserver.domain.user.repository;

import com.formasiserver.domain.user.entity.ProfileChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileChangeLogRepository extends JpaRepository<ProfileChangeLog, Long> {
}

package com.formasiserver.domain.helptime.repository;

import com.formasiserver.domain.helptime.entity.TimeTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeTransferRepository extends JpaRepository<TimeTransfer, Long> {
}

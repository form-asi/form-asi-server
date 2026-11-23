package com.formasiserver.domain.helptime.repository;

import com.formasiserver.domain.helptime.entity.TimeTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeTransactionRepository extends JpaRepository<TimeTransaction, Long>, TimeTransactionQueryRepository {
}

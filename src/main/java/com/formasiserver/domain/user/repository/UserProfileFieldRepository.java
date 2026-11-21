package com.formasiserver.domain.user.repository;

import com.formasiserver.domain.user.entity.UserProfileField;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileFieldRepository extends JpaRepository<UserProfileField, Long> {
}

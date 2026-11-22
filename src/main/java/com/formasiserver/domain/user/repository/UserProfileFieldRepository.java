package com.formasiserver.domain.user.repository;

import com.formasiserver.domain.user.entity.UserProfileField;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileFieldRepository extends JpaRepository<UserProfileField, Long> {

    List<UserProfileField> findAllByUser_UserIdOrderById(UUID userId);
}

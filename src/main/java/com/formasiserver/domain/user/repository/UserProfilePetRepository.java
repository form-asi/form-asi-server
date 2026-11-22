package com.formasiserver.domain.user.repository;

import com.formasiserver.domain.user.entity.UserProfilePet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfilePetRepository extends JpaRepository<UserProfilePet, Long> {

    List<UserProfilePet> findAllByUser_UserIdOrderById(UUID userId);
}

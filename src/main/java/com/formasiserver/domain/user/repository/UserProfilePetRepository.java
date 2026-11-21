package com.formasiserver.domain.user.repository;

import com.formasiserver.domain.user.entity.UserProfilePet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfilePetRepository extends JpaRepository<UserProfilePet, Long> {
}

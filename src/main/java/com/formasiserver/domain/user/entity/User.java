package com.formasiserver.domain.user.entity;

import com.formasiserver.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_kakao_id", columnNames = "kakao_id"))
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "kakao_id", nullable = false, length = 100, updatable = false)
    private String kakaoId;

    @Column(name = "birth_date")
    private @Nullable LocalDate birthDate;

    @Column(name = "nickname", length = 10)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled;

    public static User of(String kakaoId) {
        User user = new User();
        user.kakaoId = kakaoId;
        user.role = UserRole.USER;
        user.status = UserStatus.PENDING;
        user.notificationEnabled = true;
        return user;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

}

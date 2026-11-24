package com.formasiserver.domain.survey.entity;

import com.formasiserver.domain.user.entity.User;
import com.formasiserver.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "survey")
public class Survey extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "external_url", nullable = false, columnDefinition = "text")
    private String externalUrl;

    @Column(name = "target_count", nullable = false)
    private int targetCount;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "pay_minutes", nullable = false)
    private int payMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SurveyStatus status;

    @Column(name = "target_description", length = 200)
    private String targetDescription;

    @Column(name = "reward_description", length = 100)
    private String rewardDescription;

    @Column(length = 700)
    private String introduction;

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Builder
    private Survey(User user, String title, String externalUrl, int targetCount, int durationMinutes,
            String targetDescription, String rewardDescription, String introduction, Integer minAge, Integer maxAge) {
        this.user = user;
        this.title = title;
        this.externalUrl = externalUrl;
        this.targetCount = targetCount;
        this.durationMinutes = durationMinutes;
        this.payMinutes = durationMinutes;
        this.status = SurveyStatus.OPEN;
        this.targetDescription = targetDescription;
        this.rewardDescription = rewardDescription;
        this.introduction = introduction;
        this.minAge = minAge;
        this.maxAge = maxAge;
    }
}

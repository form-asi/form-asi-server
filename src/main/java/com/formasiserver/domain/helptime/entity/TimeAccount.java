package com.formasiserver.domain.helptime.entity;

import com.formasiserver.domain.survey.entity.Survey;
import com.formasiserver.domain.user.entity.User;
import com.formasiserver.global.entity.BaseTimeEntity;
import com.formasiserver.global.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "time_account")
public class TimeAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", unique = true)
    private Survey survey;

    @Column(name = "owner_type", nullable = false, length = 20)
    private String ownerType;

    @Column(nullable = false)
    private int balance;

    @Column(name = "bonus_balance", nullable = false)
    private int bonusBalance;

    public long getBalanceMinutes() {
        return (long) balance + bonusBalance;
    }

    public TimeAccount reserveForSurvey(Survey survey, int requiredMinutes, boolean bonusActive) {
        int usableBonusBalance = bonusActive ? bonusBalance : Math.min(0, bonusBalance);
        long availableMinutes = (long) balance + usableBonusBalance;
        if (availableMinutes < requiredMinutes) {
            throw ApiException.insufficientHelpTime(requiredMinutes, availableMinutes);
        }

        int reservedBonus = Math.min(requiredMinutes, Math.max(0, usableBonusBalance));
        int reservedRegular = requiredMinutes - reservedBonus;
        balance -= reservedRegular;
        bonusBalance -= reservedBonus;

        TimeAccount reserve = new TimeAccount();
        reserve.ownerType = "SURVEY";
        reserve.survey = survey;
        reserve.balance = reservedRegular;
        reserve.bonusBalance = reservedBonus;
        return reserve;
    }
}

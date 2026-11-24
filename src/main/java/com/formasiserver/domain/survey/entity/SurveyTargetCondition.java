package com.formasiserver.domain.survey.entity;

import com.formasiserver.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "survey_target_condition", uniqueConstraints = @UniqueConstraint(
        name = "uk_survey_target_condition", columnNames = {"survey_id", "item", "value"}))
public class SurveyTargetCondition extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @Column(nullable = false, length = 40)
    private String item;

    @Column(nullable = false, length = 40)
    private String value;

    public static SurveyTargetCondition of(Survey survey, String item, String value) {
        SurveyTargetCondition condition = new SurveyTargetCondition();
        condition.survey = survey;
        condition.item = item;
        condition.value = value;
        return condition;
    }
}

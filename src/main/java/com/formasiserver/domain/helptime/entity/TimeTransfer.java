package com.formasiserver.domain.helptime.entity;

import com.formasiserver.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "time_transfer")
public class TimeTransfer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(name = "survey_id")
    private Long surveyId;

    public static TimeTransfer forSurveyRegistration(long surveyId) {
        TimeTransfer transfer = new TimeTransfer();
        transfer.type = "SURVEY_REGISTERED";
        transfer.surveyId = surveyId;
        return transfer;
    }
}

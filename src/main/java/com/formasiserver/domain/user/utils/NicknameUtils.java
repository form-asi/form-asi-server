package com.formasiserver.domain.user.utils;

import java.util.regex.Pattern;

public final class NicknameUtils {

    public static final String FORMAT = "[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9]{2,10}";
    public static final String FORMAT_MESSAGE = "닉네임은 한글, 영문, 숫자로 2~10자를 입력해주세요.";

    private static final Pattern PATTERN = Pattern.compile(FORMAT);

    private NicknameUtils() {
    }

    public static boolean isValid(String nickname) {
        return PATTERN.matcher(nickname).matches();
    }
}

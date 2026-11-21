# formasi-server
폼앗이 서버

## 카카오 로그인·온보딩 로컬 확인

Docker를 실행한 뒤 프로젝트 루트에서 PostgreSQL과 Valkey를 시작합니다.

```bash
docker compose up -d postgres valkey
```

카카오디벨로퍼스에서 카카오 로그인을 활성화하고, REST API 키의 Redirect URI에 `http://localhost:8080/login/oauth2/code/kakao`를 등록합니다. 같은 REST API 키의 클라이언트 시크릿을 사용합니다. [카카오 로그인 설정](https://developers.kakao.com/docs/ko/kakaologin/prerequisite)

```bash
export SPRING_PROFILES_ACTIVE=local
export KAKAO_CLIENT_ID='<REST API 키>'
export KAKAO_CLIENT_SECRET='<클라이언트 시크릿>'
export KAKAO_REDIRECT_URI='http://localhost:8080/login/oauth2/code/kakao'
export AUTH_LOGIN_REDIRECT_URL='http://localhost:8080/api-docs'
export AUTH_ALLOWED_ORIGINS='http://localhost:8080'
export JWT_SECRET_KEY="$(openssl rand -base64 32)"
./gradlew bootRun
```

1. [카카오 로그인 시작](http://localhost:8080/api/v1/auth/login/kakao)을 브라우저에서 엽니다. 성공하면 브라우저에 `refresh_token` 쿠키가 설정되고 [Swagger UI](http://localhost:8080/api-docs)로 이동합니다. 실패하면 이동 URL에 `error`가 붙으므로 개발자 도구의 네트워크 탭에서 콜백 응답의 `Location`을 확인합니다.
2. `POST /api/v1/auth/refresh`로 Access 토큰을 발급받습니다. 프론트에서 API를 호출할 때 `credentials: "include"`를 설정하고, 해당 프론트 Origin을 `AUTH_ALLOWED_ORIGINS`에 등록합니다. Swagger UI에서는 같은 브라우저의 로그인 쿠키를 사용합니다.
3. Swagger의 **Authorize**에 `data.accessToken` 값을 입력하고 `GET /api/v1/users/me`를 호출합니다.
4. `PENDING`이면 선택지와 닉네임 사용 가능 여부를 조회합니다. 양력 `birthDate`와 프로필을 `POST /api/v1/users/me/onboarding`으로 제출한 뒤 같은 Access 토큰으로 내 정보를 조회해 `ACTIVE`를 확인합니다.
5. `POST /api/v1/auth/logout` 후 현재 Refresh 쿠키가 삭제되는지 확인합니다.

프론트에서 여러 API 요청의 Access 토큰이 동시에 만료되면 탭 안에서 하나의 재발급 요청을 공유합니다. 재발급이 `401`이면 같은 Refresh로 반복 요청하지 않고 다시 로그인합니다.

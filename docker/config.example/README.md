# 프로덕션 설정 템플릿

이 디렉터리는 **템플릿**이다. 실제 값은 private 설정 리포에 두고, 배포 시 러너가 `docker/config/`로 클론한다.

## private 설정 리포 만들기

```bash
gh repo create givemeticket/givemeticket-submodule --private --clone
cd givemeticket-submodule
```

아래 3개 파일을 두고 `CHANGE_ME_*`를 실제 값으로 채운 뒤 커밋·push 한다.

| 파일 | 쓰임 |
| --- | --- |
| `application-prod.yml` | backend 컨테이너 `/app/config/`로 마운트. base `application.yml`을 덮어쓴다 |
| `mysql.env` | mysql 서비스가 `env_file`로 참조 |
| `grafana.env` | grafana 관리자 계정 |

`mysql.env`의 `MYSQL_PASSWORD`와 `application-prod.yml`의 `spring.datasource.password`는 **같은 값**이어야 한다. 같은 DB 계정을 가리킨다.

## 리포·시크릿 연결

| 종류 | 이름 | 값 |
| --- | --- | --- |
| Secret | `SUBMODULE_KEY` | 설정 리포에 Contents:Read 권한이 있는 PAT |
| Secret | `DOCKER_USERNAME` / `DOCKER_PASSWORD` | Docker Hub 계정 |
| Variable | `CONFIG_REPO` | 설정 리포 (기본 `givemeticket/givemeticket-submodule`) |
| Variable | `DEPLOY_DIR` | 서버 배포 경로 (기본 `~/givemeticket-prod`) |

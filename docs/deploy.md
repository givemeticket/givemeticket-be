# 배포

`main`에 머지되면 GitHub Actions가 변경된 서비스의 이미지를 Docker Hub로 push하고, self-hosted 러너가 서버에서 `deploy.sh`를 실행한다.

```
push to main
  └─ changes    변경 경로 판별 (backend / payment-mock / deploy-files)
      ├─ build-backend        변경 시에만 → Docker Hub push
      ├─ build-payment-mock   변경 시에만 → Docker Hub push
      └─ deploy               self-hosted 러너
           ├─ docker/config 서브모듈 최신 설정 반입
           ├─ compose·deploy.sh·observability 서버 반영
           └─ deploy.sh <service>
```

`workflow_dispatch`로 서비스를 골라 수동 재배포할 수 있다. `all`은 두 이미지를 모두 빌드·push 한다.

**첫 배포는 반드시 `workflow_dispatch`로 `all`을 돌린다.** Docker Hub에 이미지가 아직 없는 상태에서 배포만 실행되면 `docker compose pull`이 실패한다.

## 설정 분리

| | 로컬 | 프로덕션 |
| --- | --- | --- |
| 설정 위치 | `.env` (`.env.example` 복사) | `docker/config` 서브모듈 |
| compose | `docker-compose.yml` | `docker/docker-compose.prod.yml` |
| Grafana | 익명 Admin | 관리자 계정 필요, 익명 차단 |

프로덕션 설정은 [givemeticket-submodule](https://github.com/givemeticket/givemeticket-submodule)(private)에 둔다. 템플릿은 `docker/config.example/` 참고.

| 파일 | 쓰임 |
| --- | --- |
| `application-prod.yml` | backend 컨테이너 `/app/config/`로 마운트. base `application.yml`을 덮어쓴다 |
| `mysql.env` | mysql 서비스가 `env_file`로 참조 |
| `grafana.env` | grafana 관리자 계정 |

`mysql.env`의 `MYSQL_USER`·`MYSQL_PASSWORD`·`MYSQL_DATABASE`는 `application-prod.yml`의 `username`·`password`·URL의 DB 이름과 각각 같아야 한다.

## 최초 설정

**1. 시크릿·변수 등록**

| 종류 | 이름 | 값 |
| --- | --- | --- |
| Secret | `DOCKER_USERNAME` / `DOCKER_PASSWORD` | Docker Hub 계정 |
| Secret | `SUBMODULE_KEY` | 설정 리포 Contents:Read 권한 PAT |
| Variable | `DEPLOY_DIR` | (선택) 서버 배포 경로. 기본 `~/givemeticket-prod` |
| Variable | `DOCKER_NAMESPACE` | (선택) Docker Hub 네임스페이스. 기본값은 `DOCKER_USERNAME` |

비밀값은 반드시 **Secrets** 탭에 넣는다. Variables는 평문으로 저장되고 로그에도 마스킹 없이 찍힌다.

**2. self-hosted 러너 등록** — 배포 서버에서 실행한다. 토큰은 Settings → Actions → Runners → New self-hosted runner에서 발급한다.

```bash
mkdir ~/actions-runner && cd ~/actions-runner
curl -o actions-runner.tar.gz -L https://github.com/actions/runner/releases/download/v2.328.0/actions-runner-linux-x64-2.328.0.tar.gz
tar xzf actions-runner.tar.gz
./config.sh --url https://github.com/givemeticket/givemeticket-be --token <TOKEN> --labels prod
sudo ./svc.sh install && sudo ./svc.sh start
```

라벨 `prod`가 있어야 CD 잡이 이 러너를 잡는다. 러너 계정이 `docker` 그룹에 속해야 한다.

```bash
sudo usermod -aG docker $USER
```

## 프로덕션 노출

| 경로 | 대상 |
| --- | --- |
| `https://api.givemeticket.site/api/v1/...` | backend |
| `https://api.givemeticket.site/grafana/` | Grafana |
| `/actuator/*` | 외부 차단 (nginx `deny all`) |

nginx만 80·443을 열고 나머지는 컨테이너 네트워크 내부에 둔다. backend는 헬스체크용으로 `127.0.0.1:8080`에만 바인딩한다.

GCP 방화벽은 80과 443만 열면 된다. 80은 HTTPS 리다이렉트와 Let's Encrypt ACME 챌린지에 쓴다.

## DNS

| 레코드 | 값 | 용도 |
| --- | --- | --- |
| `api.givemeticket.site` A | 35.216.61.208 | 이 서버 |
| `givemeticket.site` | 프론트 호스팅 | 별도 |

apex는 프론트가 가져가므로 API는 서브도메인으로 분리한다.

## HTTPS 최초 발급

`api.givemeticket.site` A 레코드가 서버 IP를 가리키고 80 포트가 열린 뒤 배포 서버에서 한 번만 실행한다. `dig api.givemeticket.site +short`로 먼저 확인한다.

```bash
cd ~/givemeticket-prod
DOMAIN=api.givemeticket.site CERTBOT_EMAIL=<이메일> ./nginx/init-letsencrypt.sh
```

임시 자체 서명 인증서로 nginx를 띄우고 → ACME 챌린지를 받고 → 실제 인증서로 교체한 뒤 리로드한다. 인증서 없이 nginx를 먼저 띄울 수 없어 필요한 절차다.

발급 한도(도메인당 주 5회)를 아끼려면 `STAGING=1`로 먼저 시험한다. 성공하면 `certbot/conf`를 지우고 다시 실행한다.

갱신은 certbot 컨테이너가 12시간마다 `certbot renew`를 돌리고, nginx가 6시간마다 리로드해 새 인증서를 집는다.

## 이미지

Docker Hub private 리포 하나에 태그로 두 서비스를 담는다.

| 태그 | 서비스 |
| --- | --- |
| `givemeticket:backend` | 최신 backend |
| `givemeticket:payment-mock` | 최신 payment-mock |
| `givemeticket:backend-<sha>` | 커밋별 고정 태그 (롤백용) |
| `givemeticket:payment-mock-<sha>` | 〃 |

네임스페이스는 CD가 배포 디렉터리에 `.env`로 써서 compose에 넘긴다. 조직 계정을 쓰면 Variable `DOCKER_NAMESPACE`를 등록한다(로그인 계정과 네임스페이스가 다른 경우). 없으면 `DOCKER_USERNAME`을 쓴다.

롤백은 서버에서 태그를 바꿔 띄운다.

```bash
cd ~/givemeticket-prod
docker compose up -d --no-deps backend   # 이미지 태그를 sha 태그로 바꾼 뒤 실행
```

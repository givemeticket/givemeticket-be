# givemeticket-be

선착순 티켓 신청 API.

## 실행

```bash
cp .env.example .env
docker compose up -d
```

관측 스택은 opt-in이다.

```bash
docker compose --profile obs up -d
```

포트와 결제 장애 주입 값은 `.env`에서 바꾼다.

배포는 [docs/deploy.md](docs/deploy.md) 참고.

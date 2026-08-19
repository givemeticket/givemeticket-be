package kr.givemeticket.api.system.ui.apiSpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.givemeticket.api.system.ui.dto.response.GetServerTimeResponse;
import org.springframework.http.ResponseEntity;

@Tag(name = "시스템 API", description = "서버 상태 관련 API 명세입니다.")
public interface ServerTimeApiSpec {

    @Operation(summary = "서버 시각 조회",
            description = """
                    현재 서버 시각을 UTC 로 내려줍니다. 인증은 필요 없습니다.

                    오픈 시각까지의 카운트다운을 사용자 PC 시계로 그리면, 시계가 몇 초만 어긋나도
                    열리지 않은 행사에 신청 버튼이 눌립니다. 이 API 로 오차를 한 번 재두고
                    로컬 시각에 더해서 쓰면 됩니다.

                    ```
                    const t0 = Date.now();
                    const { epochMilli } = await fetch('/api/v1/time').then(r => r.json());
                    const t1 = Date.now();
                    // 왕복 시간의 절반을 응답 시각으로 보정한다
                    const offset = epochMilli + (t1 - t0) / 2 - t1;
                    const serverNow = () => Date.now() + offset;
                    ```
                    """)
    ResponseEntity<GetServerTimeResponse> readServerTime();
}

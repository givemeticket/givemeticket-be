package kr.givemeticket.api.system.ui;

import java.time.Instant;
import kr.givemeticket.api.system.ui.apiSpec.ServerTimeApiSpec;
import kr.givemeticket.api.system.ui.dto.response.GetServerTimeResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServerTimeController implements ServerTimeApiSpec {

    /**
     * 캐시되면 시각 보정이 통째로 어긋나므로 명시적으로 막는다.
     */
    @Override
    @GetMapping("time")
    public ResponseEntity<GetServerTimeResponse> readServerTime() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(GetServerTimeResponse.of(Instant.now()));
    }
}

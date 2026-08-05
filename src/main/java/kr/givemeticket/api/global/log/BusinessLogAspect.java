package kr.givemeticket.api.global.log;

import kr.givemeticket.api.global.log.dto.BusinessLog;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.marker.Markers;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Aspect
@Slf4j
@Component
public class BusinessLogAspect {

    private static final String USER_ID = "user_id";

    @Around("@annotation(businessLogging)")
    public Object logBusinessAction(ProceedingJoinPoint joinPoint, BusinessLogging businessLogging)
            throws Throwable {

        String action = businessLogging.value();
        Long userId = currentUserId();

        try {
            Object result = joinPoint.proceed();
            write(BusinessLog.success(userId, action));
            return result;
        } catch (Throwable e) {
            // 실패도 비즈니스 이벤트다. 예외 자체는 GlobalExceptionHandler 가 ErrorLog 로 따로 남긴다.
            write(BusinessLog.failure(userId, action));
            throw e;
        }
    }

    /**
     * LogFilter 가 X-User-Id 헤더를 MDC 에 넣어 둔다. 인자 순서에 의존하지 않기 위해 MDC 에서 읽는다.
     */
    private Long currentUserId() {
        String userId = MDC.get(USER_ID);
        if (userId == null) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void write(BusinessLog businessLog) {
        log.info(Markers.appendEntries(businessLog.fields()), businessLog.summary());
    }
}

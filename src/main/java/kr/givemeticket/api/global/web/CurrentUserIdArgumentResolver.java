package kr.givemeticket.api.global.web;

import jakarta.servlet.http.HttpServletRequest;
import kr.givemeticket.api.global.exception.BusinessException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && parameter.getParameterType().equals(Long.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        String header = request.getHeader(USER_ID_HEADER);
        if (header == null || header.isBlank()) {
            CurrentUserId annotation = parameter.getParameterAnnotation(CurrentUserId.class);
            if (annotation != null && !annotation.required()) {
                return null;
            }
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "MISSING_USER_ID",
                    "X-User-Id 헤더가 필요합니다.");
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_USER_ID",
                    "X-User-Id 헤더는 숫자여야 합니다.");
        }
    }
}

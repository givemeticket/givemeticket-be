package kr.givemeticket.api.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import kr.givemeticket.api.global.auth.annotation.LoginUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class LoginUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    private final AccessTokenProvider accessTokenProvider;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUserId.class)
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
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || header.isBlank()) {
            LoginUserId annotation = parameter.getParameterAnnotation(LoginUserId.class);
            if (annotation != null && !annotation.required()) {
                return null;
            }
            throw AuthException.missingToken();
        }

        return accessTokenProvider.extractUserId(BearerToken.resolve(header));
    }
}

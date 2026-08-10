package kr.givemeticket.api.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import kr.givemeticket.api.global.auth.annotation.Provider;
import kr.givemeticket.api.login.domain.ProviderPrincipal;
import kr.givemeticket.api.login.domain.ProviderTokenProvider;
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
public class ProviderArgumentResolver implements HandlerMethodArgumentResolver {

    private final ProviderTokenProvider providerTokenProvider;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(Provider.class)
                && parameter.getParameterType().equals(ProviderPrincipal.class);
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

        return providerTokenProvider.extractPrincipal(BearerToken.resolve(header));
    }
}

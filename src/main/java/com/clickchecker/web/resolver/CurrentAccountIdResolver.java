package com.clickchecker.web.resolver;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.clickchecker.web.error.ApiErrorMessages;
import com.clickchecker.web.filter.JwtAuthFilter;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentAccountIdResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        boolean hasAnnotation = parameter.hasParameterAnnotation(CurrentAccountId.class);
        Class<?> parameterType = parameter.getParameterType();
        boolean supportedType = Long.class.equals(parameterType) || long.class.equals(parameterType);
        return hasAnnotation && supportedType;
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        Object value = webRequest.getAttribute(JwtAuthFilter.AUTH_ACCOUNT_ID, NativeWebRequest.SCOPE_REQUEST);
        if (value instanceof Long accountId) {
            return accountId;
        }
        throw new ResponseStatusException(UNAUTHORIZED, ApiErrorMessages.UNAUTHORIZED);
    }
}

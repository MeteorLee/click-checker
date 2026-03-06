package com.clickchecker.web.resolver;

import com.clickchecker.web.filter.ApiKeyAuthFilter;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Component
public class CurrentOrganizationIdResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        boolean hasAnnotation = parameter.hasParameterAnnotation(CurrentOrganizationId.class);
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
        Object value = webRequest.getAttribute(ApiKeyAuthFilter.AUTH_ORG_ID, NativeWebRequest.SCOPE_REQUEST);
        if (value instanceof Long orgId) {
            return orgId;
        }
        throw new ResponseStatusException(UNAUTHORIZED, "Unauthorized.");
    }
}

package com.clickchecker.web.sentry;

import io.sentry.Sentry;
import jakarta.servlet.http.HttpServletRequest;

public final class SentryRequestContextSupport {

    private static final String REQUEST_ID_TAG = "requestId";
    private static final String AUTH_TYPE_TAG = "authType";
    private static final String ACCOUNT_ID_TAG = "accountId";
    private static final String ORGANIZATION_ID_TAG = "organizationId";
    private static final String API_KEY_KID_TAG = "apiKeyKid";
    private static final String HTTP_METHOD_EXTRA = "httpMethod";
    private static final String REQUEST_PATH_EXTRA = "requestPath";
    private static final String QUERY_STRING_EXTRA = "queryString";

    private SentryRequestContextSupport() {
    }

    public static void bindRequestContext(String requestId, HttpServletRequest request) {
        Sentry.configureScope(scope -> {
            scope.setTag(REQUEST_ID_TAG, requestId);
            scope.setTag(AUTH_TYPE_TAG, "anonymous");
            scope.setExtra(HTTP_METHOD_EXTRA, request.getMethod());
            scope.setExtra(REQUEST_PATH_EXTRA, request.getRequestURI());

            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isBlank()) {
                scope.setExtra(QUERY_STRING_EXTRA, queryString);
            } else {
                scope.removeExtra(QUERY_STRING_EXTRA);
            }
        });
    }

    public static void clearRequestContext() {
        Sentry.configureScope(scope -> {
            scope.removeTag(REQUEST_ID_TAG);
            scope.removeTag(AUTH_TYPE_TAG);
            scope.removeTag(ACCOUNT_ID_TAG);
            scope.removeTag(ORGANIZATION_ID_TAG);
            scope.removeTag(API_KEY_KID_TAG);
            scope.removeExtra(HTTP_METHOD_EXTRA);
            scope.removeExtra(REQUEST_PATH_EXTRA);
            scope.removeExtra(QUERY_STRING_EXTRA);
        });
    }

    public static void bindJwtAuthContext(Long accountId) {
        Sentry.configureScope(scope -> {
            scope.setTag(AUTH_TYPE_TAG, "jwt");
            scope.setTag(ACCOUNT_ID_TAG, String.valueOf(accountId));
            scope.removeTag(ORGANIZATION_ID_TAG);
            scope.removeTag(API_KEY_KID_TAG);
        });
    }

    public static void bindApiKeyAuthContext(Long organizationId, String apiKeyKid) {
        Sentry.configureScope(scope -> {
            scope.setTag(AUTH_TYPE_TAG, "api-key");
            scope.setTag(ORGANIZATION_ID_TAG, String.valueOf(organizationId));
            scope.setTag(API_KEY_KID_TAG, apiKeyKid);
            scope.removeTag(ACCOUNT_ID_TAG);
        });
    }
}

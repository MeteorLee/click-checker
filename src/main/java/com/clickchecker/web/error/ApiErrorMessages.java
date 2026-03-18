package com.clickchecker.web.error;

public final class ApiErrorMessages {

    public static final String UNAUTHORIZED = "Unauthorized.";
    public static final String FORBIDDEN = "Forbidden.";
    public static final String RESOURCE_NOT_FOUND = "Resource not found.";
    public static final String ACCOUNT_NOT_FOUND = "Account not found.";
    public static final String ORGANIZATION_NOT_FOUND = "Organization not found.";
    public static final String ORGANIZATION_MEMBER_NOT_FOUND = "Organization member not found.";
    public static final String ORGANIZATION_MEMBER_ALREADY_EXISTS = "Organization member already exists.";
    public static final String LAST_OWNER_CANNOT_BE_DEMOTED = "Last owner cannot be demoted.";
    public static final String LAST_OWNER_CANNOT_BE_REMOVED = "Last owner cannot be removed.";
    public static final String ROUTE_TEMPLATE_NOT_FOUND = "Route template not found.";
    public static final String EVENT_TYPE_MAPPING_NOT_FOUND = "Event type mapping not found.";

    private ApiErrorMessages() {
    }
}

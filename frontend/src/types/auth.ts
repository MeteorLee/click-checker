export type AdminLoginRequest = {
  loginId: string;
  password: string;
};

export type AdminSignupRequest = {
  loginId: string;
  password: string;
};

export type AdminLoginResponse = {
  accountId: number;
  accessToken: string;
  accessTokenExpiresIn: number;
  refreshToken: string;
  refreshTokenExpiresIn: number;
};

export type AdminSignupResponse = {
  accountId: number;
  accessToken: string;
  accessTokenExpiresIn: number;
  refreshToken: string;
  refreshTokenExpiresIn: number;
};

export type AdminMeMembership = {
  membershipId: number;
  organizationId: number;
  organizationName: string;
  role: string;
};

export type AdminMeResponse = {
  accountId: number;
  loginId: string;
  status: string;
  memberships: AdminMeMembership[];
};

export type AdminOrganizationCreateRequest = {
  name: string;
};

export type AdminOrganizationCreateResponse = {
  organizationId: number;
  name: string;
  ownerMembershipId: number;
  apiKey: string;
  apiKeyPrefix: string;
};

export type AdminOrganizationApiKeyMetadataResponse = {
  kid: string;
  apiKeyPrefix: string;
  status: string;
  createdAt: string | null;
  rotatedAt: string | null;
  lastUsedAt: string | null;
};

export type AdminOrganizationApiKeyRotateResponse = {
  apiKey: string;
  apiKeyPrefix: string;
  rotatedAt: string | null;
};

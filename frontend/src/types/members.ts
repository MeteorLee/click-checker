export type AdminOrganizationMemberResponse = {
  memberId: number;
  accountId: number;
  loginId: string;
  accountStatus: string;
  role: string;
};

export type AdminOrganizationMemberListResponse = {
  members: AdminOrganizationMemberResponse[];
};

export type AdminOrganizationMemberInviteRequest = {
  loginId: string;
  role: "OWNER" | "ADMIN" | "VIEWER";
};

export type AdminOrganizationMemberRoleUpdateRequest = {
  role: "OWNER" | "ADMIN" | "VIEWER";
};

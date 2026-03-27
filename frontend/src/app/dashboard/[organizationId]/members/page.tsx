"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { ConsoleHeader } from "@/components/common/console-header";
import { fetchMe } from "@/lib/api/auth";
import {
  fetchOrganizationMembers,
  inviteOrganizationMemberByLoginId,
  removeOrganizationMember,
  updateOrganizationMemberRole,
} from "@/lib/api/members";
import { getAccessToken } from "@/lib/session/token-store";
import type { AdminOrganizationMemberResponse } from "@/types/members";
import {
  Alert,
  Badge,
  Button,
  Container,
  Group,
  Loader,
  Paper,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  ThemeIcon,
} from "@mantine/core";
import {
  IconAlertCircle,
  IconArrowRight,
  IconShield,
  IconTrash,
  IconUsers,
} from "@tabler/icons-react";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

type PageState = {
  organizationName: string;
  currentRole: string | null;
  currentAccountId: number;
  members: AdminOrganizationMemberResponse[];
};

const roleOptions = [
  { value: "OWNER", label: "OWNER" },
  { value: "ADMIN", label: "ADMIN" },
  { value: "VIEWER", label: "VIEWER" },
] as const;

const inviteExamples = ["alice", "product_admin", "viewer_demo"] as const;

export default function MembersPage() {
  const router = useRouter();
  const params = useParams<{ organizationId: string }>();
  const [data, setData] = useState<PageState | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [inviteLoginId, setInviteLoginId] = useState("");
  const [inviteRole, setInviteRole] = useState<string>("VIEWER");
  const [inviteErrorMessage, setInviteErrorMessage] = useState<string | null>(null);
  const [isInviting, setIsInviting] = useState(false);
  const [memberActionError, setMemberActionError] = useState<string | null>(null);
  const [roleDrafts, setRoleDrafts] = useState<Record<number, string>>({});
  const [updatingMemberId, setUpdatingMemberId] = useState<number | null>(null);
  const [removingMemberId, setRemovingMemberId] = useState<number | null>(null);

  useEffect(() => {
    async function load() {
      const accessToken = getAccessToken();
      if (!accessToken) {
        router.replace("/login");
        return;
      }

      setIsLoading(true);
      setErrorMessage(null);

      try {
        const [me, membersResponse] = await Promise.all([
          fetchMe(accessToken),
          fetchOrganizationMembers(accessToken, params.organizationId),
        ]);

        const currentMembership = me.memberships.find(
          (membership) => String(membership.organizationId) === params.organizationId,
        );

        const members = membersResponse.members;
        setRoleDrafts(
          Object.fromEntries(members.map((member) => [member.memberId, member.role])),
        );
        setData({
          organizationName:
            currentMembership?.organizationName ?? `Organization ${params.organizationId}`,
          currentRole: currentMembership?.role ?? null,
          currentAccountId: me.accountId,
          members,
        });
      } catch (error) {
        const status =
          "status" in (error as object) ? (error as { status?: number }).status : undefined;

        if (status === 401) {
          router.replace("/login");
          return;
        }

        const message =
          error instanceof Error
            ? error.message
            : "멤버 목록을 불러오지 못했습니다.";
        setErrorMessage(message);
      } finally {
        setIsLoading(false);
      }
    }

    void load();
  }, [params.organizationId, router]);

  async function handleInvite() {
    if (!data) {
      return;
    }

    const accessToken = getAccessToken();
    if (!accessToken) {
      router.replace("/login");
      return;
    }

    setInviteErrorMessage(null);
    setMemberActionError(null);
    setIsInviting(true);

    try {
      const created = await inviteOrganizationMemberByLoginId(
        accessToken,
        params.organizationId,
        {
          loginId: inviteLoginId,
          role: inviteRole as "OWNER" | "ADMIN" | "VIEWER",
        },
      );
      setInviteLoginId("");
      setInviteRole("VIEWER");
      setData({
        ...data,
        members: [...data.members, created].sort((left, right) =>
          left.loginId.localeCompare(right.loginId),
        ),
      });
      setRoleDrafts((previous) => ({
        ...previous,
        [created.memberId]: created.role,
      }));
    } catch (error) {
      setInviteErrorMessage(
        error instanceof Error ? error.message : "멤버 추가 중 오류가 발생했습니다.",
      );
    } finally {
      setIsInviting(false);
    }
  }

  async function handleUpdateRole(member: AdminOrganizationMemberResponse) {
    const accessToken = getAccessToken();
    if (!accessToken) {
      router.replace("/login");
      return;
    }

    const nextRole = roleDrafts[member.memberId];
    if (!nextRole || nextRole === member.role) {
      return;
    }

    setMemberActionError(null);
    setUpdatingMemberId(member.memberId);

    try {
      const updated = await updateOrganizationMemberRole(
        accessToken,
        params.organizationId,
        member.memberId,
        { role: nextRole as "OWNER" | "ADMIN" | "VIEWER" },
      );
      setData((previous) =>
        previous
          ? {
              ...previous,
              members: previous.members.map((current) =>
                current.memberId === member.memberId ? updated : current,
              ),
            }
          : previous,
      );
    } catch (error) {
      setMemberActionError(
        error instanceof Error ? error.message : "역할 변경 중 오류가 발생했습니다.",
      );
      setRoleDrafts((previous) => ({
        ...previous,
        [member.memberId]: member.role,
      }));
    } finally {
      setUpdatingMemberId(null);
    }
  }

  async function handleRemove(member: AdminOrganizationMemberResponse) {
    const accessToken = getAccessToken();
    if (!accessToken) {
      router.replace("/login");
      return;
    }

    if (!window.confirm(`${member.loginId} 멤버를 제거하시겠습니까?`)) {
      return;
    }

    setMemberActionError(null);
    setRemovingMemberId(member.memberId);

    try {
      await removeOrganizationMember(accessToken, params.organizationId, member.memberId);
      setData((previous) =>
        previous
          ? {
              ...previous,
              members: previous.members.filter(
                (current) => current.memberId !== member.memberId,
              ),
            }
          : previous,
      );
    } catch (error) {
      setMemberActionError(
        error instanceof Error ? error.message : "멤버 제거 중 오류가 발생했습니다.",
      );
    } finally {
      setRemovingMemberId(null);
    }
  }

  if (isLoading) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="멤버 관리"
          subtitle="organization 멤버 목록을 불러오는 중입니다."
          backHref={`/dashboard/${params.organizationId}`}
          badge="Settings"
        />
        <Container size="lg" py={96}>
          <Stack align="center" gap="md">
            <Loader color="blue" />
            <Text c="dimmed">멤버 정보를 불러오는 중입니다.</Text>
          </Stack>
        </Container>
      </ConsoleFrame>
    );
  }

  if (errorMessage || !data) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="멤버 관리"
          subtitle="organization 멤버와 역할을 확인합니다."
          backHref={`/dashboard/${params.organizationId}`}
          badge="Settings"
        />
        <Container size="md" py={96}>
          <Alert color="red" icon={<IconAlertCircle size={18} />} radius="lg" variant="light">
            {errorMessage ?? "멤버 화면을 불러오지 못했습니다."}
          </Alert>
        </Container>
      </ConsoleFrame>
    );
  }

  const isOwner = data.currentRole === "OWNER";

  return (
    <ConsoleFrame>
      <ConsoleHeader
        title="멤버 관리"
        subtitle={`${data.organizationName}의 멤버를 추가하고 역할을 관리합니다.`}
        backHref={`/dashboard/${params.organizationId}`}
        badge="Settings"
      />

      <Container size="lg" pb={72}>
        <Stack gap="xl">
          <Paper radius="28px" p="xl" shadow="sm" withBorder className="console-panel">
            <Stack gap="lg">
              <Group justify="space-between" align="flex-start">
                <Group align="flex-start" gap="md" wrap="nowrap">
                  <ThemeIcon color="blue" radius="xl" size={48} variant="light">
                    <IconUsers size={22} />
                  </ThemeIcon>
                  <div>
                    <Text fw={700} size="lg">
                      멤버 초대
                    </Text>
                    <Text c="dimmed" size="sm">
                      기존 계정의 loginId를 입력하면 별도 수락 없이 바로 membership이 생성됩니다.
                    </Text>
                  </div>
                </Group>
                <Badge color={isOwner ? "blue" : "gray"} variant="light">
                  현재 역할: {data.currentRole ?? "UNKNOWN"}
                </Badge>
              </Group>

              <Paper bg="gray.0" p="md" radius="20px" withBorder>
                <Stack gap="xs">
                  <Text fw={700} size="sm">
                    임시 초대 방식
                  </Text>
                  <Text c="dimmed" size="sm">
                    지금은 별도 초대 링크 없이, 이미 가입된 계정의 loginId를 직접 입력해 멤버를
                    추가합니다.
                  </Text>
                  <Group gap="xs">
                    <Text c="dimmed" size="sm">
                      예시:
                    </Text>
                    {inviteExamples.map((example) => (
                      <Badge key={example} color="gray" radius="xl" variant="light">
                        {example}
                      </Badge>
                    ))}
                  </Group>
                </Stack>
              </Paper>

              {!isOwner ? (
                <Alert color="gray" radius="lg" variant="light">
                  OWNER만 멤버 추가, 역할 변경, 제거를 할 수 있습니다.
                </Alert>
              ) : null}

              {inviteErrorMessage ? (
                <Alert color="red" icon={<IconAlertCircle size={18} />} radius="lg" variant="light">
                  {inviteErrorMessage}
                </Alert>
              ) : null}

              <Group align="flex-end">
                <TextInput
                  description="임시 방식입니다. 이미 가입된 계정의 loginId를 직접 입력합니다."
                  disabled={!isOwner}
                  label="Login ID"
                  placeholder="alice"
                  radius="xl"
                  style={{ flex: 1 }}
                  value={inviteLoginId}
                  onChange={(event) => setInviteLoginId(event.currentTarget.value)}
                />
                <Select
                  data={roleOptions as unknown as { value: string; label: string }[]}
                  disabled={!isOwner}
                  label="Role"
                  radius="xl"
                  value={inviteRole}
                  onChange={(value) => setInviteRole(value ?? "VIEWER")}
                />
                <Button
                  disabled={!isOwner || inviteLoginId.trim().length === 0}
                  loading={isInviting}
                  radius="xl"
                  rightSection={<IconArrowRight size={16} />}
                  onClick={() => void handleInvite()}
                >
                  멤버 추가
                </Button>
              </Group>
            </Stack>
          </Paper>

          {memberActionError ? (
            <Alert color="red" icon={<IconAlertCircle size={18} />} radius="lg" variant="light">
              {memberActionError}
            </Alert>
          ) : null}

          <Paper radius="28px" p="xl" shadow="sm" withBorder className="console-panel">
            <Stack gap="lg">
              <Group justify="space-between" align="center">
                <Text fw={700} size="lg">
                  현재 멤버
                </Text>
                <Badge color="dark" radius="xl" variant="light">
                  {data.members.length} members
                </Badge>
              </Group>

              <Table highlightOnHover horizontalSpacing="md" verticalSpacing="md">
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>Login ID</Table.Th>
                    <Table.Th>상태</Table.Th>
                    <Table.Th>역할</Table.Th>
                    <Table.Th>동작</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {data.members.map((member) => {
                    const isSelf = member.accountId === data.currentAccountId;

                    return (
                      <Table.Tr key={member.memberId}>
                        <Table.Td>
                          <Group gap="xs">
                            <Text fw={600}>{member.loginId}</Text>
                            {isSelf ? (
                              <Badge color="gray" size="sm" variant="light">
                                현재 계정
                              </Badge>
                            ) : null}
                          </Group>
                        </Table.Td>
                        <Table.Td>{member.accountStatus}</Table.Td>
                        <Table.Td>
                          {isOwner && !isSelf ? (
                            <Group gap="xs" wrap="nowrap">
                              <Select
                                data={roleOptions as unknown as { value: string; label: string }[]}
                                radius="xl"
                                size="sm"
                                value={roleDrafts[member.memberId] ?? member.role}
                                w={120}
                                onChange={(value) =>
                                  setRoleDrafts((previous) => ({
                                    ...previous,
                                    [member.memberId]: value ?? member.role,
                                  }))
                                }
                              />
                              <Button
                                loading={updatingMemberId === member.memberId}
                                radius="xl"
                                size="xs"
                                variant="light"
                                onClick={() => void handleUpdateRole(member)}
                              >
                                저장
                              </Button>
                            </Group>
                          ) : (
                            <Badge color="blue" variant="light">
                              {member.role}
                            </Badge>
                          )}
                        </Table.Td>
                        <Table.Td>
                          {isOwner && !isSelf ? (
                            <Button
                              color="red"
                              leftSection={<IconTrash size={14} />}
                              loading={removingMemberId === member.memberId}
                              radius="xl"
                              size="xs"
                              variant="subtle"
                              onClick={() => void handleRemove(member)}
                            >
                              제거
                            </Button>
                          ) : (
                            <Text c="dimmed" size="sm">
                              {isSelf ? "현재 계정" : "-"}
                            </Text>
                          )}
                        </Table.Td>
                      </Table.Tr>
                    );
                  })}
                </Table.Tbody>
              </Table>
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </ConsoleFrame>
  );
}

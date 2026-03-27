"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { ConsoleHeader } from "@/components/common/console-header";
import { createOrganization, fetchMe, leaveOrganization } from "@/lib/api/auth";
import { clearAccessToken, getAccessToken } from "@/lib/session/token-store";
import type {
  AdminMeMembership,
  AdminOrganizationCreateResponse,
} from "@/types/auth";
import {
  Alert,
  Badge,
  Button,
  Card,
  Code,
  Container,
  CopyButton,
  Divider,
  Group,
  Loader,
  Modal,
  Paper,
  rem,
  SimpleGrid,
  Stack,
  Text,
  TextInput,
  Title,
  ThemeIcon,
} from "@mantine/core";
import {
  IconAlertCircle,
  IconArrowRight,
  IconBuildingPlus,
  IconBuildingSkyscraper,
  IconCheck,
  IconCopy,
  IconTrash,
} from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";

const SOLE_OWNER_CONFIRMATION =
  "혼자 남은 OWNER라 삭제 후 복구할 수 없음을 이해했습니다";

type OrganizationsState = {
  loginId: string;
  memberships: AdminMeMembership[];
};

type CreatedOrganizationState = {
  organizationId: number;
  organizationName: string;
  apiKey: string;
  apiKeyPrefix: string;
};

export default function OrganizationsPage() {
  const router = useRouter();
  const [data, setData] = useState<OrganizationsState | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [newOrganizationName, setNewOrganizationName] = useState("");
  const [createErrorMessage, setCreateErrorMessage] = useState<string | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [isCreateModalOpened, setIsCreateModalOpened] = useState(false);
  const [createdOrganization, setCreatedOrganization] =
    useState<CreatedOrganizationState | null>(null);
  const [pendingDeleteMembership, setPendingDeleteMembership] =
    useState<AdminMeMembership | null>(null);
  const [deleteErrorMessage, setDeleteErrorMessage] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteConfirmationText, setDeleteConfirmationText] = useState("");
  const [requiresDeleteConfirmation, setRequiresDeleteConfirmation] = useState(false);

  async function loadOrganizations() {
    const accessToken = getAccessToken();

    if (!accessToken) {
      router.replace("/login");
      return;
    }

    try {
      const me = await fetchMe(accessToken);
      setData({
        loginId: me.loginId,
        memberships: me.memberships,
      });
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : "organization 목록을 불러오지 못했습니다.";

      if ("status" in (error as object) && (error as { status?: number }).status === 401) {
        clearAccessToken();
        router.replace("/login");
        return;
      }

      setErrorMessage(message);
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void loadOrganizations();
  }, [router]);

  async function handleCreateOrganization(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const accessToken = getAccessToken();
    if (!accessToken) {
      router.replace("/login");
      return;
    }

    setCreateErrorMessage(null);
    setIsCreating(true);

    try {
      const result = await createOrganization(accessToken, {
        name: newOrganizationName,
      });
      setNewOrganizationName("");
      setIsCreateModalOpened(false);
      setCreatedOrganization({
        organizationId: result.organizationId,
        organizationName: result.name,
        apiKey: result.apiKey,
        apiKeyPrefix: result.apiKeyPrefix,
      });

      setData((previous) =>
        previous
          ? {
              ...previous,
              memberships: [
                ...previous.memberships,
                {
                  membershipId: result.ownerMembershipId,
                  organizationId: result.organizationId,
                  organizationName: result.name,
                  role: "OWNER",
                },
              ].sort((left, right) =>
                left.organizationName.localeCompare(right.organizationName),
              ),
            }
          : previous,
      );
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : "organization 생성 중 오류가 발생했습니다.";
      setCreateErrorMessage(message);
    } finally {
      setIsCreating(false);
    }
  }

  async function handleLeaveOrganization() {
    if (!pendingDeleteMembership) {
      return;
    }

    const accessToken = getAccessToken();
    if (!accessToken) {
      router.replace("/login");
      return;
    }

    setDeleteErrorMessage(null);
    setIsDeleting(true);

    try {
      await leaveOrganization(
        accessToken,
        pendingDeleteMembership.organizationId,
        requiresDeleteConfirmation ? deleteConfirmationText : undefined,
      );
      setData((previous) =>
        previous
          ? {
              ...previous,
              memberships: previous.memberships.filter(
                (membership) =>
                  membership.membershipId !== pendingDeleteMembership.membershipId,
              ),
            }
          : previous,
      );
      setPendingDeleteMembership(null);
      setDeleteConfirmationText("");
      setRequiresDeleteConfirmation(false);
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : "organization 삭제 중 오류가 발생했습니다.";

      if (message === "Sole owner confirmation is required.") {
        setRequiresDeleteConfirmation(true);
        setDeleteErrorMessage(
          "혼자 남은 OWNER인 경우에만 삭제할 수 있습니다. 아래 문구를 정확히 입력하면 진행됩니다.",
        );
      } else {
        setDeleteErrorMessage(message);
      }
    } finally {
      setIsDeleting(false);
    }
  }

  if (isLoading) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="Organization 선택"
          subtitle="로그인한 계정이 접근 가능한 organization 목록을 불러오는 중입니다."
        />
        <Container size="md" py={96}>
          <Stack align="center" gap="md">
            <Loader color="blue" />
            <Text c="dimmed">organization 목록을 불러오는 중입니다.</Text>
          </Stack>
        </Container>
      </ConsoleFrame>
    );
  }

  if (errorMessage) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="Organization 선택"
          subtitle="로그인한 계정이 접근 가능한 organization 목록을 확인합니다."
        />
        <Container size="sm" py={96}>
          <Alert
            color="red"
            icon={<IconAlertCircle size={18} />}
            radius="lg"
            variant="light"
          >
            {errorMessage}
          </Alert>
        </Container>
      </ConsoleFrame>
    );
  }

  if (!data) {
    return null;
  }

  return (
    <ConsoleFrame>
      <Modal
        centered
        opened={pendingDeleteMembership !== null}
        radius="28px"
        title="Organization 삭제"
        onClose={() => {
          if (isDeleting) {
            return;
          }
          setPendingDeleteMembership(null);
          setDeleteErrorMessage(null);
          setDeleteConfirmationText("");
          setRequiresDeleteConfirmation(false);
        }}
      >
        {pendingDeleteMembership ? (
          <Stack gap="lg">
            <Text c="dimmed" size="sm">
              <Text component="span" fw={700} inherit>
                {pendingDeleteMembership.organizationName}
              </Text>
              에서 현재 계정의 membership 연결을 끊습니다. organization 데이터는 남고,
              내 목록에서만 사라집니다.
            </Text>

            {deleteErrorMessage ? (
              <Alert
                color="red"
                icon={<IconAlertCircle size={18} />}
                radius="lg"
                variant="light"
              >
                {deleteErrorMessage}
              </Alert>
            ) : null}

            <Paper bg="gray.0" p="md" radius="20px" withBorder>
              <Stack gap={4}>
                <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                  현재 역할
                </Text>
                <Text fw={700}>{pendingDeleteMembership.role}</Text>
              </Stack>
            </Paper>

            <Text c="dimmed" size="sm">
              마지막 OWNER는 바로 삭제할 수 없습니다. 필요하면 다른 멤버에게 OWNER를 먼저
              넘겨야 합니다.
            </Text>

            {requiresDeleteConfirmation ? (
              <Stack gap="xs">
                <Stack gap={2}>
                  <Text fw={700} size="sm">
                    확인 문구 입력
                  </Text>
                  <Text c="dimmed" size="sm">
                    위 확인 문구를 그대로 입력해야 삭제할 수 있습니다.
                  </Text>
                </Stack>
                <Paper bg="gray.0" p="md" radius="20px" withBorder>
                  <Stack gap={8}>
                    <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                      확인 문구
                    </Text>
                    <Text ff="monospace" fw={700} size="sm">
                      {SOLE_OWNER_CONFIRMATION}
                    </Text>
                  </Stack>
                </Paper>
                <TextInput
                  radius="xl"
                  value={deleteConfirmationText}
                  onChange={(event) => setDeleteConfirmationText(event.currentTarget.value)}
                />
              </Stack>
            ) : null}

            <Group justify="flex-end">
              <Button
                radius="xl"
                variant="subtle"
                onClick={() => {
                  setPendingDeleteMembership(null);
                  setDeleteErrorMessage(null);
                  setDeleteConfirmationText("");
                  setRequiresDeleteConfirmation(false);
                }}
              >
                취소
              </Button>
              <Button
                color="red"
                disabled={
                  requiresDeleteConfirmation &&
                  deleteConfirmationText !== SOLE_OWNER_CONFIRMATION
                }
                leftSection={<IconTrash size={16} />}
                loading={isDeleting}
                radius="xl"
                onClick={() => void handleLeaveOrganization()}
              >
                삭제
              </Button>
            </Group>
          </Stack>
        ) : null}
      </Modal>

      <Modal
        centered
        opened={isCreateModalOpened}
        radius="28px"
        title="새 organization 만들기"
        onClose={() => {
          if (isCreating) {
            return;
          }
          setIsCreateModalOpened(false);
          setCreateErrorMessage(null);
          setNewOrganizationName("");
        }}
      >
        <Stack gap="lg">
          <Text c="dimmed" size="sm">
            이름을 입력하면 OWNER 권한과 수집용 API key가 함께 생성됩니다.
          </Text>

          {createErrorMessage ? (
            <Alert color="red" icon={<IconAlertCircle size={18} />} radius="lg" variant="light">
              {createErrorMessage}
            </Alert>
          ) : null}

          <form onSubmit={handleCreateOrganization}>
            <Stack gap="md">
              <TextInput
                label="Organization Name"
                placeholder="My Product Team"
                required
                radius="xl"
                size="md"
                value={newOrganizationName}
                onChange={(event) => setNewOrganizationName(event.currentTarget.value)}
              />
              <Group justify="flex-end">
                <Button
                  radius="xl"
                  variant="subtle"
                  onClick={() => {
                    setIsCreateModalOpened(false);
                    setCreateErrorMessage(null);
                    setNewOrganizationName("");
                  }}
                >
                  취소
                </Button>
                <Button
                  type="submit"
                  radius="xl"
                  loading={isCreating}
                  rightSection={<IconArrowRight size={16} />}
                >
                  생성하고 바로 보기
                </Button>
              </Group>
            </Stack>
          </form>
        </Stack>
      </Modal>

      <Modal
        centered
        closeOnClickOutside={false}
        closeOnEscape={false}
        opened={createdOrganization !== null}
        radius="28px"
        size="lg"
        title="API Key가 생성되었습니다"
        onClose={() => {
          if (createdOrganization) {
            router.push(`/dashboard/${createdOrganization.organizationId}`);
          }
        }}
      >
        {createdOrganization ? (
          <Stack gap="lg">
            <Text c="dimmed" size="sm">
              <Text component="span" fw={700} inherit>
                {createdOrganization.organizationName}
              </Text>
              의 수집용 API key입니다. 전체 키는 지금 한 번만 확인할 수 있습니다.
            </Text>

            <Paper radius="24px" p="lg" withBorder bg="gray.0">
              <Stack gap="xs">
                <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                  API Key
                </Text>
                <Code block>{createdOrganization.apiKey}</Code>
              </Stack>
            </Paper>

            <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="md">
              <Paper radius="20px" p="md" withBorder>
                <Stack gap={4}>
                  <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                    Prefix
                  </Text>
                  <Text fw={700}>{createdOrganization.apiKeyPrefix}</Text>
                </Stack>
              </Paper>
              <Paper radius="20px" p="md" withBorder>
                <Stack gap={4}>
                  <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                    다음 단계
                  </Text>
                  <Text size="sm">
                    overview를 확인하거나, 이벤트를 보내는 클라이언트 설정에 이 키를 바로 사용할 수 있습니다.
                  </Text>
                </Stack>
              </Paper>
            </SimpleGrid>

            <Alert color="yellow" radius="lg" variant="light">
              이후에는 전체 API key를 다시 조회할 수 없고, prefix 확인과 rotate만 사용할 수 있습니다.
            </Alert>

            <Divider />

            <Group justify="space-between">
              <CopyButton value={createdOrganization.apiKey}>
                {({ copied, copy }) => (
                  <Button
                    color={copied ? "teal" : "dark"}
                    leftSection={copied ? <IconCheck size={16} /> : <IconCopy size={16} />}
                    radius="xl"
                    variant={copied ? "filled" : "light"}
                    onClick={copy}
                  >
                    {copied ? "복사됨" : "API Key 복사"}
                  </Button>
                )}
              </CopyButton>
              <Button
                radius="xl"
                rightSection={<IconArrowRight size={16} />}
                onClick={() => {
                  router.push(`/dashboard/${createdOrganization.organizationId}`);
                }}
              >
                대시보드로 이동
              </Button>
            </Group>
          </Stack>
        ) : null}
      </Modal>

      <ConsoleHeader
        title="Organization 선택"
        subtitle={`${data.loginId} 계정으로 접근 가능한 organization을 선택하거나 새 organization을 만들어 overview 대시보드로 이동합니다.`}
      />
      <Container size="lg" pb={72}>
        <Stack gap="xl">
          <Group justify="space-between" align="center">
            <Stack gap={4}>
              <Text fw={700} size="xl">
                작업할 organization 선택
              </Text>
              <Text c="dimmed" size="sm">
                현재 계정의 membership 기준으로 접근 가능한 organization만 표시합니다.
              </Text>
            </Stack>
            <Badge color="dark" radius="xl" size="lg" variant="light">
              {data.memberships.length} organizations
            </Badge>
          </Group>

          <Paper radius="28px" p="xl" shadow="sm" withBorder className="console-panel">
            <Stack gap="lg">
              <Group justify="space-between" align="flex-start">
                <Group align="flex-start" gap="md" wrap="nowrap">
                  <ThemeIcon color="teal" radius="xl" size={48} variant="light">
                    <IconBuildingPlus size={22} />
                  </ThemeIcon>
                  <div>
                    <Text fw={700} size="lg">
                      새 organization 만들기
                    </Text>
                    <Text c="dimmed" size="sm">
                      이름을 입력하면 OWNER 권한과 수집용 API key가 함께 생성됩니다.
                    </Text>
                  </div>
                </Group>
                <Button
                  radius="xl"
                  rightSection={<IconArrowRight size={16} />}
                  onClick={() => {
                    setCreateErrorMessage(null);
                    setIsCreateModalOpened(true);
                  }}
                >
                  새 organization 만들기
                </Button>
              </Group>
            </Stack>
          </Paper>

          {data.memberships.length === 0 ? (
            <Paper radius="28px" p="xl" withBorder className="console-panel">
              <Stack align="center" gap="md" py="lg">
                <ThemeIcon color="gray" radius="xl" size={54} variant="light">
                  <IconBuildingSkyscraper size={24} />
                </ThemeIcon>
                <Stack align="center" gap={4}>
                  <Text fw={700} size="lg">
                    아직 접근 가능한 organization이 없습니다
                  </Text>
                  <Text c="dimmed" size="sm" ta="center" maw={440}>
                    위에서 첫 organization을 만들면 OWNER 권한으로 overview 대시보드에 바로
                    들어갈 수 있습니다.
                  </Text>
                </Stack>
              </Stack>
            </Paper>
          ) : (
            <SimpleGrid cols={{ base: 1, md: 2 }} spacing="lg">
              {data.memberships.map((membership) => (
                <Card
                  key={membership.membershipId}
                  radius="28px"
                  p="xl"
                  shadow="sm"
                  withBorder
                  className="console-panel"
                >
                  <Stack gap="lg">
                    <Group justify="space-between" align="flex-start">
                      <Group align="flex-start" gap="md" wrap="nowrap">
                        <ThemeIcon color="blue" radius="xl" size={48} variant="light">
                          <IconBuildingSkyscraper size={22} />
                        </ThemeIcon>
                        <div>
                          <Text fw={700} size="lg">
                            {membership.organizationName}
                          </Text>
                          <Text c="dimmed" size="sm">
                            organizationId: {membership.organizationId}
                          </Text>
                        </div>
                      </Group>
                      <Badge variant="light" color="blue">
                        {membership.role}
                      </Badge>
                    </Group>

                    <Paper radius="lg" p="md" bg="gray.0">
                      <Text c="dimmed" size="sm">
                        이 organization의 overview, top routes, top event types를 조회할 수
                        있습니다.
                      </Text>
                    </Paper>

                    <Group justify="space-between" align="center">
                      <Button
                        color="red"
                        leftSection={<IconTrash size={16} />}
                        radius="xl"
                        variant="subtle"
                        onClick={() => {
                          setDeleteErrorMessage(null);
                          setPendingDeleteMembership(membership);
                        }}
                      >
                        삭제
                      </Button>
                      <Button
                        radius="xl"
                        rightSection={<IconArrowRight size={16} />}
                        onClick={() => router.push(`/dashboard/${membership.organizationId}`)}
                      >
                        대시보드 열기
                      </Button>
                    </Group>
                  </Stack>
                </Card>
              ))}
            </SimpleGrid>
          )}
        </Stack>
      </Container>
    </ConsoleFrame>
  );
}

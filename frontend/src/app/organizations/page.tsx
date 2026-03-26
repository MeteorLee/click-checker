"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { ConsoleHeader } from "@/components/common/console-header";
import { createOrganization, fetchMe } from "@/lib/api/auth";
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
} from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";

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
  const [createdOrganization, setCreatedOrganization] =
    useState<CreatedOrganizationState | null>(null);

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
                    프런트 overview를 확인하거나, 클라이언트에서 이 키로 이벤트를 전송할 수
                    있습니다.
                  </Text>
                </Stack>
              </Paper>
            </SimpleGrid>

            <Alert color="yellow" radius="lg" variant="light">
              이후에는 전체 API key를 다시 조회할 수 없고, prefix와 rotate 기능만 사용합니다.
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
        subtitle={`${data.loginId} 계정이 접근 가능한 organization 목록입니다. organization을 하나 고르면 overview 대시보드로 이동합니다.`}
      />
      <Container size="lg" pb={72}>
        <Stack gap="xl">
          <Group justify="space-between" align="center">
            <Stack gap={4}>
              <Text fw={700} size="xl">
                접근 가능한 organization
              </Text>
              <Text c="dimmed" size="sm">
                현재 계정의 membership을 기준으로 접근 가능한 조직만 표시합니다.
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
                      organization 이름을 입력하면 OWNER membership과 API key가 함께
                      생성됩니다.
                    </Text>
                  </div>
                </Group>
              </Group>

              {createErrorMessage ? (
                <Alert
                  color="red"
                  icon={<IconAlertCircle size={18} />}
                  radius="lg"
                  variant="light"
                >
                  {createErrorMessage}
                </Alert>
              ) : null}

              <form onSubmit={handleCreateOrganization}>
                <Group align="flex-end">
                  <TextInput
                    label="Organization Name"
                    placeholder="My Product Team"
                    required
                    radius="xl"
                    size="md"
                    style={{ flex: 1 }}
                    value={newOrganizationName}
                    onChange={(event) => setNewOrganizationName(event.currentTarget.value)}
                  />
                  <Button
                    type="submit"
                    radius="xl"
                    size="md"
                    loading={isCreating}
                    rightSection={<IconArrowRight size={16} />}
                  >
                    생성하고 바로 보기
                  </Button>
                </Group>
              </form>
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
                    위의 생성 폼으로 첫 organization을 만들면, OWNER 권한으로 overview
                    대시보드에 바로 들어갈 수 있습니다.
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

                    <Group justify="flex-end">
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

"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { ConsoleHeader } from "@/components/common/console-header";
import {
  fetchMe,
  fetchOrganizationApiKeyMetadata,
  rotateOrganizationApiKey,
} from "@/lib/api/auth";
import { getAccessToken } from "@/lib/session/token-store";
import { formatDateTime } from "@/lib/utils/format";
import type {
  AdminOrganizationApiKeyMetadataResponse,
  AdminOrganizationApiKeyRotateResponse,
} from "@/types/auth";
import {
  Alert,
  Badge,
  Button,
  Code,
  Container,
  CopyButton,
  Group,
  Loader,
  Modal,
  Paper,
  SimpleGrid,
  Stack,
  Text,
  Title,
} from "@mantine/core";
import {
  IconAlertCircle,
  IconCheck,
  IconCopy,
  IconKey,
  IconRefresh,
} from "@tabler/icons-react";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

type ApiKeyPageState = {
  organizationName: string;
  currentRole: string | null;
  metadata: AdminOrganizationApiKeyMetadataResponse | null;
};

type RotatedApiKeyState = {
  organizationName: string;
  apiKey: string;
  apiKeyPrefix: string;
  rotatedAt: string | null;
};

export default function ApiKeyPage() {
  const router = useRouter();
  const params = useParams<{ organizationId: string }>();
  const [data, setData] = useState<ApiKeyPageState | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRotating, setIsRotating] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [rotatedApiKey, setRotatedApiKey] = useState<RotatedApiKeyState | null>(null);

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
        const me = await fetchMe(accessToken);
        const currentMembership = me.memberships.find(
          (membership) => String(membership.organizationId) === params.organizationId,
        );

        let metadata: AdminOrganizationApiKeyMetadataResponse | null = null;

        try {
          metadata = await fetchOrganizationApiKeyMetadata(accessToken, params.organizationId);
        } catch (error) {
          const status =
            "status" in (error as object)
              ? (error as { status?: number }).status
              : undefined;

          if (status !== 403) {
            throw error;
          }
        }

        setData({
          organizationName: currentMembership?.organizationName ?? `Organization ${params.organizationId}`,
          currentRole: currentMembership?.role ?? null,
          metadata,
        });
      } catch (error) {
        const status =
          "status" in (error as object) ? (error as { status?: number }).status : undefined;

        if (status === 401) {
          router.replace("/login");
          return;
        }

        setErrorMessage(
          error instanceof Error ? error.message : "API key 정보를 불러오지 못했습니다.",
        );
      } finally {
        setIsLoading(false);
      }
    }

    void load();
  }, [params.organizationId, router]);

  async function handleRotate() {
    const accessToken = getAccessToken();

    if (!accessToken) {
      router.replace("/login");
      return;
    }

    setActionError(null);
    setIsRotating(true);

    try {
      const result: AdminOrganizationApiKeyRotateResponse = await rotateOrganizationApiKey(
        accessToken,
        params.organizationId,
      );

      setData((previous) =>
        previous
          ? {
              ...previous,
              metadata: previous.metadata
                ? {
                    ...previous.metadata,
                    apiKeyPrefix: result.apiKeyPrefix,
                    rotatedAt: result.rotatedAt,
                  }
                : previous.metadata,
            }
          : previous,
      );

      setRotatedApiKey({
        organizationName: data?.organizationName ?? `Organization ${params.organizationId}`,
        apiKey: result.apiKey,
        apiKeyPrefix: result.apiKeyPrefix,
        rotatedAt: result.rotatedAt,
      });
    } catch (error) {
      setActionError(
        error instanceof Error ? error.message : "API key를 재발급하지 못했습니다.",
      );
    } finally {
      setIsRotating(false);
    }
  }

  if (isLoading) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="API Key 관리"
          subtitle="수집용 API key 상태를 불러오는 중입니다."
          backHref={`/dashboard/${params.organizationId}`}
          badge="Settings"
        />
        <Container size="lg" py={96}>
          <Stack align="center" gap="md">
            <Loader color="blue" />
            <Text c="dimmed">API key 정보를 불러오는 중입니다.</Text>
          </Stack>
        </Container>
      </ConsoleFrame>
    );
  }

  if (errorMessage || !data) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="API Key 관리"
          subtitle="수집용 API key 상태를 확인하고 재발급합니다."
          backHref={`/dashboard/${params.organizationId}`}
          badge="Settings"
        />
        <Container size="lg" py={96}>
          <Alert color="red" icon={<IconAlertCircle size={18} />} radius="lg" variant="light">
            {errorMessage ?? "API key 정보를 불러오지 못했습니다."}
          </Alert>
        </Container>
      </ConsoleFrame>
    );
  }

  return (
    <ConsoleFrame>
      <Modal
        centered
        closeOnClickOutside={false}
        closeOnEscape={false}
        opened={rotatedApiKey !== null}
        radius="28px"
        size="lg"
        title="새 API Key가 발급되었습니다"
        onClose={() => setRotatedApiKey(null)}
      >
        {rotatedApiKey ? (
          <Stack gap="lg">
            <Text c="dimmed" size="sm">
              <Text component="span" fw={700} inherit>
                {rotatedApiKey.organizationName}
              </Text>
              의 새 수집용 API key입니다. 전체 키는 지금 한 번만 확인할 수 있습니다.
            </Text>

            <Paper radius="24px" p="lg" withBorder bg="gray.0">
              <Stack gap="xs">
                <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                  New API Key
                </Text>
                <Code block>{rotatedApiKey.apiKey}</Code>
              </Stack>
            </Paper>

            <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="md">
              <Paper radius="20px" p="md" withBorder>
                <Stack gap={4}>
                  <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                    Prefix
                  </Text>
                  <Text fw={700}>{rotatedApiKey.apiKeyPrefix}</Text>
                </Stack>
              </Paper>
              <Paper radius="20px" p="md" withBorder>
                <Stack gap={4}>
                  <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                    Rotated At
                  </Text>
                  <Text fw={700}>{formatDateTime(rotatedApiKey.rotatedAt)}</Text>
                </Stack>
              </Paper>
            </SimpleGrid>

            <Alert color="yellow" radius="lg" variant="light">
              기존 키는 더 이상 사용할 수 없습니다. 이벤트를 보내는 클라이언트 설정을 새 키로 바로 교체하세요.
            </Alert>

            <Group justify="space-between">
              <CopyButton value={rotatedApiKey.apiKey}>
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
              <Button radius="xl" onClick={() => setRotatedApiKey(null)}>
                확인
              </Button>
            </Group>
          </Stack>
        ) : null}
      </Modal>

      <ConsoleHeader
        title="API Key 관리"
        subtitle={`${data.organizationName}의 수집용 API key 상태를 확인하고 새 키로 재발급합니다.`}
        backHref={`/dashboard/${params.organizationId}`}
        badge="Settings"
      />

      <Container size="xl" pb={72}>
        <Stack gap="xl">
          <Paper radius="32px" p={40} shadow="sm" withBorder className="console-panel">
            <Stack gap="lg">
              <Group justify="space-between" align="flex-start">
                <div>
                  <Badge color="dark" leftSection={<IconKey size={12} />} mb="md" variant="light">
                    API Key
                  </Badge>
                  <Title order={1}>수집용 API key</Title>
                  <Text c="dimmed" mt="sm">
                    현재 key 상태를 확인하고, 필요한 경우 새 키로 재발급합니다.
                  </Text>
                </div>
                <Stack gap="xs" align="flex-end">
                  <Badge color={data.currentRole === "OWNER" ? "dark" : "gray"} radius="xl" variant="light">
                    현재 역할: {data.currentRole ?? "UNKNOWN"}
                  </Badge>
                  <Button
                    color="dark"
                    leftSection={<IconRefresh size={16} />}
                    disabled={!data.metadata || data.currentRole !== "OWNER"}
                    loading={isRotating}
                    radius="xl"
                    variant="light"
                    onClick={handleRotate}
                  >
                    API Key 재발급
                  </Button>
                </Stack>
              </Group>

              {actionError ? (
                <Alert color="red" icon={<IconAlertCircle size={18} />} radius="lg" variant="light">
                  {actionError}
                </Alert>
              ) : null}

              {data.metadata ? (
                <SimpleGrid cols={{ base: 1, md: 2, xl: 4 }} spacing="md">
                  <Paper radius="20px" p="md" withBorder bg="gray.0">
                    <Stack gap={4}>
                      <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                        Prefix
                      </Text>
                      <Text fw={700}>{data.metadata.apiKeyPrefix}</Text>
                    </Stack>
                  </Paper>
                  <Paper radius="20px" p="md" withBorder bg="gray.0">
                    <Stack gap={4}>
                      <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                        Status
                      </Text>
                      <Text fw={700}>{data.metadata.status}</Text>
                    </Stack>
                  </Paper>
                  <Paper radius="20px" p="md" withBorder bg="gray.0">
                    <Stack gap={4}>
                      <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                        Last Used
                      </Text>
                      <Text fw={700}>{formatDateTime(data.metadata.lastUsedAt)}</Text>
                    </Stack>
                  </Paper>
                  <Paper radius="20px" p="md" withBorder bg="gray.0">
                    <Stack gap={4}>
                      <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                        Rotated At
                      </Text>
                      <Text fw={700}>{formatDateTime(data.metadata.rotatedAt)}</Text>
                    </Stack>
                  </Paper>
                </SimpleGrid>
              ) : (
                <Alert color="gray" radius="lg" variant="light">
                  현재 역할은 <strong>{data.currentRole ?? "UNKNOWN"}</strong> 입니다. API key 정보 조회는
                  ADMIN 이상, 재발급은 OWNER만 가능합니다.
                </Alert>
              )}
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </ConsoleFrame>
  );
}

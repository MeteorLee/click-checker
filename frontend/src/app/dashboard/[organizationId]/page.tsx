"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { ConsoleHeader } from "@/components/common/console-header";
import { OverviewCards } from "@/components/dashboard/overview-cards";
import { SummaryCard } from "@/components/dashboard/summary-card";
import {
  fetchMe,
  fetchOrganizationApiKeyMetadata,
  rotateOrganizationApiKey,
} from "@/lib/api/auth";
import { fetchOverview } from "@/lib/api/analytics";
import { getAccessToken } from "@/lib/session/token-store";
import { getOverviewRange } from "@/lib/utils/date";
import {
  formatDateTime,
  formatNumber,
  formatPercent,
} from "@/lib/utils/format";
import type { ActivityOverviewResponse } from "@/types/analytics";
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
  Select,
  SegmentedControl,
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

type OverviewRangePreset = "1d" | "7d" | "30d";

type DashboardState = {
  organizationName: string;
  memberships: {
    value: string;
    label: string;
    role: string;
  }[];
  range: {
    from: string;
    to: string;
    displayFrom: string;
    displayTo: string;
  };
  overview: ActivityOverviewResponse;
  apiKeyMetadata: AdminOrganizationApiKeyMetadataResponse;
};

type RotatedApiKeyState = {
  organizationId: string;
  organizationName: string;
  apiKey: string;
  apiKeyPrefix: string;
  rotatedAt: string | null;
};

export default function DashboardPage() {
  const router = useRouter();
  const params = useParams<{ organizationId: string }>();
  const [data, setData] = useState<DashboardState | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedRange, setSelectedRange] = useState<OverviewRangePreset>("7d");
  const [isRotatingApiKey, setIsRotatingApiKey] = useState(false);
  const [apiKeyActionError, setApiKeyActionError] = useState<string | null>(null);
  const [rotatedApiKey, setRotatedApiKey] = useState<RotatedApiKeyState | null>(null);

  function resolveRangeDays(rangePreset: OverviewRangePreset) {
    if (rangePreset === "1d") {
      return 1;
    }

    if (rangePreset === "30d") {
      return 30;
    }

    return 7;
  }

  useEffect(() => {
    async function load() {
      const accessToken = getAccessToken();

      if (!accessToken) {
        router.replace("/login");
        return;
      }

      const organizationId = params.organizationId;
      const range = getOverviewRange(resolveRangeDays(selectedRange));

      setIsLoading(true);
      setErrorMessage(null);

      try {
        const [me, overview] = await Promise.all([
          fetchMe(accessToken),
          fetchOverview(accessToken, organizationId, range.from, range.to),
        ]);

        const currentMembership = me.memberships.find(
          (membership) => String(membership.organizationId) === organizationId,
        );

        const apiKeyMetadata = await fetchOrganizationApiKeyMetadata(
          accessToken,
          organizationId,
        );

        setData({
          organizationName: currentMembership?.organizationName ?? `Organization ${organizationId}`,
          memberships: me.memberships.map((membership) => ({
            value: String(membership.organizationId),
            label: membership.organizationName,
            role: membership.role,
          })),
          range,
          overview,
          apiKeyMetadata,
        });
      } catch (error) {
        const status = "status" in (error as object) ? (error as { status?: number }).status : undefined;

        if (status === 401) {
          router.replace("/login");
          return;
        }

        const message =
          error instanceof Error
            ? error.message
            : "overview 데이터를 불러오지 못했습니다.";
        setErrorMessage(message);
      } finally {
        setIsLoading(false);
      }
    }

    void load();
  }, [params.organizationId, router, selectedRange]);

  if (isLoading) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="Overview 대시보드"
          subtitle="선택된 organization의 overview 데이터를 불러오는 중입니다."
          backHref="/organizations"
          badge="Analytics"
        />
        <Container size="lg" py={96}>
          <Stack align="center" gap="md">
            <Loader color="blue" />
            <Text c="dimmed">overview 데이터를 불러오는 중입니다.</Text>
          </Stack>
        </Container>
      </ConsoleFrame>
    );
  }

  if (errorMessage) {
    return (
      <ConsoleFrame>
        <ConsoleHeader
          title="Overview 대시보드"
          subtitle="선택된 organization의 overview 데이터를 확인합니다."
          backHref="/organizations"
          badge="Analytics"
        />
        <Container size="md" py={96}>
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

  async function handleRotateApiKey() {
    const accessToken = getAccessToken();
    const currentData = data;

    if (!accessToken) {
      router.replace("/login");
      return;
    }

    if (!currentData) {
      return;
    }

    setApiKeyActionError(null);
    setIsRotatingApiKey(true);

    try {
      const result: AdminOrganizationApiKeyRotateResponse =
        await rotateOrganizationApiKey(accessToken, params.organizationId);

      setData((previous) =>
        previous
          ? {
              ...previous,
              apiKeyMetadata: {
                ...previous.apiKeyMetadata,
                apiKeyPrefix: result.apiKeyPrefix,
                rotatedAt: result.rotatedAt,
              },
            }
          : previous,
      );

      setRotatedApiKey({
        organizationId: params.organizationId,
        organizationName: currentData.organizationName,
        apiKey: result.apiKey,
        apiKeyPrefix: result.apiKeyPrefix,
        rotatedAt: result.rotatedAt,
      });
    } catch (error) {
      setApiKeyActionError(
        error instanceof Error
          ? error.message
          : "API key를 회전하지 못했습니다.",
      );
    } finally {
      setIsRotatingApiKey(false);
    }
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
              기존 키는 더 이상 사용되지 않습니다. 클라이언트 설정을 새 키로 바로 교체하세요.
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
        title={data.organizationName}
        subtitle={`${selectedRange === "1d" ? "최근 1일" : selectedRange === "30d" ? "최근 30일" : "최근 7일"} 기준 overview입니다. 기간은 ${data.range.displayFrom}부터 ${data.range.displayTo}까지입니다.`}
        backHref="/organizations"
        badge="Analytics"
      />
      <Container size="xl" pb={72}>
        <Stack gap="xl">
          <Paper radius="32px" p={40} shadow="sm" withBorder className="console-panel">
            <Stack gap="lg">
              <Group justify="space-between" align="flex-start">
                <div>
                  <Badge color="blue" variant="light" mb="md">
                    Admin Overview
                  </Badge>
                  <Title order={1}>핵심 overview</Title>
                  <Text c="dimmed" mt="sm">
                    현재 organization의 핵심 KPI와 정규화 커버리지, 상위 route/event type
                    요약을 한 화면에서 확인합니다.
                  </Text>
                </div>
                <Stack gap="xs" align="flex-end">
                  <Select
                    aria-label="organization 전환"
                    data={data.memberships.map((membership) => ({
                      value: membership.value,
                      label: `${membership.label} · ${membership.role}`,
                    }))}
                    radius="xl"
                    size="sm"
                    value={params.organizationId}
                    w={280}
                    onChange={(value) => {
                      if (value && value !== params.organizationId) {
                        router.push(`/dashboard/${value}`);
                      }
                    }}
                  />
                  <Text c="dimmed" fw={600} size="sm">
                    조회 기간
                  </Text>
                  <SegmentedControl
                    data={[
                      { label: "1일", value: "1d" },
                      { label: "7일", value: "7d" },
                      { label: "30일", value: "30d" },
                    ]}
                    radius="xl"
                    value={selectedRange}
                    onChange={(value) => setSelectedRange(value as OverviewRangePreset)}
                  />
                </Stack>
              </Group>

              <SimpleGrid cols={{ base: 1, md: 3 }} spacing="lg">
                <Paper radius="24px" p="lg" bg="blue.0" className="console-soft-panel">
                  <Stack gap={4}>
                    <Text fw={700} size="sm" c="blue.8">
                      Route Match Coverage
                    </Text>
                    <Text fw={800} size="1.6rem">
                      {formatPercent(data.overview.routeMatchCoverage)}
                    </Text>
                  </Stack>
                </Paper>
                <Paper radius="24px" p="lg" bg="teal.0" className="console-soft-panel">
                  <Stack gap={4}>
                    <Text fw={700} size="sm" c="teal.8">
                      Event Type Mapping Coverage
                    </Text>
                    <Text fw={800} size="1.6rem">
                      {formatPercent(data.overview.eventTypeMappingCoverage)}
                    </Text>
                  </Stack>
                </Paper>
                <Paper radius="24px" p="lg" bg="gray.0" className="console-soft-panel">
                  <Stack gap={4}>
                    <Text fw={700} size="sm" c="dark.6">
                      Organization ID
                    </Text>
                    <Text fw={800} size="1.6rem">
                      {formatNumber(data.overview.organizationId)}
                    </Text>
                  </Stack>
                </Paper>
              </SimpleGrid>

              <Paper radius="24px" p="lg" withBorder bg="gray.0">
                <Stack gap="md">
                  <Group justify="space-between" align="flex-start">
                    <Group gap="sm" wrap="nowrap">
                      <Badge color="dark" leftSection={<IconKey size={12} />} variant="light">
                        API Key
                      </Badge>
                      <Stack gap={0}>
                        <Text fw={700}>수집용 API key 관리</Text>
                        <Text c="dimmed" size="sm">
                          prefix와 사용 상태를 확인하고 필요하면 새 키로 rotate할 수 있습니다.
                        </Text>
                      </Stack>
                    </Group>
                    <Button
                      color="dark"
                      leftSection={<IconRefresh size={16} />}
                      loading={isRotatingApiKey}
                      radius="xl"
                      variant="light"
                      onClick={handleRotateApiKey}
                    >
                      API Key 재발급
                    </Button>
                  </Group>

                  {apiKeyActionError ? (
                    <Alert
                      color="red"
                      icon={<IconAlertCircle size={18} />}
                      radius="lg"
                      variant="light"
                    >
                      {apiKeyActionError}
                    </Alert>
                  ) : null}

                  <SimpleGrid cols={{ base: 1, md: 2, xl: 4 }} spacing="md">
                    <Paper radius="20px" p="md" withBorder bg="white">
                      <Stack gap={4}>
                        <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                          Prefix
                        </Text>
                        <Text fw={700}>{data.apiKeyMetadata.apiKeyPrefix}</Text>
                      </Stack>
                    </Paper>
                    <Paper radius="20px" p="md" withBorder bg="white">
                      <Stack gap={4}>
                        <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                          Status
                        </Text>
                        <Text fw={700}>{data.apiKeyMetadata.status}</Text>
                      </Stack>
                    </Paper>
                    <Paper radius="20px" p="md" withBorder bg="white">
                      <Stack gap={4}>
                        <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                          Last Used
                        </Text>
                        <Text fw={700}>{formatDateTime(data.apiKeyMetadata.lastUsedAt)}</Text>
                      </Stack>
                    </Paper>
                    <Paper radius="20px" p="md" withBorder bg="white">
                      <Stack gap={4}>
                        <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                          Rotated At
                        </Text>
                        <Text fw={700}>{formatDateTime(data.apiKeyMetadata.rotatedAt)}</Text>
                      </Stack>
                    </Paper>
                  </SimpleGrid>
                </Stack>
              </Paper>
            </Stack>
          </Paper>

          <OverviewCards overview={data.overview} />

          <SimpleGrid cols={{ base: 1, xl: 2 }} spacing="lg">
            <SummaryCard
              title="Top Routes"
              description="Route template 정규화 기준 상위 경로"
              emptyMessage="표시할 route 데이터가 없습니다."
              items={data.overview.topRoutes.map((route) => ({
                label: route.routeKey,
                value: `${formatNumber(route.count)} events`,
              }))}
            />
            <SummaryCard
              title="Top Event Types"
              description="Canonical event type 기준 상위 이벤트 타입"
              emptyMessage="표시할 event type 데이터가 없습니다."
              items={data.overview.topEventTypes.map((eventType) => ({
                label: eventType.eventType,
                value: `${formatNumber(eventType.count)} events`,
              }))}
            />
          </SimpleGrid>

          <Group justify="space-between" wrap="wrap">
            <Alert color="blue" radius="lg" variant="light" style={{ flex: 1 }}>
              현재 선택된 preset은{" "}
              {selectedRange === "1d" ? "최근 1일" : selectedRange === "30d" ? "최근 30일" : "최근 7일"}입니다.
              organization은 상단 select에서 바로 전환할 수 있고, 이후에는 사용자 지정 날짜
              선택도 추가할 수 있습니다.
            </Alert>
            <Button radius="xl" variant="light" onClick={() => setSelectedRange("7d")}>
              기본 기간으로 되돌리기
            </Button>
          </Group>
        </Stack>
      </Container>
    </ConsoleFrame>
  );
}

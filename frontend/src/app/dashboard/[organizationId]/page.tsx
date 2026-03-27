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
import {
  getCustomOverviewRange,
  getInclusiveDateRangeLengthDays,
  getOverviewRange,
  MAX_ANALYTICS_RANGE_DAYS,
} from "@/lib/utils/date";
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
  Popover,
  Paper,
  SegmentedControl,
  SimpleGrid,
  Stack,
  Text,
  TextInput,
  ThemeIcon,
  Title,
  UnstyledButton,
} from "@mantine/core";
import {
  IconAlertCircle,
  IconBolt,
  IconChartLine,
  IconCheck,
  IconCopy,
  IconFilter,
  IconKey,
  IconRefresh,
  IconRepeat,
  IconUsers,
  IconArrowRight,
} from "@tabler/icons-react";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

type OverviewRangePreset = "1d" | "7d" | "30d" | "custom";

type DashboardState = {
  organizationName: string;
  currentRole: string | null;
  range: {
    from: string;
    to: string;
    displayFrom: string;
    displayTo: string;
  };
  overview: ActivityOverviewResponse;
  apiKeyMetadata: AdminOrganizationApiKeyMetadataResponse | null;
};

type RotatedApiKeyState = {
  organizationId: string;
  organizationName: string;
  apiKey: string;
  apiKeyPrefix: string;
  rotatedAt: string | null;
};

type AppliedRange = {
  from: string;
  to: string;
  displayFrom: string;
  displayTo: string;
};

export default function DashboardPage() {
  const router = useRouter();
  const params = useParams<{ organizationId: string }>();
  const [data, setData] = useState<DashboardState | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedRange, setSelectedRange] = useState<OverviewRangePreset>("7d");
  const [appliedRange, setAppliedRange] = useState<AppliedRange>(getOverviewRange(7));
  const [customFrom, setCustomFrom] = useState<string>(getOverviewRange(7).displayFrom);
  const [customTo, setCustomTo] = useState<string>(getOverviewRange(7).displayTo);
  const [rangeValidationMessage, setRangeValidationMessage] = useState<string | null>(null);
  const [isRangePopoverOpened, setIsRangePopoverOpened] = useState(false);
  const [isRotatingApiKey, setIsRotatingApiKey] = useState(false);
  const [apiKeyActionError, setApiKeyActionError] = useState<string | null>(null);
  const [rotatedApiKey, setRotatedApiKey] = useState<RotatedApiKeyState | null>(null);

  const analyticsLinks = [
    {
      title: "시계열 추이",
      description: "이벤트 수와 고유 사용자 수 변화를 시간축으로 봅니다.",
      color: "grape",
      icon: IconChartLine,
      href: `/dashboard/${params.organizationId}/trends`,
    },
    {
      title: "사용자 현황",
      description: "신규/재방문 사용자와 평균 이벤트 수를 확인합니다.",
      color: "cyan",
      icon: IconUsers,
      href: `/dashboard/${params.organizationId}/users`,
    },
    {
      title: "활동량",
      description: "일별 활동량과 시간대 분포를 비교합니다.",
      color: "orange",
      icon: IconBolt,
      href: `/dashboard/${params.organizationId}/activity`,
    },
    {
      title: "유지율",
      description: "기준일 코호트별 재방문 비율을 확인합니다.",
      color: "violet",
      icon: IconRepeat,
      href: `/dashboard/${params.organizationId}/retention`,
    },
    {
      title: "Funnel",
      description: "단계별 전환율과 이탈 구간을 분석합니다.",
      color: "lime",
      icon: IconFilter,
      href: `/dashboard/${params.organizationId}/funnels`,
    },
  ] as const;

  function getRangeLengthDays(range: AppliedRange) {
    const start = new Date(`${range.displayFrom}T00:00:00`);
    const end = new Date(`${range.displayTo}T00:00:00`);
    const diff = end.getTime() - start.getTime();

    return Math.floor(diff / (1000 * 60 * 60 * 24)) + 1;
  }

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
    if (selectedRange === "custom") {
      return;
    }

    const nextRange = getOverviewRange(resolveRangeDays(selectedRange));
    setAppliedRange(nextRange);
    setCustomFrom(nextRange.displayFrom);
    setCustomTo(nextRange.displayTo);
    setRangeValidationMessage(null);
  }, [selectedRange]);

  useEffect(() => {
    async function load() {
      const accessToken = getAccessToken();

      if (!accessToken) {
        router.replace("/login");
        return;
      }

      const organizationId = params.organizationId;
      const range = appliedRange;

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

        let apiKeyMetadata: AdminOrganizationApiKeyMetadataResponse | null = null;

        try {
          apiKeyMetadata = await fetchOrganizationApiKeyMetadata(
            accessToken,
            organizationId,
          );
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
          organizationName: currentMembership?.organizationName ?? `Organization ${organizationId}`,
          currentRole: currentMembership?.role ?? null,
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
  }, [appliedRange, params.organizationId, router]);

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
              apiKeyMetadata: previous.apiKeyMetadata
                ? {
                    ...previous.apiKeyMetadata,
                    apiKeyPrefix: result.apiKeyPrefix,
                    rotatedAt: result.rotatedAt,
                  }
                : previous.apiKeyMetadata,
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

  function handleApplyCustomRange() {
    if (!customFrom || !customTo) {
      setRangeValidationMessage("시작일과 종료일을 모두 선택하세요.");
      return;
    }

    if (customFrom > customTo) {
      setRangeValidationMessage("시작일은 종료일보다 늦을 수 없습니다.");
      return;
    }

    const rangeLengthDays = getInclusiveDateRangeLengthDays(customFrom, customTo);
    if (rangeLengthDays > MAX_ANALYTICS_RANGE_DAYS) {
      setRangeValidationMessage(`조회 기간은 최대 ${MAX_ANALYTICS_RANGE_DAYS}일까지 선택할 수 있습니다.`);
      return;
    }

    const nextRange = getCustomOverviewRange(customFrom, customTo);
    setSelectedRange("custom");
    setAppliedRange(nextRange);
    setRangeValidationMessage(null);
    setIsRangePopoverOpened(false);
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
        title={data.organizationName}
        subtitle={`${selectedRange === "1d" ? "오늘" : selectedRange === "30d" ? "최근 30일" : selectedRange === "custom" ? "사용자 지정 기간" : "최근 7일"} 기준 overview입니다.`}
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
                    현재 organization의 핵심 지표, 정규화 상태, 상위 경로와 이벤트 타입 요약을 한 화면에서 확인합니다.
                  </Text>
                </div>
                <Stack gap="xs" align="flex-end">
                  <Text c="dimmed" fw={600} size="sm">
                    조회 기간
                  </Text>
                  <Popover
                    opened={isRangePopoverOpened}
                    position="bottom-end"
                    shadow="md"
                    width={420}
                    withArrow
                    onChange={setIsRangePopoverOpened}
                  >
                    <Popover.Target>
                      <Button
                        radius="xl"
                        variant="light"
                        onClick={() => setIsRangePopoverOpened((opened) => !opened)}
                      >
                        {selectedRange === "custom"
                          ? `${data.range.displayFrom} ~ ${data.range.displayTo} · ${getRangeLengthDays(data.range)}일`
                          : selectedRange === "1d"
                            ? "오늘"
                            : selectedRange === "30d"
                              ? "최근 30일"
                              : "최근 7일"}
                      </Button>
                    </Popover.Target>
                    <Popover.Dropdown>
                      <Stack gap="md">
                        <div>
                          <Text fw={700}>조회 기간 설정</Text>
                          <Text c="dimmed" size="sm">
                            현재 적용 중인 기간은 {data.range.displayFrom}부터 {data.range.displayTo}
                            까지, 총 {getRangeLengthDays(data.range)}일입니다.
                          </Text>
                        </div>

                        <SegmentedControl
                          data={[
                            { label: "1일", value: "1d" },
                            { label: "7일", value: "7d" },
                            { label: "30일", value: "30d" },
                          ]}
                          radius="xl"
                          value={selectedRange === "custom" ? "7d" : selectedRange}
                          onChange={(value) => setSelectedRange(value as OverviewRangePreset)}
                        />

                        {rangeValidationMessage ? (
                          <Alert
                            color="red"
                            icon={<IconAlertCircle size={18} />}
                            radius="lg"
                            variant="light"
                          >
                            {rangeValidationMessage}
                          </Alert>
                        ) : null}

                        <Group align="flex-end" wrap="wrap">
                          <TextInput
                            label="시작일"
                            radius="xl"
                            type="date"
                            value={customFrom}
                            onChange={(event) => setCustomFrom(event.currentTarget.value)}
                          />
                          <TextInput
                            label="종료일"
                            radius="xl"
                            type="date"
                            value={customTo}
                            onChange={(event) => setCustomTo(event.currentTarget.value)}
                          />
                          <Button radius="xl" onClick={handleApplyCustomRange}>
                            적용
                          </Button>
                        </Group>

                        <Text c="dimmed" size="sm">
                          종료일은 포함해서 계산합니다.
                        </Text>
                      </Stack>
                    </Popover.Dropdown>
                  </Popover>
                </Stack>
              </Group>

              <SimpleGrid cols={{ base: 1, sm: 2, xl: 5 }} spacing="md">
                {analyticsLinks.map((item) => {
                  const Icon = item.icon;

                  return (
                    <Paper
                      key={item.href}
                      component="button"
                      p="lg"
                      radius="24px"
                      shadow="xs"
                      type="button"
                      withBorder
                      style={{
                        cursor: "pointer",
                        textAlign: "left",
                        background:
                          item.color === "grape"
                            ? "linear-gradient(180deg, rgba(124, 58, 237, 0.08), rgba(255, 255, 255, 0.98))"
                            : item.color === "cyan"
                              ? "linear-gradient(180deg, rgba(8, 145, 178, 0.08), rgba(255, 255, 255, 0.98))"
                              : item.color === "orange"
                                ? "linear-gradient(180deg, rgba(234, 88, 12, 0.08), rgba(255, 255, 255, 0.98))"
                                : item.color === "violet"
                                  ? "linear-gradient(180deg, rgba(139, 92, 246, 0.08), rgba(255, 255, 255, 0.98))"
                                  : "linear-gradient(180deg, rgba(101, 163, 13, 0.08), rgba(255, 255, 255, 0.98))",
                        transition:
                          "transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease",
                      }}
                      onMouseEnter={(event) => {
                        event.currentTarget.style.transform = "translateY(-2px)";
                        event.currentTarget.style.boxShadow =
                          "0 18px 36px rgba(15, 23, 42, 0.08)";
                        event.currentTarget.style.borderColor = "rgba(59, 130, 246, 0.2)";
                      }}
                      onMouseLeave={(event) => {
                        event.currentTarget.style.transform = "translateY(0)";
                        event.currentTarget.style.boxShadow = "";
                        event.currentTarget.style.borderColor = "";
                      }}
                      onClick={() => router.push(item.href)}
                    >
                      <Stack gap="md">
                        <Group justify="space-between" align="flex-start" wrap="nowrap">
                          <ThemeIcon color={item.color} radius="xl" size={42} variant="light">
                            <Icon size={20} />
                          </ThemeIcon>
                          <ThemeIcon color={item.color} radius="xl" size={30} variant="subtle">
                            <IconArrowRight size={16} />
                          </ThemeIcon>
                        </Group>
                        <Stack gap={6}>
                          <Text fw={700}>{item.title}</Text>
                          <Text c="dimmed" size="sm">
                            {item.description}
                          </Text>
                          <Text c={item.color} fw={700} size="sm">
                            분석 열기
                          </Text>
                        </Stack>
                      </Stack>
                    </Paper>
                  );
                })}
              </SimpleGrid>

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

            </Stack>
          </Paper>

          <OverviewCards overview={data.overview} />

          <SimpleGrid cols={{ base: 1, xl: 2 }} spacing="lg">
            <SummaryCard
              title="Top Routes"
              description="정규화된 route 기준 상위 경로"
              actionHref={`/dashboard/${params.organizationId}/routes`}
              actionLabel="Route 상세"
              emptyMessage="표시할 route 데이터가 없습니다."
              items={data.overview.topRoutes.map((route) => ({
                label: route.routeKey,
                value: `${formatNumber(route.count)} events`,
              }))}
            />
            <SummaryCard
              title="Top Event Types"
              description="정규화된 event type 기준 상위 이벤트"
              actionHref={`/dashboard/${params.organizationId}/event-types`}
              actionLabel="Event Type 상세"
              emptyMessage="표시할 event type 데이터가 없습니다."
              items={data.overview.topEventTypes.map((eventType) => ({
                label: eventType.eventType,
                value: `${formatNumber(eventType.count)} events`,
              }))}
            />
          </SimpleGrid>

          <Paper radius="28px" p="xl" withBorder className="console-panel">
            <Stack gap="lg">
              <Group justify="space-between" align="flex-start">
                <Group gap="sm" wrap="nowrap">
                  <Badge color="dark" leftSection={<IconKey size={12} />} variant="light">
                    API Key
                  </Badge>
                  <Stack gap={0}>
                    <Text fw={700}>수집용 API key 관리</Text>
                    <Text c="dimmed" size="sm">
                      overview 아래에서 현재 key 상태를 확인하고, 필요한 경우 새 키로 재발급합니다.
                    </Text>
                  </Stack>
                </Group>
                <Button
                  color="dark"
                  leftSection={<IconRefresh size={16} />}
                  disabled={!data.apiKeyMetadata || data.currentRole !== "OWNER"}
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

              {data.apiKeyMetadata ? (
                <SimpleGrid cols={{ base: 1, md: 2, xl: 4 }} spacing="md">
                  <Paper radius="20px" p="md" withBorder bg="gray.0">
                    <Stack gap={4}>
                      <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                        Prefix
                      </Text>
                      <Text fw={700}>{data.apiKeyMetadata.apiKeyPrefix}</Text>
                    </Stack>
                  </Paper>
                  <Paper radius="20px" p="md" withBorder bg="gray.0">
                    <Stack gap={4}>
                      <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                        Status
                      </Text>
                      <Text fw={700}>{data.apiKeyMetadata.status}</Text>
                    </Stack>
                  </Paper>
                  <Paper radius="20px" p="md" withBorder bg="gray.0">
                    <Stack gap={4}>
                      <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                        Last Used
                      </Text>
                      <Text fw={700}>{formatDateTime(data.apiKeyMetadata.lastUsedAt)}</Text>
                    </Stack>
                  </Paper>
                  <Paper radius="20px" p="md" withBorder bg="gray.0">
                    <Stack gap={4}>
                      <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                        Rotated At
                      </Text>
                      <Text fw={700}>{formatDateTime(data.apiKeyMetadata.rotatedAt)}</Text>
                    </Stack>
                  </Paper>
                </SimpleGrid>
              ) : (
                <Alert color="gray" radius="lg" variant="light">
                  현재 역할은 <strong>{data.currentRole ?? "UNKNOWN"}</strong> 입니다. API key 정보 조회는
                  ADMIN 이상, 재발급은 OWNER만 가능합니다. overview 조회는 계속 사용할 수 있습니다.
                </Alert>
              )}
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </ConsoleFrame>
  );
}

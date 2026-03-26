"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { ConsoleHeader } from "@/components/common/console-header";
import { OverviewCards } from "@/components/dashboard/overview-cards";
import { SummaryCard } from "@/components/dashboard/summary-card";
import { fetchMe } from "@/lib/api/auth";
import { fetchOverview } from "@/lib/api/analytics";
import { getAccessToken } from "@/lib/session/token-store";
import { getOverviewRange } from "@/lib/utils/date";
import { formatNumber, formatPercent } from "@/lib/utils/format";
import type { ActivityOverviewResponse } from "@/types/analytics";
import {
  Alert,
  Badge,
  Button,
  Container,
  Group,
  Loader,
  Paper,
  Select,
  SegmentedControl,
  SimpleGrid,
  Stack,
  Text,
  Title,
} from "@mantine/core";
import { IconAlertCircle } from "@tabler/icons-react";
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
  };
  overview: ActivityOverviewResponse;
};

export default function DashboardPage() {
  const router = useRouter();
  const params = useParams<{ organizationId: string }>();
  const [data, setData] = useState<DashboardState | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedRange, setSelectedRange] = useState<OverviewRangePreset>("7d");

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

        setData({
          organizationName: currentMembership?.organizationName ?? `Organization ${organizationId}`,
          memberships: me.memberships.map((membership) => ({
            value: String(membership.organizationId),
            label: membership.organizationName,
            role: membership.role,
          })),
          range,
          overview,
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

  return (
    <ConsoleFrame>
      <ConsoleHeader
        title={data.organizationName}
        subtitle={`${selectedRange === "1d" ? "최근 1일" : selectedRange === "30d" ? "최근 30일" : "최근 7일"} 기준 overview입니다. 기간은 ${data.range.from}부터 ${data.range.to}까지입니다.`}
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

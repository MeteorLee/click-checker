"use client";

import { GuideFrame } from "@/components/guide/guide-frame";
import { createOrganization, fetchMe } from "@/lib/api/auth";
import { fetchOverview } from "@/lib/api/analytics";
import { buildApiUrl } from "@/lib/api/config";
import { getAccessToken } from "@/lib/session/token-store";
import { getOverviewRange } from "@/lib/utils/date";
import type { AdminMeMembership } from "@/types/auth";
import type { ActivityOverviewResponse } from "@/types/analytics";
import {
  Accordion,
  Alert,
  Badge,
  Button,
  Code,
  Container,
  CopyButton,
  Group,
  Loader,
  Paper,
  SimpleGrid,
  Stack,
  Text,
  TextInput,
  ThemeIcon,
  Title,
} from "@mantine/core";
import {
  IconArrowRight,
  IconBuildingPlus,
  IconCheck,
  IconCode,
  IconCopy,
  IconInfoCircle,
  IconPlayerPlay,
  IconRefresh,
} from "@tabler/icons-react";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

type QuickStartOrganization = {
  organizationId: number;
  organizationName: string;
  apiKey: string;
  apiKeyPrefix: string;
};

type CreateEventResponse = {
  id: number;
};

type MissionStatusProps = {
  label: string;
  done: boolean;
  description: string;
};

function MissionStatus({ label, done, description }: MissionStatusProps) {
  return (
    <Paper
      bg={done ? "teal.0" : "gray.0"}
      p="md"
      radius="20px"
      withBorder
      style={{
        borderColor: done ? "var(--mantine-color-teal-3)" : undefined,
      }}
    >
      <Group align="flex-start" gap="sm" wrap="nowrap">
        <ThemeIcon color={done ? "teal" : "gray"} radius="xl" size={30} variant="light">
          <IconCheck size={16} />
        </ThemeIcon>
        <Stack gap={2}>
          <Text fw={700} size="sm">
            {label}
          </Text>
          <Text c="dimmed" size="sm">
            {description}
          </Text>
        </Stack>
      </Group>
    </Paper>
  );
}

const missionSteps = [
  "조직 만들기",
  "API key 준비",
  "샘플 이벤트 전송",
  "개요 값 확인",
] as const;

function buildQuickStartOrganizationName() {
  const now = new Date();
  const timestamp = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, "0"),
    String(now.getDate()).padStart(2, "0"),
    String(now.getHours()).padStart(2, "0"),
    String(now.getMinutes()).padStart(2, "0"),
    String(now.getSeconds()).padStart(2, "0"),
  ].join("");
  const suffix = Math.floor(Math.random() * 9000 + 1000);
  return `quick-start-${timestamp}-${suffix}`;
}

function buildOverviewRange() {
  const range = getOverviewRange(7);
  return {
    from: range.from,
    to: range.to,
  };
}

export default function QuickStartPage() {
  const [isCheckingSession, setIsCheckingSession] = useState(true);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [membershipCount, setMembershipCount] = useState(0);
  const [quickStartOrganization, setQuickStartOrganization] =
    useState<QuickStartOrganization | null>(null);
  const [overview, setOverview] = useState<ActivityOverviewResponse | null>(null);
  const [sentEventId, setSentEventId] = useState<number | null>(null);
  const [apiKeyInput, setApiKeyInput] = useState("");
  const [isCreatingOrganization, setIsCreatingOrganization] = useState(false);
  const [isSendingEvent, setIsSendingEvent] = useState(false);
  const [isRefreshingOverview, setIsRefreshingOverview] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    async function loadSession() {
      const token = getAccessToken();
      setAccessToken(token);

      if (!token) {
        setIsCheckingSession(false);
        return;
      }

      try {
        const me = await fetchMe(token);
        setMembershipCount(me.memberships.length);
      } catch (error) {
        setErrorMessage(
          error instanceof Error
            ? error.message
            : "Quick Start 상태를 불러오지 못했습니다.",
        );
      } finally {
        setIsCheckingSession(false);
      }
    }

    void loadSession();
  }, []);

  async function refreshOverviewMetrics(
    token: string,
    organization: Pick<AdminMeMembership, "organizationId"> | QuickStartOrganization,
  ) {
    const range = buildOverviewRange();
    const result = await fetchOverview(
      token,
      String(organization.organizationId),
      range.from,
      range.to,
    );
    setOverview(result);
    return result;
  }

  async function handleCreateQuickStartOrganization() {
    if (!accessToken) {
      return;
    }

    setIsCreatingOrganization(true);
    setErrorMessage(null);

    try {
      const result = await createOrganization(accessToken, {
        name: buildQuickStartOrganizationName(),
      });
      const organization = {
        organizationId: result.organizationId,
        organizationName: result.name,
        apiKey: result.apiKey,
        apiKeyPrefix: result.apiKeyPrefix,
      };

      setQuickStartOrganization(organization);
      setMembershipCount((previous) => previous + 1);
      setSentEventId(null);
      setOverview(null);
      setApiKeyInput("");
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "빠른 시작용 조직을 만들지 못했습니다.",
      );
    } finally {
      setIsCreatingOrganization(false);
    }
  }

  async function handleSendSampleEvent() {
    if (!quickStartOrganization) {
      return;
    }

    if (!apiKeyInput.trim()) {
      setErrorMessage("샘플 이벤트를 보내기 전에 API key를 직접 입력해주세요.");
      return;
    }

    setIsSendingEvent(true);
    setErrorMessage(null);

    try {
      const response = await fetch(buildApiUrl("/api/events"), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-API-Key": apiKeyInput.trim(),
        },
        body: JSON.stringify({
          eventType: "page_view",
          path: "/quick-start",
          externalUserId: `quick-start-user-${quickStartOrganization.organizationId}`,
          occurredAt: new Date().toISOString(),
        }),
      });

      if (!response.ok) {
        const data = (await response.json().catch(() => null)) as { message?: string } | null;
        throw new Error(data?.message ?? "샘플 이벤트 전송에 실패했습니다.");
      }

      const data = (await response.json()) as CreateEventResponse;
      setSentEventId(data.id);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "샘플 이벤트 전송에 실패했습니다.",
      );
    } finally {
      setIsSendingEvent(false);
    }
  }

  async function handleCheckOverview() {
    if (!accessToken || !quickStartOrganization) {
      return;
    }

    setIsRefreshingOverview(true);
    setErrorMessage(null);

    try {
      await refreshOverviewMetrics(accessToken, quickStartOrganization);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "overview를 확인하지 못했습니다.",
      );
    } finally {
      setIsRefreshingOverview(false);
    }
  }

  const isLoggedIn = Boolean(accessToken);
  const hasOrganization = Boolean(quickStartOrganization);
  const hasApiKey = Boolean(quickStartOrganization?.apiKey);
  const hasSentEvent = sentEventId !== null;
  const hasCheckedOverview = overview !== null;
  const isOverviewConfirmed = (overview?.totalEvents ?? 0) >= 1;

  const eventExample = useMemo(() => {
    const apiKey = quickStartOrganization?.apiKey ?? "<API_KEY>";
    return `curl -X POST ${buildApiUrl("/api/events")} \\
  -H "Content-Type: application/json" \\
  -H "X-API-Key: ${apiKey}" \\
  -d '{
    "eventType": "page_view",
    "path": "/quick-start",
    "externalUserId": "quick-start-user",
    "occurredAt": "${new Date().toISOString()}"
  }'`;
  }, [quickStartOrganization?.apiKey]);

  if (isCheckingSession) {
    return (
      <GuideFrame>
        <Container size="sm" py={120}>
          <Stack align="center" gap="md">
            <Loader color="blue" />
            <Text c="dimmed">Quick Start를 준비하는 중입니다.</Text>
          </Stack>
        </Container>
      </GuideFrame>
    );
  }

  return (
    <GuideFrame>
      <Container size="xl" py={56}>
        <Stack gap="xl">
          <Paper
            p={{ base: "xl", md: 40 }}
            radius={36}
            shadow="sm"
            withBorder
            style={{
              background:
                "radial-gradient(circle at top right, rgba(59,130,246,0.12), transparent 28%), radial-gradient(circle at top left, rgba(20,184,166,0.10), transparent 24%), linear-gradient(180deg, rgba(255,255,255,0.98), rgba(248,250,252,0.98))",
            }}
          >
            <Stack gap="lg">
              <Badge color="blue" radius="xl" variant="light" w="fit-content">
                빠른 시작
              </Badge>
              <Title order={1} style={{ fontSize: "clamp(2rem, 5vw, 3.2rem)", lineHeight: 1.08 }}>
                첫 이벤트를 보내고,
                <br />
                개요 화면에서 바로 확인합니다.
              </Title>
              <Text c="dimmed" maw={760} size="lg">
                빠른 시작용 조직을 만들고 API key로 이벤트를 한 번 보낸 뒤, 개요 화면에서 반영 여부를 확인합니다.
              </Text>
              <Group gap="xs">
                {missionSteps.map((step) => (
                  <Badge color="gray" key={step} radius="xl" size="lg" variant="light">
                    {step}
                  </Badge>
                ))}
              </Group>
              <Group>
                <Button component={Link} href="/guide" radius="xl" variant="light">
                  소개 보기
                </Button>
                {quickStartOrganization ? (
                  <Button
                    component={Link}
                    href={`/dashboard/${quickStartOrganization.organizationId}`}
                    radius="xl"
                    rightSection={<IconArrowRight size={16} />}
                  >
                    생성한 조직 열기
                  </Button>
                ) : null}
              </Group>
            </Stack>
          </Paper>

          {errorMessage ? (
            <Alert color="red" icon={<IconInfoCircle size={16} />} radius="24px" variant="light">
              {errorMessage}
            </Alert>
          ) : null}

          <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
            <Paper radius="28px" p={{ base: "xl", md: 28 }} withBorder>
              <Stack gap="lg">
                <Stack gap={4}>
                  <Text fw={700} size="lg">
                    지금 할 일
                  </Text>
                  <Text c="dimmed" size="sm">
                    지금 필요한 액션만 순서대로 진행하면 됩니다.
                  </Text>
                </Stack>

                {!isLoggedIn ? (
                  <Stack gap="sm">
                    <Badge color="gray" radius="xl" variant="light" w="fit-content">
                      1단계
                    </Badge>
                    <Text size="sm">먼저 로그인 상태가 필요합니다.</Text>
                    <Group>
                      <Button component={Link} href="/login" radius="xl">
                        로그인
                      </Button>
                      <Button component={Link} href="/signup" radius="xl" variant="light">
                        계정 만들기
                      </Button>
                    </Group>
                  </Stack>
                ) : !hasOrganization ? (
                  <Stack gap="sm">
                    <Badge color="blue" radius="xl" variant="light" w="fit-content">
                      1단계
                    </Badge>
                    <Text size="sm">
                      빠른 시작 전용 조직을 하나 만듭니다.
                    </Text>
                    <Button
                      leftSection={<IconBuildingPlus size={16} />}
                      loading={isCreatingOrganization}
                      onClick={() => void handleCreateQuickStartOrganization()}
                      radius="xl"
                      w="fit-content"
                    >
                      빠른 시작용 테스트 조직 만들기
                    </Button>
                    <Text c="dimmed" size="sm">
                      현재 접근 가능한 조직 {membershipCount}개
                    </Text>
                  </Stack>
                ) : !hasSentEvent ? (
                  <Stack gap="sm">
                    <Badge color="teal" radius="xl" variant="light" w="fit-content">
                      2단계
                    </Badge>
                    <Paper bg="blue.0" p="md" radius="20px" withBorder>
                      <Stack gap={6}>
                        <Text fw={700} size="sm">
                          준비된 조직
                        </Text>
                        <Text size="sm">{quickStartOrganization?.organizationName}</Text>
                        <Group gap="xs">
                          <Badge color="teal" radius="xl" variant="light">
                            {quickStartOrganization?.apiKeyPrefix}
                          </Badge>
                          <CopyButton value={quickStartOrganization?.apiKey ?? ""} timeout={1500}>
                            {({ copied, copy }) => (
                              <Button
                                color={copied ? "teal" : "gray"}
                                leftSection={copied ? <IconCheck size={14} /> : <IconCopy size={14} />}
                                onClick={copy}
                                radius="xl"
                                size="xs"
                                variant="subtle"
                              >
                                {copied ? "복사됨" : "API key 복사"}
                              </Button>
                            )}
                          </CopyButton>
                        </Group>
                        <Text c="dimmed" size="sm">
                          아래 입력칸에 API key를 붙여 넣고 이벤트를 보냅니다.
                        </Text>
                      </Stack>
                    </Paper>

                    <TextInput
                      label="API key 입력"
                      onChange={(event) => setApiKeyInput(event.currentTarget.value)}
                      placeholder={quickStartOrganization?.apiKey ?? "ck_live_xxx"}
                      radius="xl"
                      size="md"
                      value={apiKeyInput}
                    />

                    <Button
                      leftSection={<IconPlayerPlay size={16} />}
                      loading={isSendingEvent}
                      onClick={() => void handleSendSampleEvent()}
                      radius="xl"
                      w="fit-content"
                    >
                      샘플 이벤트 보내기
                    </Button>
                    <Text c="dimmed" size="sm">
                      `page_view` 이벤트 1건을 `/quick-start` 경로로 전송합니다.
                    </Text>
                  </Stack>
                ) : (
                  <Stack gap="sm">
                    <Badge color="orange" radius="xl" variant="light" w="fit-content">
                      3단계
                    </Badge>
                    <Text size="sm">
                      이벤트를 보냈습니다. 이제 개요 값을 확인합니다.
                    </Text>
                    <Group>
                      <Button
                        leftSection={<IconRefresh size={16} />}
                        loading={isRefreshingOverview}
                        onClick={() => void handleCheckOverview()}
                        radius="xl"
                      >
                        개요 확인
                      </Button>
                      <Button
                        component={Link}
                        href={`/dashboard/${quickStartOrganization?.organizationId ?? ""}`}
                        rel="noreferrer"
                        target="_blank"
                        radius="xl"
                        variant="light"
                      >
                        대시보드 열기
                      </Button>
                    </Group>
                  </Stack>
                )}
              </Stack>
            </Paper>

            <Paper radius="28px" p={{ base: "xl", md: 28 }} withBorder>
              <Stack gap="lg">
                <Stack gap={4}>
                  <Text fw={700} size="lg">
                    진행 상태
                  </Text>
                  <Text c="dimmed" size="sm">
                    필요한 조건이 준비되면 완료 상태로 바뀝니다.
                  </Text>
                </Stack>

                <Stack gap="sm">
                  <MissionStatus
                    description={isLoggedIn ? "로그인되어 있습니다." : "로그인 후 시작할 수 있습니다."}
                    done={isLoggedIn}
                    label="로그인 상태"
                  />
                  <MissionStatus
                    description={
                      hasOrganization
                        ? `${quickStartOrganization?.organizationName} 조직이 준비되었습니다.`
                        : "빠른 시작용 조직이 아직 없습니다."
                    }
                    done={hasOrganization}
                    label="조직 준비"
                  />
                  <MissionStatus
                    description={
                      hasApiKey
                        ? `${quickStartOrganization?.apiKeyPrefix} 키를 사용할 수 있습니다.`
                        : "조직을 만들면 API key를 확인할 수 있습니다."
                    }
                    done={hasApiKey}
                    label="API key 준비"
                  />
                  <MissionStatus
                    description={
                      hasSentEvent
                        ? "샘플 이벤트를 전송했습니다."
                        : "샘플 이벤트를 아직 보내지 않았습니다."
                    }
                    done={hasSentEvent}
                    label="이벤트 전송"
                  />
                  <MissionStatus
                    description={
                      !hasCheckedOverview
                        ? "아직 개요 값을 확인하지 않았습니다."
                        : isOverviewConfirmed
                          ? `총 이벤트 ${overview?.totalEvents}건이 보입니다.`
                          : "개요 값을 확인했지만 아직 이벤트가 보이지 않습니다."
                    }
                    done={hasCheckedOverview && isOverviewConfirmed}
                    label="개요 확인"
                  />
                </Stack>
              </Stack>
            </Paper>
          </SimpleGrid>

          <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
            <Paper radius="28px" p={{ base: "xl", md: 28 }} withBorder>
              <Stack gap="lg">
                <Stack gap={4}>
                  <Text fw={700} size="lg">
                    결과 확인
                  </Text>
                  <Text c="dimmed" size="sm">
                    개요 화면에서 가장 먼저 확인할 값만 보여줍니다.
                  </Text>
                </Stack>

                <Paper
                  bg={isOverviewConfirmed ? "teal.0" : "gray.0"}
                  p="md"
                  radius="20px"
                  withBorder
                  style={{
                    borderColor: isOverviewConfirmed
                      ? "var(--mantine-color-teal-3)"
                      : undefined,
                  }}
                >
                  <Group align="center" gap="sm" wrap="nowrap">
                    <ThemeIcon
                      color={isOverviewConfirmed ? "teal" : "gray"}
                      radius="xl"
                      size={34}
                      variant="light"
                    >
                      <IconCheck size={18} />
                    </ThemeIcon>
                    <Stack gap={2}>
                      <Text fw={700} size="sm">
                        {isOverviewConfirmed ? "Quick Start 완료" : "개요 값 대기 중"}
                      </Text>
                      <Text c="dimmed" size="sm">
                        {isOverviewConfirmed
                          ? `총 이벤트 ${overview?.totalEvents}건이 반영되었습니다.`
                          : "샘플 이벤트를 보낸 뒤 개요 확인 버튼을 눌러 결과를 확인합니다."}
                      </Text>
                    </Stack>
                  </Group>
                </Paper>

                <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="md">
                  <Paper bg="blue.0" p="md" radius="20px" withBorder>
                    <Stack gap={4}>
                      <Text c="dimmed" size="xs" tt="uppercase" fw={700}>
                        총 이벤트
                      </Text>
                      <Text fw={800} size="xl">
                        {hasCheckedOverview ? overview?.totalEvents ?? "-" : "확인 전"}
                      </Text>
                    </Stack>
                  </Paper>
                  <Paper bg="teal.0" p="md" radius="20px" withBorder>
                    <Stack gap={4}>
                      <Text c="dimmed" size="xs" tt="uppercase" fw={700}>
                        고유 사용자
                      </Text>
                      <Text fw={800} size="xl">
                        {hasCheckedOverview ? overview?.uniqueUsers ?? "-" : "확인 전"}
                      </Text>
                    </Stack>
                  </Paper>
                </SimpleGrid>

                <Paper bg="gray.0" p="md" radius="20px" withBorder>
                  <Stack gap={6}>
                    <Text fw={700} size="sm">
                      Quick Start 완료 기준
                    </Text>
                    <Text c="dimmed" size="sm">
                      샘플 이벤트를 보낸 뒤 개요 값에서 총 이벤트가 1건 이상 보이면 충분합니다.
                    </Text>
                  </Stack>
                </Paper>
              </Stack>
            </Paper>

            <Paper radius="28px" p={{ base: "xl", md: 28 }} withBorder>
              <Accordion chevronPosition="right" radius="24px" variant="contained">
                <Accordion.Item value="curl">
                  <Accordion.Control icon={<IconCode size={18} />}>
                    직접 API 호출해보기
                  </Accordion.Control>
                  <Accordion.Panel>
                    <Stack gap="sm">
                      <Text c="dimmed" size="sm">
                        직접 호출해보고 싶다면 아래 예시를 그대로 사용할 수 있습니다.
                      </Text>
                      <Code block>{eventExample}</Code>
                    </Stack>
                  </Accordion.Panel>
                </Accordion.Item>
              </Accordion>
            </Paper>
          </SimpleGrid>
        </Stack>
      </Container>
    </GuideFrame>
  );
}

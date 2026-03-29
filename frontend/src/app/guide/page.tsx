"use client";

import { GuideFrame } from "@/components/guide/guide-frame";
import { getAccessToken } from "@/lib/session/token-store";
import {
  Badge,
  Button,
  Container,
  Grid,
  Group,
  Loader,
  Paper,
  SimpleGrid,
  Stack,
  Text,
  ThemeIcon,
  Title,
} from "@mantine/core";
import {
  IconArrowRight,
  IconChartBar,
  IconDatabase,
  IconKey,
  IconRoute2,
  IconUsersGroup,
} from "@tabler/icons-react";
import Link from "next/link";
import { useEffect, useState } from "react";

const flowSteps = [
  {
    title: "이벤트 전송",
    description: "클라이언트가 page_view, click, purchase 같은 이벤트를 API로 보냅니다.",
    color: "blue",
    icon: IconRoute2,
  },
  {
    title: "원본 저장",
    description: "이벤트는 조직 범위로 저장되고, 익명/식별 사용자 기준도 함께 유지됩니다.",
    color: "teal",
    icon: IconDatabase,
  },
  {
    title: "자동 집계",
    description: "시간 흐름과 활동 패턴을 빠르게 보도록 요약 집계가 함께 계산됩니다.",
    color: "orange",
    icon: IconChartBar,
  },
  {
    title: "결과 확인",
    description: "콘솔 화면이나 제품 API에서 바로 분석 결과를 확인할 수 있습니다.",
    color: "blue",
    icon: IconUsersGroup,
  },
] as const;

const features = [
  {
    title: "이벤트 수집 API",
    description: "간단한 API 호출만으로 원본 이벤트를 적재합니다.",
  },
  {
    title: "조직 단위 분리",
    description: "organization 기준으로 데이터와 운영 설정을 분리합니다.",
  },
  {
    title: "API Key 인증",
    description: "제품 API는 `X-API-Key` 기준으로 조직 범위를 판별합니다.",
  },
  {
    title: "분석 결과 제공",
    description: "개요, 추이, 사용자 현황, 활동량, 유지율, 퍼널 분석을 제공합니다.",
  },
] as const;

const useCases = [
  {
    title: "SaaS 사용자 행동 분석",
    description: "가입 이후 어떤 경로와 기능이 자주 쓰이는지 확인합니다.",
  },
  {
    title: "기능 사용률 측정",
    description: "이벤트 타입과 경로 기준으로 기능 도달률과 사용 패턴을 봅니다.",
  },
  {
    title: "마케팅 유입 추적",
    description: "유입 이후 어떤 이벤트가 이어지고 어디서 이탈하는지 파악합니다.",
  },
] as const;

const guideSequence = [
  {
    title: "빠른 시작",
    description: "첫 이벤트를 보내고 개요 값이 반영되는지 바로 확인합니다.",
  },
  {
    title: "API Key",
    description: "제품 API에서 쓰는 인증 방식과 재발급 흐름을 확인합니다.",
  },
  {
    title: "이벤트 전송 / 집계 API",
    description: "실제 요청 형식과 응답 필드를 자세히 봅니다.",
  },
] as const;

export default function GuideOverviewPage() {
  const [isCheckingSession, setIsCheckingSession] = useState(true);
  const [hasAccessToken, setHasAccessToken] = useState(false);

  useEffect(() => {
    setHasAccessToken(Boolean(getAccessToken()));
    setIsCheckingSession(false);
  }, []);

  if (isCheckingSession) {
    return (
      <GuideFrame>
        <Container size="sm" py={120}>
          <Stack align="center" gap="md">
            <Loader color="blue" />
            <Text c="dimmed">사용 가이드를 준비하는 중입니다.</Text>
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
            <Grid gutter="xl">
              <Grid.Col span={{ base: 12, md: 7 }}>
                <Stack gap="lg">
                  <Badge color="blue" radius="xl" variant="light" w="fit-content">
                    소개
                  </Badge>
                  <Title order={1} style={{ fontSize: "clamp(2rem, 5vw, 3.4rem)", lineHeight: 1.08 }}>
                    이벤트 기반 사용자 행동 분석 플랫폼
                  </Title>
                  <Text c="dimmed" maw={700} size="lg">
                    간단한 API 호출만으로 사용자 행동 데이터를 수집하고, 조직 단위로 분석 결과를 확인할 수 있습니다.
                  </Text>
                  <Group>
                    {hasAccessToken ? (
                      <Button
                        component={Link}
                        href="/organizations"
                        radius="xl"
                        rightSection={<IconArrowRight size={16} />}
                        size="lg"
                      >
                        조직 만들고 시작하기
                      </Button>
                    ) : (
                      <>
                        <Button
                          component={Link}
                          href="/login"
                          radius="xl"
                          rightSection={<IconArrowRight size={16} />}
                          size="lg"
                        >
                          로그인
                        </Button>
                        <Button component={Link} href="/signup" radius="xl" size="lg" variant="light">
                          계정 만들기
                        </Button>
                      </>
                    )}
                  </Group>
                </Stack>
              </Grid.Col>

              <Grid.Col span={{ base: 12, md: 5 }}>
                <Paper
                  p="xl"
                  radius="32px"
                  withBorder
                  style={{
                    background:
                      "linear-gradient(160deg, rgba(15,23,42,0.96), rgba(30,41,59,0.94))",
                    color: "white",
                  }}
                >
                  <Stack gap="md">
                    <Text c="blue.1" fw={700} size="sm">
                      한 줄로 보면
                    </Text>
                    <Text fw={800} size="xl" style={{ lineHeight: 1.15 }}>
                      이벤트를 넣고
                      <br />
                      바로 분석 결과를 봅니다
                    </Text>
                    <Text c="gray.3" size="sm">
                      제품 API는 `X-API-Key`, 관리자 콘솔은 JWT를 사용합니다. 적재와 분석 확인이 한 서비스 안에서 연결됩니다.
                    </Text>
                  </Stack>
                </Paper>
              </Grid.Col>
            </Grid>
          </Paper>

          <SimpleGrid cols={{ base: 1, md: 2 }} spacing="lg">
            <Paper radius="28px" p="xl" withBorder>
              <Stack gap="sm">
                <Badge color="gray" radius="xl" variant="light" w="fit-content">
                  문제
                </Badge>
                <Title order={3}>로그는 쌓이는데 분석이 어렵습니다</Title>
                <Text c="dimmed" size="sm">
                  사용자 행동이 어떤 경로를 타는지, 어떤 이벤트가 자주 발생하는지, 어디에서 이탈하는지 바로 보기 어렵습니다.
                </Text>
              </Stack>
            </Paper>
            <Paper radius="28px" p="xl" withBorder>
              <Stack gap="sm">
                <Badge color="blue" radius="xl" variant="light" w="fit-content">
                  해결
                </Badge>
                <Title order={3}>이벤트를 수집하고 자동 집계합니다</Title>
                <Text c="dimmed" size="sm">
                  API로 이벤트를 보내면 조직 범위로 저장되고, 개요/추이/활동량/유지율/퍼널 분석 화면과 제품 API로 결과를 바로 확인할 수 있습니다.
                </Text>
              </Stack>
            </Paper>
          </SimpleGrid>

          <Paper radius="32px" p={{ base: "xl", md: 32 }} withBorder>
            <Stack gap="lg">
              <Stack gap={4}>
                <Badge color="blue" radius="xl" variant="light" w="fit-content">
                  시작 순서
                </Badge>
                <Title order={2}>이 순서대로 보면 됩니다</Title>
                <Text c="dimmed" size="sm">
                  소개에서 흐름을 이해한 뒤, 빠른 시작으로 첫 성공을 만들고 상세 가이드에서 요청과 응답 형식을 확인합니다.
                </Text>
              </Stack>
              <SimpleGrid cols={{ base: 1, md: 3 }} spacing="lg">
                {guideSequence.map((item, index) => (
                  <Paper key={item.title} radius="24px" p="lg" withBorder bg="gray.0">
                    <Stack gap={6}>
                      <Badge color="gray" radius="xl" variant="light" w="fit-content">
                        {index + 1}
                      </Badge>
                      <Text fw={700}>{item.title}</Text>
                      <Text c="dimmed" size="sm">
                        {item.description}
                      </Text>
                    </Stack>
                  </Paper>
                ))}
              </SimpleGrid>
            </Stack>
          </Paper>

          <Paper radius="32px" p={{ base: "xl", md: 32 }} withBorder>
            <Stack gap="xl">
              <Stack gap={4}>
                <Badge color="teal" radius="xl" variant="light" w="fit-content">
                  동작 흐름
                </Badge>
                <Title order={2}>이렇게 동작합니다</Title>
                <Text c="dimmed" size="sm">
                  복잡한 설정 없이, 이벤트 적재부터 분석 확인까지 한 흐름으로 이어집니다.
                </Text>
              </Stack>

              <SimpleGrid cols={{ base: 1, md: 2, lg: 4 }} spacing="lg">
                {flowSteps.map((step, index) => {
                  const Icon = step.icon;

                  return (
                    <Paper key={step.title} radius="24px" p="lg" withBorder bg="gray.0">
                      <Stack gap="sm">
                        <Group justify="space-between" align="center">
                          <ThemeIcon color={step.color} radius="xl" size={42} variant="light">
                            <Icon size={20} />
                          </ThemeIcon>
                          <Badge color="gray" radius="xl" variant="light">
                            {index + 1}
                          </Badge>
                        </Group>
                        <Text fw={700}>{step.title}</Text>
                        <Text c="dimmed" size="sm">
                          {step.description}
                        </Text>
                      </Stack>
                    </Paper>
                  );
                })}
              </SimpleGrid>
            </Stack>
          </Paper>

          <Paper radius="32px" p={{ base: "xl", md: 32 }} withBorder>
            <Stack gap="xl">
              <Stack gap={4}>
                <Badge color="orange" radius="xl" variant="light" w="fit-content">
                  핵심 기능
                </Badge>
                <Title order={2}>핵심 기능</Title>
              </Stack>
              <SimpleGrid cols={{ base: 1, md: 2 }} spacing="lg">
                {features.map((feature) => (
                  <Paper key={feature.title} radius="24px" p="lg" withBorder bg="gray.0">
                    <Stack gap={6}>
                      <Text fw={700}>{feature.title}</Text>
                      <Text c="dimmed" size="sm">
                        {feature.description}
                      </Text>
                    </Stack>
                  </Paper>
                ))}
              </SimpleGrid>
            </Stack>
          </Paper>

          <Paper radius="32px" p={{ base: "xl", md: 32 }} withBorder>
            <Stack gap="xl">
              <Stack gap={4}>
                <Badge color="gray" radius="xl" variant="light" w="fit-content">
                  활용 예시
                </Badge>
                <Title order={2}>이럴 때 쓸 수 있습니다</Title>
              </Stack>
              <SimpleGrid cols={{ base: 1, md: 3 }} spacing="lg">
                {useCases.map((useCase) => (
                  <Paper key={useCase.title} radius="24px" p="lg" withBorder bg="gray.0">
                    <Stack gap={6}>
                      <Text fw={700}>{useCase.title}</Text>
                      <Text c="dimmed" size="sm">
                        {useCase.description}
                      </Text>
                    </Stack>
                  </Paper>
                ))}
              </SimpleGrid>
            </Stack>
          </Paper>

          <Paper radius="32px" p={{ base: "xl", md: 32 }} withBorder>
            <Group justify="space-between" align="center" gap="lg">
              <Stack gap={4}>
                <Text fw={700} size="lg">
                  다음 단계
                </Text>
                <Text c="dimmed" size="sm">
                  실제로 조직을 만들고 API key를 확인한 뒤, 이벤트 1건을 보내보면 가장 빠르게 감이 잡힙니다.
                </Text>
              </Stack>
              <Button
                component={Link}
                href="/quick-start"
                radius="xl"
                rightSection={<IconArrowRight size={16} />}
              >
                {hasAccessToken ? "Quick Start로 이동" : "Quick Start 보기"}
              </Button>
            </Group>
          </Paper>
        </Stack>
      </Container>
    </GuideFrame>
  );
}

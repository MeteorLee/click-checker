"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { getAccessToken } from "@/lib/session/token-store";
import {
  Badge,
  Box,
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
  IconChartBarPopular,
  IconFilterBolt,
  IconKey,
  IconRoute2,
  IconUsersGroup,
} from "@tabler/icons-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

const productHighlights = [
  {
    title: "개요부터 상세 분석까지",
    description:
      "개요를 시작으로 경로, 이벤트, 추이, 사용자, 활동량, 유지율, 퍼널 분석까지 같은 흐름으로 이어집니다.",
    icon: IconChartBarPopular,
    color: "blue",
  },
  {
    title: "수집 규칙과 API Key 운영",
    description:
      "경로 규칙, 이벤트 규칙, API Key 재발급을 관리자 콘솔 안에서 함께 관리합니다.",
    icon: IconKey,
    color: "teal",
  },
  {
    title: "조직 단위 운영 관리",
    description:
      "조직 생성, 멤버십 기반 삭제, 멤버 권한 관리까지 조직 단위로 분리해 다룹니다.",
    icon: IconUsersGroup,
    color: "orange",
  },
] as const;

const featurePills = [
  "JWT 관리자 콘솔",
  "조직별 분석",
  "유지율 · 퍼널",
  "경로 · 이벤트 규칙",
] as const;

export default function Home() {
  const router = useRouter();
  const [isCheckingSession, setIsCheckingSession] = useState(true);

  useEffect(() => {
    const accessToken = getAccessToken();

    if (accessToken) {
      router.replace("/organizations");
      return;
    }

    setIsCheckingSession(false);
  }, [router]);

  if (isCheckingSession) {
    return (
      <ConsoleFrame>
        <Container size="sm" py={120}>
          <Stack align="center" gap="md">
            <Loader color="blue" />
            <Text c="dimmed">콘솔 진입 상태를 확인하는 중입니다.</Text>
          </Stack>
        </Container>
      </ConsoleFrame>
    );
  }

  return (
    <ConsoleFrame>
      <Container size="xl" py={56}>
        <Paper
          p={{ base: "xl", md: 40 }}
          radius={36}
          shadow="sm"
          withBorder
          style={{
            overflow: "hidden",
            background:
              "radial-gradient(circle at top right, rgba(59,130,246,0.12), transparent 30%), radial-gradient(circle at top left, rgba(20,184,166,0.12), transparent 28%), linear-gradient(180deg, rgba(255,255,255,0.98), rgba(248,250,252,0.98))",
          }}
        >
          <Grid align="stretch" gutter="xl">
            <Grid.Col span={{ base: 12, md: 7 }}>
              <Stack gap="xl" h="100%" justify="space-between">
                <Stack gap="lg">
                  <Stack gap="sm">
                    <Badge color="blue" radius="xl" variant="light" w="fit-content">
                      Click Checker
                    </Badge>
                    <Title order={1} style={{ fontSize: "clamp(2rem, 5vw, 4rem)", lineHeight: 1.05 }}>
                      조직별 이벤트 수집과
                      <br />
                      제품 분석을 한 콘솔에서 관리합니다.
                    </Title>
                    <Text c="dimmed" maw={680} size="lg">
                      로그인 후 조직을 선택하거나 새로 만들고, 개요부터 상세 분석, 규칙 관리,
                      멤버 운영까지 이어서 확인할 수 있는 관리자 콘솔입니다.
                    </Text>
                  </Stack>

                  <Group gap="sm">
                    {featurePills.map((pill) => (
                      <Badge key={pill} color="gray" radius="xl" size="lg" variant="dot">
                        {pill}
                      </Badge>
                    ))}
                  </Group>

                  <Group>
                    <Button
                      component={Link}
                      href="/login"
                      radius="xl"
                      rightSection={<IconArrowRight size={16} />}
                      size="lg"
                    >
                      콘솔 로그인
                    </Button>
                    <Button component={Link} href="/signup" radius="xl" size="lg" variant="light">
                      계정 만들기
                    </Button>
                    <Button component={Link} href="/guide" radius="xl" size="lg" variant="subtle">
                      서비스 소개
                    </Button>
                  </Group>
                </Stack>

                <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="md">
                  <Paper bg="white" p="lg" radius="24px" withBorder>
                    <Stack gap={2}>
                      <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                        Analytics
                      </Text>
                      <Text fw={800} size="xl">
                        7+
                      </Text>
                      <Text c="dimmed" size="sm">
                        개요 화면에서 상세 분석으로 자연스럽게 확장됩니다.
                      </Text>
                    </Stack>
                  </Paper>
                  <Paper bg="white" p="lg" radius="24px" withBorder>
                    <Stack gap={2}>
                      <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                        Operations
                      </Text>
                      <Text fw={800} size="xl">
                        4
                      </Text>
                      <Text c="dimmed" size="sm">
                        API Key, 경로 규칙, 이벤트 규칙, 멤버 관리가 함께 제공됩니다.
                      </Text>
                    </Stack>
                  </Paper>
                  <Paper bg="white" p="lg" radius="24px" withBorder>
                    <Stack gap={2}>
                      <Text c="dimmed" fw={700} size="xs" tt="uppercase">
                        Scope
                      </Text>
                      <Text fw={800} size="xl">
                        JWT
                      </Text>
                      <Text c="dimmed" size="sm">
                        브라우저는 JWT만 사용하고 조직 멤버십으로 접근 범위를 검증합니다.
                      </Text>
                    </Stack>
                  </Paper>
                </SimpleGrid>
              </Stack>
            </Grid.Col>

            <Grid.Col span={{ base: 12, md: 5 }}>
              <Stack gap="lg" h="100%">
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
                  <Stack gap="lg">
                    <Group justify="space-between" align="flex-start">
                      <Stack gap={4}>
                        <Text c="blue.1" fw={700} size="sm">
                          Console Preview
                        </Text>
                        <Text fw={800} size="xl">
                          분석과 운영이 한 흐름으로 연결됩니다
                        </Text>
                      </Stack>
                      <ThemeIcon color="blue" radius="xl" size={44} variant="light">
                        <IconRoute2 size={20} />
                      </ThemeIcon>
                    </Group>

                    <Stack gap="sm">
                      <Box
                        style={{
                          borderRadius: 20,
                          padding: 16,
                          background: "rgba(255,255,255,0.06)",
                          border: "1px solid rgba(255,255,255,0.08)",
                        }}
                      >
                        <Text fw={700} size="sm">
                          1. Organization 선택 / 생성
                        </Text>
                        <Text c="gray.3" size="sm">
                          organization을 고르고 API key를 확인한 뒤 overview로 들어갑니다.
                        </Text>
                      </Box>
                      <Box
                        style={{
                          borderRadius: 20,
                          padding: 16,
                          background: "rgba(255,255,255,0.06)",
                          border: "1px solid rgba(255,255,255,0.08)",
                        }}
                      >
                        <Text fw={700} size="sm">
                          2. Overview와 상세 분석
                        </Text>
                        <Text c="gray.3" size="sm">
                          routes, event types, trends, users, activity, retention, funnel로 자연스럽게 이어집니다.
                        </Text>
                      </Box>
                      <Box
                        style={{
                          borderRadius: 20,
                          padding: 16,
                          background: "rgba(255,255,255,0.06)",
                          border: "1px solid rgba(255,255,255,0.08)",
                        }}
                      >
                        <Text fw={700} size="sm">
                          3. 규칙과 멤버 운영
                        </Text>
                        <Text c="gray.3" size="sm">
                          route 규칙, event type 규칙, API key, membership을 같은 콘솔에서 관리합니다.
                        </Text>
                      </Box>
                    </Stack>

                    <Group>
                      <ThemeIcon color="teal" radius="xl" size={40} variant="light">
                        <IconFilterBolt size={18} />
                      </ThemeIcon>
                      <Text c="gray.2" size="sm">
                        지금 화면은 랜딩페이지보다 관리자 콘솔 입구에 가깝게 설계되어 있습니다.
                      </Text>
                    </Group>
                    <Button
                      component={Link}
                      href="/guide"
                      radius="xl"
                      rightSection={<IconArrowRight size={16} />}
                      variant="white"
                    >
                      서비스 소개 보기
                    </Button>
                  </Stack>
                </Paper>
              </Stack>
            </Grid.Col>
          </Grid>
        </Paper>
      </Container>
    </ConsoleFrame>
  );
}

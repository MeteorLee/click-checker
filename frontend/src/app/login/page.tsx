"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { LoginForm } from "@/components/auth/login-form";
import { getAccessToken } from "@/lib/session/token-store";
import {
  Button,
  Badge,
  Container,
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
  IconArrowLeft,
  IconActivityHeartbeat,
  IconBuildingSkyscraper,
  IconShieldLock,
} from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

export default function LoginPage() {
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
            <Text c="dimmed">로그인 상태를 확인하는 중입니다.</Text>
          </Stack>
        </Container>
      </ConsoleFrame>
    );
  }

  return (
    <ConsoleFrame>
      <Container size="xl" py={72}>
        <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="xl">
          <div>
            <Paper radius="32px" p={40} shadow="sm" withBorder className="console-hero">
              <Stack gap="xl">
                <Stack gap="md">
                  <Badge color="blue" radius="xl" size="lg" variant="light" w="fit-content">
                    Click Checker
                  </Badge>
                  <Title order={1} fz={{ base: 36, md: 48 }} maw={520}>
                    로그인 후 바로 조직별 분석과 운영 흐름으로 들어갑니다.
                  </Title>
                  <Text c="dimmed" maw={520} size="lg">
                    로그인 후 작업할 조직을 고르면 개요 화면에서 핵심 지표를 먼저 보고, 필요한 상세 분석과 운영 설정으로 바로 이동할 수 있습니다.
                  </Text>
                </Stack>

                <Group gap="sm">
                  <Badge color="gray" radius="xl" size="lg" variant="dot">
                    개요부터 상세 분석까지
                  </Badge>
                  <Badge color="gray" radius="xl" size="lg" variant="dot">
                    조직별 접근 제어
                  </Badge>
                </Group>

                <Stack gap="md">
                  <Group align="flex-start" wrap="nowrap">
                    <ThemeIcon color="blue" radius="xl" size={44} variant="light">
                      <IconShieldLock size={20} />
                    </ThemeIcon>
                    <div>
                      <Text fw={700}>안전한 관리자 진입</Text>
                      <Text c="dimmed" size="sm">
                        관리자 계정으로 로그인하고, 조직 접근 권한은 서버에서 멤버십 기준으로 다시 확인합니다.
                      </Text>
                    </div>
                  </Group>

                  <Group align="flex-start" wrap="nowrap">
                    <ThemeIcon color="teal" radius="xl" size={44} variant="light">
                      <IconBuildingSkyscraper size={20} />
                    </ThemeIcon>
                    <div>
                      <Text fw={700}>조직 중심 콘솔</Text>
                      <Text c="dimmed" size="sm">
                        여러 조직 중 작업할 대상을 고르고, 선택한 조직 기준으로 개요와 운영 상태를 확인합니다.
                      </Text>
                    </div>
                  </Group>

                  <Group align="flex-start" wrap="nowrap">
                    <ThemeIcon color="orange" radius="xl" size={44} variant="light">
                      <IconActivityHeartbeat size={20} />
                    </ThemeIcon>
                    <div>
                      <Text fw={700}>핵심 지표 우선</Text>
                      <Text c="dimmed" size="sm">
                        총 이벤트, 고유 사용자, 상위 경로와 이벤트 타입부터 빠르게 확인하고,
                        이후 세부 집계 화면으로 확장할 수 있습니다.
                      </Text>
                    </div>
                  </Group>
                </Stack>
              </Stack>
            </Paper>
          </div>

          <div>
            <Container size="xs" px={0}>
              <Stack gap="md">
                <Group justify="flex-start">
                  <Button
                    component="a"
                    href="/"
                    leftSection={<IconArrowLeft size={14} />}
                    radius="xl"
                    size="compact-sm"
                    variant="subtle"
                  >
                    홈으로
                  </Button>
                </Group>
                <LoginForm />
              </Stack>
            </Container>
          </div>
        </SimpleGrid>
      </Container>
    </ConsoleFrame>
  );
}

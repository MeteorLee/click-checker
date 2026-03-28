"use client";

import { ConsoleFrame } from "@/components/common/console-frame";
import { SignupForm } from "@/components/auth/signup-form";
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
  IconBuildingSkyscraper,
  IconKey,
  IconUserPlus,
} from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

export default function SignupPage() {
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
                    새 관리자 계정을 만들고 바로 조직 작업을 시작합니다.
                  </Title>
                  <Text c="dimmed" maw={520} size="lg">
                    계정 생성이 완료되면 바로 로그인된 상태가 되고, 조직을 만들거나
                    기존 조직을 선택해 개요 화면으로 바로 들어갈 수 있습니다.
                  </Text>
                </Stack>

                <Group gap="sm">
                  <Badge color="gray" radius="xl" size="lg" variant="dot">
                    계정 생성 후 바로 진입
                  </Badge>
                  <Badge color="gray" radius="xl" size="lg" variant="dot">
                    조직별 분석 시작
                  </Badge>
                </Group>

                <Stack gap="md">
                  <Group align="flex-start" wrap="nowrap">
                    <ThemeIcon color="blue" radius="xl" size={44} variant="light">
                      <IconUserPlus size={20} />
                    </ThemeIcon>
                    <div>
                      <Text fw={700}>생성 즉시 콘솔 진입</Text>
                      <Text c="dimmed" size="sm">
                        계정이 만들어지면 별도 로그인 없이 바로 organization 선택 화면으로
                        이동합니다.
                      </Text>
                    </div>
                  </Group>

                  <Group align="flex-start" wrap="nowrap">
                    <ThemeIcon color="teal" radius="xl" size={44} variant="light">
                      <IconBuildingSkyscraper size={20} />
                    </ThemeIcon>
                    <div>
                      <Text fw={700}>조직 단위 운영 흐름</Text>
                      <Text c="dimmed" size="sm">
                        계정 생성 뒤에는 조직을 만들거나 선택하고, 각 조직의 개요와
                        API key 상태를 확인합니다.
                      </Text>
                    </div>
                  </Group>

                  <Group align="flex-start" wrap="nowrap">
                    <ThemeIcon color="orange" radius="xl" size={44} variant="light">
                      <IconKey size={20} />
                    </ThemeIcon>
                    <div>
                      <Text fw={700}>빠른 로컬 시작</Text>
                      <Text c="dimmed" size="sm">
                        로컬 환경에서도 계정을 바로 만들고, 첫 organization 생성부터 overview
                        조회까지 한 흐름으로 확인할 수 있습니다.
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
                <SignupForm />
              </Stack>
            </Container>
          </div>
        </SimpleGrid>
      </Container>
    </ConsoleFrame>
  );
}

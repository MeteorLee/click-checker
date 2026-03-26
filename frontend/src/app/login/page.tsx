import { ConsoleFrame } from "@/components/common/console-frame";
import { LoginForm } from "@/components/auth/login-form";
import {
  Badge,
  Container,
  Group,
  Paper,
  SimpleGrid,
  Stack,
  Text,
  ThemeIcon,
  Title,
} from "@mantine/core";
import {
  IconActivityHeartbeat,
  IconBuildingSkyscraper,
  IconShieldLock,
} from "@tabler/icons-react";

export default function LoginPage() {
  return (
    <ConsoleFrame>
      <Container size="xl" py={72}>
        <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="xl">
          <div>
            <Paper radius="32px" p={40} shadow="sm" withBorder className="console-hero">
              <Stack gap="xl">
                <Stack gap="md">
                  <Badge color="blue" radius="xl" size="lg" variant="light">
                    Click Checker
                  </Badge>
                  <Title order={1} fz={{ base: 36, md: 48 }} maw={520}>
                    운영 콘솔에서 조직별 이벤트 흐름을 바로 확인합니다.
                  </Title>
                  <Text c="dimmed" maw={520} size="lg">
                    로그인 후 organization을 선택하면 JWT 기반 admin overview API를
                    통해 핵심 KPI와 상위 route, event type 요약을 확인할 수 있습니다.
                  </Text>
                </Stack>

                <Stack gap="md">
                  <Group align="flex-start" wrap="nowrap">
                    <ThemeIcon color="blue" radius="xl" size={44} variant="light">
                      <IconShieldLock size={20} />
                    </ThemeIcon>
                    <div>
                      <Text fw={700}>JWT 기반 관리자 인증</Text>
                      <Text c="dimmed" size="sm">
                        브라우저는 admin token만 사용하고 organization 권한은 서버에서
                        membership으로 다시 검증합니다.
                      </Text>
                    </div>
                  </Group>

                  <Group align="flex-start" wrap="nowrap">
                    <ThemeIcon color="teal" radius="xl" size={44} variant="light">
                      <IconBuildingSkyscraper size={20} />
                    </ThemeIcon>
                    <div>
                      <Text fw={700}>멀티테넌트 organization 흐름</Text>
                      <Text c="dimmed" size="sm">
                        로그인 후 접근 가능한 organization을 고르고, 선택된 path 기준으로
                        analytics를 조회합니다.
                      </Text>
                    </div>
                  </Group>

                  <Group align="flex-start" wrap="nowrap">
                    <ThemeIcon color="orange" radius="xl" size={44} variant="light">
                      <IconActivityHeartbeat size={20} />
                    </ThemeIcon>
                    <div>
                      <Text fw={700}>Overview 중심 1차 콘솔</Text>
                      <Text c="dimmed" size="sm">
                        현재는 overview, top routes, top event types를 먼저 보여주고 이후
                        기간 선택과 세부 집계를 확장합니다.
                      </Text>
                    </div>
                  </Group>
                </Stack>
              </Stack>
            </Paper>
          </div>

          <div>
            <Container size="xs" px={0}>
              <LoginForm />
            </Container>
          </div>
        </SimpleGrid>
      </Container>
    </ConsoleFrame>
  );
}

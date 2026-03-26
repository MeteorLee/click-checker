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
                    조직별 이벤트 흐름과 수집 상태를 한 화면에서 확인합니다.
                  </Title>
                  <Text c="dimmed" maw={520} size="lg">
                    로그인 후 작업할 organization을 고르면 overview 지표, 상위 경로,
                    이벤트 타입 요약과 API key 상태를 바로 확인할 수 있습니다.
                  </Text>
                </Stack>

                <Stack gap="md">
                  <Group align="flex-start" wrap="nowrap">
                    <ThemeIcon color="blue" radius="xl" size={44} variant="light">
                      <IconShieldLock size={20} />
                    </ThemeIcon>
                    <div>
                      <Text fw={700}>안전한 관리자 진입</Text>
                      <Text c="dimmed" size="sm">
                        관리자 계정으로 로그인하고, organization 접근 권한은 서버에서
                        멤버십 기준으로 다시 확인합니다.
                      </Text>
                    </div>
                  </Group>

                  <Group align="flex-start" wrap="nowrap">
                    <ThemeIcon color="teal" radius="xl" size={44} variant="light">
                      <IconBuildingSkyscraper size={20} />
                    </ThemeIcon>
                    <div>
                      <Text fw={700}>Organization 중심 콘솔</Text>
                      <Text c="dimmed" size="sm">
                        여러 organization 중 작업할 대상을 고르고, 선택한 조직 기준으로
                        overview와 운영 상태를 확인합니다.
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
              <LoginForm />
            </Container>
          </div>
        </SimpleGrid>
      </Container>
    </ConsoleFrame>
  );
}

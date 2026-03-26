import { ConsoleFrame } from "@/components/common/console-frame";
import { SignupForm } from "@/components/auth/signup-form";
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
  IconBuildingSkyscraper,
  IconKey,
  IconUserPlus,
} from "@tabler/icons-react";

export default function SignupPage() {
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
                    새 관리자 계정을 만들고 바로 organization 작업을 시작합니다.
                  </Title>
                  <Text c="dimmed" maw={520} size="lg">
                    계정 생성이 완료되면 바로 로그인된 상태가 되고, organization을 만들거나
                    기존 조직을 선택해 overview 대시보드로 들어갈 수 있습니다.
                  </Text>
                </Stack>

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
                      <Text fw={700}>Organization 단위 운영 흐름</Text>
                      <Text c="dimmed" size="sm">
                        계정 생성 뒤에는 organization을 만들거나 선택하고, 각 조직의 overview와
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
              <SignupForm />
            </Container>
          </div>
        </SimpleGrid>
      </Container>
    </ConsoleFrame>
  );
}

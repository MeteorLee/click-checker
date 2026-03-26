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
                    새 관리자 계정을 만들고 콘솔로 바로 진입합니다.
                  </Title>
                  <Text c="dimmed" maw={520} size="lg">
                    계정 생성이 완료되면 access token을 받아 organization 선택 화면으로 바로
                    이동합니다.
                  </Text>
                </Stack>

                <Stack gap="md">
                  <Group align="flex-start" wrap="nowrap">
                    <ThemeIcon color="blue" radius="xl" size={44} variant="light">
                      <IconUserPlus size={20} />
                    </ThemeIcon>
                    <div>
                      <Text fw={700}>즉시 로그인된 상태로 시작</Text>
                      <Text c="dimmed" size="sm">
                        signup 응답으로 받은 JWT를 저장한 뒤 별도 로그인 없이 다음 화면으로
                        이동합니다.
                      </Text>
                    </div>
                  </Group>

                  <Group align="flex-start" wrap="nowrap">
                    <ThemeIcon color="teal" radius="xl" size={44} variant="light">
                      <IconBuildingSkyscraper size={20} />
                    </ThemeIcon>
                    <div>
                      <Text fw={700}>Organization 기반 콘솔 흐름</Text>
                      <Text c="dimmed" size="sm">
                        계정 생성 뒤에는 접근 가능한 organization 목록을 기준으로 overview
                        대시보드로 진입합니다.
                      </Text>
                    </div>
                  </Group>

                  <Group align="flex-start" wrap="nowrap">
                    <ThemeIcon color="orange" radius="xl" size={44} variant="light">
                      <IconKey size={20} />
                    </ThemeIcon>
                    <div>
                      <Text fw={700}>간단한 개발용 진입 경로</Text>
                      <Text c="dimmed" size="sm">
                        지금 단계에서는 프런트에서 직접 회원가입을 열어 로컬 테스트와 데모
                        확인을 쉽게 합니다.
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

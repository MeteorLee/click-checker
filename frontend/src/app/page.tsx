import { Anchor, Badge, Button, Container, Group, Paper, Stack, Text, Title } from "@mantine/core";

export default function Home() {
  return (
    <Container size="md" py={96}>
      <Paper radius="xl" p={40} shadow="sm" withBorder>
        <Stack gap="xl">
          <Group justify="space-between" align="flex-start">
            <div>
              <Badge color="blue" variant="light" mb="md">
                Frontend Setup
              </Badge>
              <Title order={1}>Click Checker Console</Title>
              <Text c="dimmed" mt="sm">
                JWT admin overview API를 연결할 관리자 콘솔 초기 구조를 준비했습니다.
              </Text>
            </div>
          </Group>

          <Stack gap="sm">
            <Text fw={600}>다음 구현 순서</Text>
            <Text c="dimmed">1. 로그인 화면</Text>
            <Text c="dimmed">2. organization 선택 화면</Text>
            <Text c="dimmed">3. overview 대시보드 화면</Text>
          </Stack>

          <Group>
            <Button component="a" href="/login" radius="xl">
              로그인 화면 만들기
            </Button>
            <Anchor component="a" href="/organizations" c="dimmed">
              organization 선택 화면 예정
            </Anchor>
          </Group>
        </Stack>
      </Paper>
    </Container>
  );
}

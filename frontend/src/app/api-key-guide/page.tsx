"use client";

import { GuideFrame } from "@/components/guide/guide-frame";
import {
  Badge,
  Code,
  Container,
  Paper,
  SimpleGrid,
  Stack,
  Table,
  Text,
  Title,
} from "@mantine/core";

const keyUsageExamples = [
  `POST /api/events\nX-API-Key: ck_live_xxx`,
  `GET /api/v1/events/analytics/aggregates/overview\nX-API-Key: ck_live_xxx`,
  `GET /api/v1/events/analytics/activity\nX-API-Key: ck_live_xxx`,
] as const;

const errorCases = [
  {
    status: "401",
    reason: "API key가 없거나 형식이 잘못된 경우",
  },
  {
    status: "401",
    reason: "존재하지 않거나 검증에 실패한 API key를 보낸 경우",
  },
  {
    status: "403",
    reason: "비활성화된 조직 키이거나 접근할 수 없는 상태인 경우",
  },
] as const;

const keyRules = [
  "API key 하나는 하나의 조직에만 연결됩니다.",
  "제품 API 호출에는 JWT 대신 X-API-Key를 사용합니다.",
  "조직을 바꾸면 API key도 함께 바꿔야 합니다.",
] as const;

export default function ApiKeyGuidePage() {
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
                API Key
              </Badge>
              <Title order={1} style={{ fontSize: "clamp(2rem, 5vw, 3.2rem)", lineHeight: 1.08 }}>
                제품 API 인증은
                <br />
                조직별 API key로 처리합니다.
              </Title>
              <Text c="dimmed" maw={760} size="lg">
                이벤트 적재와 집계 조회는 모두 `X-API-Key`를 사용합니다. 관리자 콘솔의 JWT와는 역할이 다르고, 하나의 API key는 하나의 조직 범위에만 연결됩니다.
              </Text>
            </Stack>
          </Paper>

          <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
            <Paper radius="28px" p={{ base: "xl", md: 28 }} withBorder>
              <Stack gap="lg">
                <Badge color="teal" radius="xl" variant="light" w="fit-content">
                  기본 원칙
                </Badge>
                <Stack gap="xs">
                  {keyRules.map((rule) => (
                    <Paper key={rule} bg="gray.0" p="sm" radius="16px" withBorder>
                      <Text size="sm">{rule}</Text>
                    </Paper>
                  ))}
                </Stack>
              </Stack>
            </Paper>

            <Paper radius="28px" p={{ base: "xl", md: 28 }} withBorder>
              <Stack gap="lg">
                <Badge color="gray" radius="xl" variant="light" w="fit-content">
                  사용 헤더
                </Badge>
              <Code block>{`X-API-Key: ck_live_xxx`}</Code>
              <Text c="dimmed" size="sm">
                `Authorization: Bearer ...` 대신 위 헤더를 사용합니다. 이벤트 적재와 집계 조회 모두 같은 방식입니다.
              </Text>
            </Stack>
          </Paper>
          </SimpleGrid>

          <Paper radius="32px" p={{ base: "xl", md: 32 }} withBorder>
            <Stack gap="lg">
              <Stack gap={4}>
                <Badge color="blue" radius="xl" variant="light" w="fit-content">
                  어디에 쓰나
                </Badge>
                <Title order={2}>이런 요청에서 공통으로 사용합니다</Title>
              </Stack>

              <SimpleGrid cols={{ base: 1, md: 3 }} spacing="lg">
                {keyUsageExamples.map((example) => (
                  <Paper key={example} bg="gray.0" p="md" radius="20px" withBorder>
                    <Code block>{example}</Code>
                  </Paper>
                ))}
              </SimpleGrid>
            </Stack>
          </Paper>

          <Paper radius="32px" p={{ base: "xl", md: 32 }} withBorder>
            <Stack gap="lg">
              <Stack gap={4}>
                <Badge color="orange" radius="xl" variant="light" w="fit-content">
                  확인과 재발급
                </Badge>
                <Title order={2}>어디서 확인하나</Title>
                <Text c="dimmed" size="sm">
                  관리자 콘솔의 `API Key 관리` 화면에서 현재 키 prefix, 상태, 마지막 사용 시각을 보고 필요하면 새 키로 재발급할 수 있습니다.
                </Text>
              </Stack>
              <SimpleGrid cols={{ base: 1, md: 2 }} spacing="lg">
                <Paper bg="gray.0" p="md" radius="20px" withBorder>
                  <Stack gap={6}>
                    <Text fw={700} size="sm">
                      콘솔 경로
                    </Text>
                    <Code block>{`/dashboard/{organizationId}/api-key`}</Code>
                  </Stack>
                </Paper>
                <Paper bg="gray.0" p="md" radius="20px" withBorder>
                  <Stack gap={6}>
                    <Text fw={700} size="sm">
                      재발급 시 주의
                    </Text>
                    <Text c="dimmed" size="sm">
                      새 키를 만들면 기존 키 대신 새 값을 클라이언트 설정에 넣어야 합니다. 전체 키는 생성 시점에만 다시 확인할 수 있습니다.
                    </Text>
                  </Stack>
                </Paper>
              </SimpleGrid>
            </Stack>
          </Paper>

          <Paper radius="32px" p={{ base: "xl", md: 32 }} withBorder>
            <Stack gap="lg">
              <Stack gap={4}>
                <Badge color="red" radius="xl" variant="light" w="fit-content">
                  실패 케이스
                </Badge>
                <Title order={2}>이럴 때 요청이 실패합니다</Title>
              </Stack>

              <Table.ScrollContainer minWidth={640}>
                <Table highlightOnHover>
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th>상태 코드</Table.Th>
                      <Table.Th>의미</Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {errorCases.map((item, index) => (
                      <Table.Tr key={`${item.status}-${index}`}>
                        <Table.Td>
                          <Code>{item.status}</Code>
                        </Table.Td>
                        <Table.Td>{item.reason}</Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </Table.ScrollContainer>
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </GuideFrame>
  );
}

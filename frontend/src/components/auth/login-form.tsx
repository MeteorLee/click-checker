"use client";

import { login } from "@/lib/api/auth";
import { setSessionTokens } from "@/lib/session/token-store";
import {
  Alert,
  Button,
  Group,
  Paper,
  PasswordInput,
  Stack,
  Text,
  TextInput,
  Title,
} from "@mantine/core";
import { IconAlertCircle, IconArrowRight } from "@tabler/icons-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";

export function LoginForm() {
  const router = useRouter();
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    setErrorMessage(null);
    setIsSubmitting(true);

    try {
      const result = await login({ loginId, password });
      setSessionTokens(result.accessToken, result.refreshToken);
      router.push("/organizations");
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : "로그인 중 알 수 없는 오류가 발생했습니다.";
      setErrorMessage(message);
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Paper radius="32px" p={40} shadow="sm" withBorder className="console-panel">
      <Stack gap="xl">
        <Stack gap={8}>
          <Text fw={700} size="sm" c="blue.7" tt="uppercase">
            Admin Console
          </Text>
          <Title order={1}>로그인</Title>
          <Text c="dimmed">
            관리자 콘솔에 로그인해 organization별 overview와 API key 상태를 확인합니다.
          </Text>
        </Stack>

        {errorMessage ? (
          <Alert
            color="red"
            icon={<IconAlertCircle size={18} />}
            radius="lg"
            variant="light"
          >
            {errorMessage}
          </Alert>
        ) : null}

        <form onSubmit={handleSubmit}>
          <Stack gap="md">
            <TextInput
              autoComplete="username"
              label="Login ID"
              placeholder="admin"
              required
              size="md"
              value={loginId}
              onChange={(event) => setLoginId(event.currentTarget.value)}
            />
            <PasswordInput
              autoComplete="current-password"
              label="Password"
              placeholder="비밀번호를 입력하세요"
              required
              size="md"
              value={password}
              onChange={(event) => setPassword(event.currentTarget.value)}
            />
            <Button
              type="submit"
              size="md"
              radius="xl"
              loading={isSubmitting}
              rightSection={<IconArrowRight size={18} />}
            >
              로그인
            </Button>
            <Group justify="space-between" mt={4}>
              <Text c="dimmed" size="sm">
                아직 계정이 없나요?
              </Text>
              <Text component={Link} href="/signup" c="blue.7" fw={700} size="sm">
                회원가입으로 이동
              </Text>
            </Group>
          </Stack>
        </form>
      </Stack>
    </Paper>
  );
}

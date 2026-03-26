"use client";

import { signup } from "@/lib/api/auth";
import { setAccessToken } from "@/lib/session/token-store";
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

export function SignupForm() {
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
      const result = await signup({ loginId, password });
      setAccessToken(result.accessToken);
      router.push("/organizations");
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : "회원가입 중 알 수 없는 오류가 발생했습니다.";
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
          <Title order={1}>회원가입</Title>
          <Text c="dimmed">
            관리자 계정을 생성하고 바로 organization 선택 화면으로 이동합니다.
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

        <Alert color="blue" radius="lg" variant="light">
          loginId는 영문자로 시작하는 4~20자여야 하고, 비밀번호는 영문과 숫자를 모두 포함한
          8자 이상이어야 합니다.
        </Alert>

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
              autoComplete="new-password"
              label="Password"
              placeholder="영문과 숫자를 포함한 8자 이상"
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
              계정 만들기
            </Button>
            <Group justify="space-between" mt={4}>
              <Text c="dimmed" size="sm">
                이미 계정이 있나요?
              </Text>
              <Text component={Link} href="/login" c="blue.7" fw={700} size="sm">
                로그인으로 이동
              </Text>
            </Group>
          </Stack>
        </form>
      </Stack>
    </Paper>
  );
}


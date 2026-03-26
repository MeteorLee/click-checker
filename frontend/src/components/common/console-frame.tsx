import { Box } from "@mantine/core";
import type { ReactNode } from "react";

type ConsoleFrameProps = {
  children: ReactNode;
};

export function ConsoleFrame({ children }: ConsoleFrameProps) {
  return <Box className="console-frame">{children}</Box>;
}


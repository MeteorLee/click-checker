import { DashboardNavigation } from "@/components/common/dashboard-navigation";
import { Box } from "@mantine/core";
import type { ReactNode } from "react";

type ConsoleFrameProps = {
  children: ReactNode;
};

export function ConsoleFrame({ children }: ConsoleFrameProps) {
  return (
    <Box className="console-frame">
      <Box className="console-layout">
        <DashboardNavigation />
        <Box className="console-main">{children}</Box>
      </Box>
    </Box>
  );
}

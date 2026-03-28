import { GuideNavigation } from "@/components/guide/guide-navigation";
import { Box } from "@mantine/core";
import type { ReactNode } from "react";

type GuideFrameProps = {
  children: ReactNode;
};

export function GuideFrame({ children }: GuideFrameProps) {
  return (
    <Box className="console-frame">
      <Box className="console-layout">
        <GuideNavigation />
        <Box className="console-main">{children}</Box>
      </Box>
    </Box>
  );
}

import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";
import tsconfigPaths from "vite-tsconfig-paths";

export default defineConfig({
  plugins: [react(), tsconfigPaths()],
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: true,
    include: ["src/**/*.{test,spec}.{ts,tsx}"],
    coverage: {
      provider: "v8",
      reporter: ["text", "html", "json-summary", "lcov"],
      reportsDirectory: "coverage/frontend",
      include: [
        "src/lib/api.ts",
        "src/lib/pincode.ts",
        "src/lib/product-fit.ts",
        "src/lib/coupons.functions.ts",
        "src/lib/orders.functions.ts",
        "src/hooks/useAuth.tsx",
        "src/store/shop.tsx",
        "src/routes/login.tsx",
        "src/routes/admin.login.tsx",
      ],
      exclude: [
        "src/routeTree.gen.ts",
        "src/router.tsx",
        "src/styles.css",
        "src/assets/**",
        "src/data/**",
        "src/test/**",
        "src/**/*.d.ts",
      ],
      thresholds: {
        statements: 91,
        branches: 83,
        functions: 94,
        lines: 94,
      },
    },
  },
});

// Reexport the native module. On web, it will be resolved to MotionTrackerModule.web.ts
// and on native platforms to MotionTrackerModule.ts
import { requireNativeModule } from "expo-modules-core";
export default requireNativeModule("MotionTracker");
export * from "./src/MotionTracker.types";
export { default as MotionTrackerView } from "./src/MotionTrackerView";


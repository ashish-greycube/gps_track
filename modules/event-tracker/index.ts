// Reexport the native module. On web it resolves to EventTrackerModule.web.ts,
// on native platforms to the autolinked EventTracker native module.
// NOTE: Android-only for now — there is no iOS implementation yet, so importing
// this on iOS will throw. Guard usage with Platform.OS === "android".
import { requireNativeModule } from "expo-modules-core";
export default requireNativeModule("EventTracker");
export * from "./src/EventTracker.types";

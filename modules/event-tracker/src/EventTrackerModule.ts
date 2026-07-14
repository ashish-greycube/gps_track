import { NativeModule, requireNativeModule } from "expo";

import { EventTrackerModuleEvents, TrackedLocation } from "./EventTracker.types";

declare class EventTrackerModule extends NativeModule<EventTrackerModuleEvents> {
  // Registers event-driven location + activity-transition listeners. No foreground
  // service and no notification — the OS wakes the app from a dead state to record
  // points, then the app sleeps again.
  startService(): void;
  stopService(): void;
  getLocations(): Promise<Array<TrackedLocation>>;
}

export default requireNativeModule<EventTrackerModule>("EventTracker");

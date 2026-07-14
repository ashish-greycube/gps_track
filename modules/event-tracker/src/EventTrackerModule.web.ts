// Non-functional web stub. Event-driven background tracking is native-only.
import { TrackedLocation } from "./EventTracker.types";

export default {
  startService(): void {},
  stopService(): void {},
  async getLocations(): Promise<Array<TrackedLocation>> {
    return [];
  },
};

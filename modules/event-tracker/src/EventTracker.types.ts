export type EventTrackerModuleEvents = Record<string, never>;

export type TrackedLocation = {
  id: number;
  latitude: number;
  longitude: number;
  timestamp: number;
  source: string; // "PASSIVE_UPDATE" | "ACTIVITY_TRANSITION"
};

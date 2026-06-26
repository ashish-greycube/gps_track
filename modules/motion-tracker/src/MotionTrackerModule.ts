import { NativeModule, requireNativeModule } from 'expo';

import { MotionTrackerModuleEvents } from './MotionTracker.types';

declare class MotionTrackerModule extends NativeModule<MotionTrackerModuleEvents> {
  startService(): void;
  stopService(): void;
  getLocations(): Promise<Array<{
    id: number;
    latitude: number;
    longitude: number;
    timestamp: number;
    source: string;
  }>>;
}

export default requireNativeModule<MotionTrackerModule>('MotionTracker');

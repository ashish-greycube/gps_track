import { registerWebModule, NativeModule } from 'expo';

import { ChangeEventPayload } from './MotionTracker.types';

type MotionTrackerModuleEvents = {
  onChange: (params: ChangeEventPayload) => void;
}

class MotionTrackerModule extends NativeModule<MotionTrackerModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
};

export default registerWebModule(MotionTrackerModule, 'MotionTrackerModule');

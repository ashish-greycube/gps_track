import { requireNativeView } from 'expo';
import * as React from 'react';

import { MotionTrackerViewProps } from './MotionTracker.types';

const NativeView: React.ComponentType<MotionTrackerViewProps> =
  requireNativeView('MotionTracker');

export default function MotionTrackerView(props: MotionTrackerViewProps) {
  return <NativeView {...props} />;
}

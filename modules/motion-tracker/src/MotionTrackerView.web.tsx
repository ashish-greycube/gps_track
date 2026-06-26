import * as React from 'react';

import { MotionTrackerViewProps } from './MotionTracker.types';

export default function MotionTrackerView(props: MotionTrackerViewProps) {
  return (
    <div>
      <iframe
        style={{ flex: 1 }}
        src={props.url}
        onLoad={() => props.onLoad({ nativeEvent: { url: props.url } })}
      />
    </div>
  );
}

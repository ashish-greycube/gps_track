// AppDelegate.swift is already patched directly in ios/GPSTrack/AppDelegate.swift.
// This plugin is a no-op kept only to satisfy the app.json reference.
module.exports = function withLocationRelaunch(config) {
  return config;
};

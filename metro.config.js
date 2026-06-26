const { getDefaultConfig } = require('expo/metro-config');

const config = getDefaultConfig(__dirname);

// Block the nested react-native that some packages hoist into
// node_modules/react-native/node_modules/react-native — the codegen
// chokes on VirtualViewExperimentalNativeComponent.js inside it.
config.resolver.blockList = [
  /node_modules\/react-native\/node_modules\/react-native\/.*/,
];

module.exports = config;

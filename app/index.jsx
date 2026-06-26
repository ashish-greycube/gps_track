import * as Location from 'expo-location';
import { Pedometer } from 'expo-sensors';
import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import {
  Alert, Linking, PermissionsAndroid, Platform,
  StyleSheet, Text, TouchableOpacity, View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import MotionTracker from '../modules/motion-tracker';

const requestAllPermissions = async () => {
  if (Platform.OS !== 'android') return true;

  const foreground = await PermissionsAndroid.requestMultiple([
    PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
    PermissionsAndroid.PERMISSIONS.ACCESS_COARSE_LOCATION,
    PermissionsAndroid.PERMISSIONS.ACTIVITY_RECOGNITION,
    PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
  ]);

  const foregroundGranted =
    foreground[PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION] === PermissionsAndroid.RESULTS.GRANTED;

  if (!foregroundGranted) return false;

  const background = await PermissionsAndroid.request(
    PermissionsAndroid.PERMISSIONS.ACCESS_BACKGROUND_LOCATION,
    {
      title: 'Background Location',
      message: 'Allow location access all the time so tracking works when the app is in the background.',
      buttonPositive: 'Allow all the time',
      buttonNegative: 'Deny',
    }
  );

  return background === PermissionsAndroid.RESULTS.GRANTED;
};

export default function App() {
  const router = useRouter();
  const [permissionsGranted, setPermissionsGranted] = useState(false);

  useEffect(() => {
    if (Platform.OS === 'ios') {
      (async () => {
        // 1. Location — "While Using" first, then "Always Allow"
        const { status: fgStatus } = await Location.requestForegroundPermissionsAsync();
        if (fgStatus !== 'granted') {
          Alert.alert(
            'Location Required',
            'Location permission is needed for tracking. Please enable it in Settings.',
            [
              { text: 'Cancel', style: 'cancel' },
              { text: 'Open Settings', onPress: () => Linking.openSettings() },
            ]
          );
          return;
        }
        await Location.requestBackgroundPermissionsAsync();

        // 2. Physical Activity (NSMotionUsageDescription)
        await Pedometer.requestPermissionsAsync();
      })();
    } else {
      requestAllPermissions().then(setPermissionsGranted);
    }
  }, []);

  const handleStartShield = async () => {
    if (Platform.OS === 'android') {
      let granted = permissionsGranted;
      if (!granted) {
        granted = await requestAllPermissions();
        setPermissionsGranted(granted);
      }
      if (!granted) {
        Alert.alert(
          'Permissions Required',
          'Location and activity permissions are needed for tracking. Please enable them in Settings.',
          [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Open Settings', onPress: () => Linking.openSettings() },
          ]
        );
        return;
      }
    }

    MotionTracker.startService();
  };

  const handleStopShield = () => {
    MotionTracker.stopService();
  };

  return (
    <SafeAreaView style={styles.container}>

      {/* Header */}
      <View style={styles.header}>
        <View style={styles.iconCircle}>
          <Text style={styles.iconEmoji}>📍</Text>
        </View>
        <Text style={styles.title}>GPS Tracking</Text>
        <Text style={styles.subtitle}>This app will track your location every 5 minutes</Text>
      </View>

      {/* Action Buttons */}
      <View style={styles.card}>
        <TouchableOpacity
          style={[styles.button, styles.buttonStart]}
          onPress={handleStartShield}
          activeOpacity={0.8}
        >
          <Text style={styles.buttonIcon}>▶</Text>
          <Text style={styles.buttonText}>Start Tracking</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.button, styles.buttonStop]}
          onPress={handleStopShield}
          activeOpacity={0.8}
        >
          <Text style={styles.buttonIcon}>■</Text>
          <Text style={styles.buttonText}>Stop Tracking</Text>
        </TouchableOpacity>

        <View style={styles.divider} />

        <TouchableOpacity
          style={[styles.button, styles.buttonMap]}
          onPress={() => router.push('/MapScreen')}
          activeOpacity={0.8}
        >
          <Text style={styles.buttonIcon}>🗺</Text>
          <Text style={[styles.buttonText , {color: '#0000'}]}>View Map</Text>
        </TouchableOpacity>
      </View>

      {/* Footer */}
      <TouchableOpacity
        style={styles.footer}
        onPress={() => Linking.openURL('https://greycube.in')}
        activeOpacity={0.7}
      >
        <Text style={styles.footerText}>Powered by </Text>
        <Text style={styles.footerLink}>Greycube</Text>
      </TouchableOpacity>

    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F0F4FF',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 32,
    paddingHorizontal: 24,
  },
  header: {
    alignItems: 'center',
    gap: 8,
  },
  iconCircle: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
    shadowColor: '#1A73E8',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.15,
    shadowRadius: 12,
    elevation: 6,
  },
  iconEmoji: {
    fontSize: 36,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#1C1C1E',
    letterSpacing: -0.5,
  },
  subtitle: {
    fontSize: 14,
    color: '#6B7280',
    fontWeight: '400',
  },
  card: {
    width: '100%',
    backgroundColor: '#FFFFFF',
    borderRadius: 20,
    padding: 16,
    gap: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 12,
    elevation: 4,
  },
  button: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 16,
    borderRadius: 14,
    gap: 10,
  },
  buttonStart: {
    backgroundColor: '#5998e9',
  },
  buttonStop: {
    backgroundColor: '#f47266',
  },
  buttonMap: {
    backgroundColor: '#F0F4FF',
    borderWidth: 1,
    borderColor: '#D1D9FF',
  },
  buttonIcon: {
    fontSize: 16,
    color: '#ffff',
  },
  buttonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#ffff',
  },
  divider: {
    height: 1,
    backgroundColor: '#F3F4F6',
    marginVertical: 4,
  },
  footer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  footerText: {
    fontSize: 13,
    color: '#9CA3AF',
  },
  footerLink: {
    fontSize: 13,
    color: '#1A73E8',
    fontWeight: '600',
  },
});

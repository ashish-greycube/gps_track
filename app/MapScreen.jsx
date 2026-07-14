import { AppleMaps, GoogleMaps } from 'expo-maps';
import { useLocalSearchParams, useRouter } from 'expo-router';
import React, { useEffect, useState } from 'react';
import { Alert, Platform, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import MotionTracker from '../modules/motion-tracker';

// event-tracker is Android-only; requiring it on iOS would throw at load time.
const EventTracker = Platform.OS === 'android'
    ? require('../modules/event-tracker').default
    : null;

const MapScreen = () => {
    const router = useRouter();
    const { engine } = useLocalSearchParams();
    const [cordData, setCordData] = useState([]);
    const [zoom, setZoom] = useState(15);

    useEffect(() => {
        const tracker = engine === 'event' && EventTracker ? EventTracker : MotionTracker;
        tracker.getLocations().then(setCordData);
    }, [engine]);

    const zoomIn  = () => setZoom(z => Math.min(z + 1, 20));
    const zoomOut = () => setZoom(z => Math.max(z - 1, 1));

    const renderMap = () => {
        if (!cordData || cordData.length === 0) {
            return (
                <View style={styles.emptyState}>
                    <Text style={styles.emptyIcon}>📍</Text>
                    <Text style={styles.emptyTitle}>No Data Yet</Text>
                    <Text style={styles.emptySubtitle}>Start tracking to see your route here</Text>
                </View>
            );
        }

        const startPoint = cordData[cordData.length - 1];
        const endPoint   = cordData[0];
        const coords     = cordData.map(p => ({ latitude: p.latitude, longitude: p.longitude }));

        if (Platform.OS === 'ios') {
            return (
                <AppleMaps.View
                    style={StyleSheet.absoluteFill}
                    cameraPosition={{
                        coordinates: { latitude: endPoint.latitude, longitude: endPoint.longitude },
                        zoom,
                    }}
                    polylines={[{ color: '#1A73E8', width: 15, coordinates: coords }]}
                    markers={[
                        { id: 'start', coordinates: { latitude: startPoint.latitude, longitude: startPoint.longitude }, title: 'Start', tintColor: '#34A853' },
                        { id: 'end',   coordinates: { latitude: endPoint.latitude,   longitude: endPoint.longitude   }, title: 'End',   tintColor: '#EA4335' },
                    ]}
                />
            );
        }

        if (Platform.OS === 'android') {
            return (
                <GoogleMaps.View
                    style={StyleSheet.absoluteFill}
                    cameraPosition={{
                        coordinates: { latitude: endPoint.latitude, longitude: endPoint.longitude },
                        zoom,
                    }}
                    polylines={[{ color: '#1A73E8', width: 15, coordinates: coords }]}
                    markers={[
                        { id: 'start', coordinates: { latitude: startPoint.latitude, longitude: startPoint.longitude }, title: 'Start', snippet: 'First recorded point' },
                        { id: 'end',   coordinates: { latitude: endPoint.latitude,   longitude: endPoint.longitude   }, title: 'End',   snippet: 'Latest recorded point' },
                    ]}
                />
            );
        }

        return <Text style={styles.emptyTitle}>Maps unavailable on this platform</Text>;
    };

    return (
        <SafeAreaView style={styles.container}>
            {/* Header */}
            <View style={styles.header}>
                <TouchableOpacity style={styles.backButton} onPress={() => router.back()} activeOpacity={0.7}>
                    <Text style={styles.backIcon}>←</Text>
                </TouchableOpacity>
                <Text style={styles.headerTitle}>Route Map</Text>
                <TouchableOpacity
                    style={styles.pointsBadge}
                    activeOpacity={0.7}
                    onPress={() => {
                        if (!cordData.length) { Alert.alert('No Data', 'No locations saved yet.'); return; }
                        const output = cordData.map((p, i) =>
                            `${i + 1}. ${new Date(p.timestamp).toLocaleTimeString()}\n   ${p.latitude}, ${p.longitude}\n   ${p.source}`
                        ).join('\n\n');
                        Alert.alert(`${cordData.length} Saved Points`, output);
                    }}
                >
                    <Text style={styles.pointsText}>{cordData.length} pts</Text>
                </TouchableOpacity>
            </View>

            {/* Map */}
            <View style={styles.mapContainer}>
                {renderMap()}
                {cordData.length > 0 && (
                    <View style={styles.zoomControls}>
                        <TouchableOpacity style={styles.zoomButton} onPress={zoomIn} activeOpacity={0.8}>
                            <Text style={styles.zoomIcon}>+</Text>
                        </TouchableOpacity>
                        <View style={styles.zoomDivider} />
                        <TouchableOpacity style={styles.zoomButton} onPress={zoomOut} activeOpacity={0.8}>
                            <Text style={styles.zoomIcon}>−</Text>
                        </TouchableOpacity>
                    </View>
                )}
            </View>
        </SafeAreaView>
    );
};

export default MapScreen;

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: '#F0F4FF',
    },
    header: {
        flexDirection: 'row',
        alignItems: 'center',
        paddingHorizontal: 16,
        paddingVertical: 12,
        gap: 12,
    },
    backButton: {
        width: 40,
        height: 40,
        borderRadius: 12,
        backgroundColor: '#FFFFFF',
        alignItems: 'center',
        justifyContent: 'center',
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 1 },
        shadowOpacity: 0.08,
        shadowRadius: 4,
        elevation: 2,
    },
    backIcon: {
        fontSize: 20,
        color: '#1C1C1E',
        fontWeight: '600',
    },
    headerTitle: {
        flex: 1,
        fontSize: 20,
        fontWeight: '700',
        color: '#1C1C1E',
        letterSpacing: -0.3,
    },
    pointsBadge: {
        backgroundColor: '#1A73E8',
        paddingHorizontal: 12,
        paddingVertical: 4,
        borderRadius: 20,
    },
    pointsText: {
        fontSize: 12,
        fontWeight: '600',
        color: '#FFFFFF',
    },
    mapContainer: {
        flex: 1,
        marginHorizontal: 16,
        marginBottom: 16,
        borderRadius: 20,
        overflow: 'hidden',
        backgroundColor: '#E5E7EB',
    },
    emptyState: {
        flex: 1,
        alignItems: 'center',
        justifyContent: 'center',
        gap: 8,
    },
    emptyIcon: {
        fontSize: 48,
        marginBottom: 8,
    },
    emptyTitle: {
        fontSize: 18,
        fontWeight: '600',
        color: '#1C1C1E',
    },
    emptySubtitle: {
        fontSize: 14,
        color: '#6B7280',
    },
    zoomControls: {
        position: 'absolute',
        right: 12,
        bottom: 24,
        backgroundColor: '#FFFFFF',
        borderRadius: 12,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.12,
        shadowRadius: 6,
        elevation: 4,
        overflow: 'hidden',
    },
    zoomButton: {
        width: 44,
        height: 44,
        alignItems: 'center',
        justifyContent: 'center',
    },
    zoomIcon: {
        fontSize: 22,
        fontWeight: '400',
        color: '#1C1C1E',
        lineHeight: 26,
    },
    zoomDivider: {
        height: StyleSheet.hairlineWidth,
        backgroundColor: '#E5E7EB',
        marginHorizontal: 8,
    },
});

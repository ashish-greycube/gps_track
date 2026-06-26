import { AppleMaps, GoogleMaps } from 'expo-maps';
import { useRouter } from 'expo-router';
import React, { useEffect, useState } from 'react';
import { Alert, Platform, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import MotionTracker from '../modules/motion-tracker';

const MapScreen = () => {
    const router = useRouter();
    const [cordData, setCordData] = useState([]);

    useEffect(() => {
        MotionTracker.getLocations().then(setCordData);
    }, []);

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

        if (Platform.OS === 'ios') {
            return <AppleMaps.View style={StyleSheet.absoluteFill} />;
        }

        if (Platform.OS === 'android') {
            return (
                <GoogleMaps.View
                    style={StyleSheet.absoluteFill}
                    cameraPosition={{
                        coordinates: {
                            latitude: cordData[0].latitude,
                            longitude: cordData[0].longitude,
                        },
                        zoom: 15,
                    }}
                    polylines={[{
                        color: '#1A73E8',
                        width: 15,
                        coordinates: cordData.map(p => ({ latitude: p.latitude, longitude: p.longitude })),
                    }]}
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
});

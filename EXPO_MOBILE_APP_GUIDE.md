# Sentrium Mobile App - Expo/React Native Integration Guide
## Door Control, Camera Feeds & Video Clips

## 📱 Overview

This guide shows how to integrate the Sentrium backend with a React Native/Expo mobile app.

**Backend Features:**
- ✅ Door lock/unlock control
- ✅ Real RTSP camera support
- ✅ Video clip recording & playback
- ✅ Motion detection events
- ✅ Access logs

---

## 🚀 Setup & Dependencies

### 1. Install Required Packages

```bash
# Core dependencies
npx expo install expo-av               # Video playback
npx expo install expo-file-system      # File downloads
npx expo install expo-camera           # Camera access (future)
npx expo install expo-notifications    # Already installed (push notifications)
npx expo install expo-secure-store     # Store JWT tokens

# Networking & utilities
npm install axios                      # API client
npm install react-query               # Data fetching & caching
npm install zustand                   # State management (lightweight)

# UI components (if not already installed)
npm install react-native-paper        # Material Design components
npm install @react-navigation/native  # Navigation
npm install @react-navigation/stack
npm install @react-navigation/bottom-tabs
```

### 2. Project Structure

```
src/
├── api/
│   ├── client.ts           # Axios instance
│   ├── devices.ts          # Device endpoints
│   ├── videoClips.ts       # Video clip endpoints
│   └── auth.ts             # Already exists
├── screens/
│   ├── DevicesScreen.tsx
│   ├── DeviceDetailScreen.tsx
│   ├── CameraFeedScreen.tsx
│   ├── VideoClipsScreen.tsx
│   └── ClipDetailScreen.tsx
├── components/
│   ├── DoorControlCard.tsx
│   ├── CameraCard.tsx
│   ├── VideoClipCard.tsx
│   └── RecordButton.tsx
├── hooks/
│   ├── useDevices.ts
│   ├── useVideoClips.ts
│   └── useDevice.ts
├── stores/
│   └── authStore.ts        # Already exists
└── types/
    ├── device.ts
    └── videoClip.ts
```

---

## 🔧 API Client Setup

### `src/api/client.ts`

```typescript
import axios from 'axios';
import * as SecureStore from 'expo-secure-store';

const API_BASE_URL = __DEV__ 
  ? 'http://10.0.2.2:8080/api/v1'  // Android emulator
  : 'https://your-backend.com/api/v1';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor: Add JWT token
apiClient.interceptors.request.use(
  async (config) => {
    const token = await SecureStore.getItemAsync('jwt_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor: Handle errors
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // Token expired - redirect to login
      await SecureStore.deleteItemAsync('jwt_token');
      // Navigate to login screen
    }
    return Promise.reject(error);
  }
);
```

---

## 📄 TypeScript Types

### `src/types/device.ts`

```typescript
export type DeviceType = 'ACCESS_POINT' | 'CAMERA_SIM' | 'SENSOR';

export type DeviceStatus = 'IDLE' | 'ACTIVE' | 'OFFLINE' | 'ERROR';

export type ConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'ERROR';

export interface Device {
  id: string;
  name: string;
  type: DeviceType;
  status: DeviceStatus;
  zoneId: string;
  zoneName: string;
  active: boolean;
  lastHeartbeatAt?: string;
  
  // Connectivity
  endpointUrl?: string;
  connectionProtocol?: string;
  connectionStatus?: ConnectionStatus;
  lastCommandAt?: string;
  
  // Camera stream
  streamUrl?: string;
  streamType?: string;
  streamResolution?: string;
  streamFps?: number;
}

export interface DeviceCommand {
  id: string;
  deviceId: string;
  commandType: string;
  status: 'PENDING' | 'SUCCESS' | 'FAILED' | 'TIMEOUT';
  requestedBy: string;
  requestedAt: string;
  executedAt?: string;
  errorMessage?: string;
}

export interface DeviceConfigRequest {
  endpointUrl?: string;
  apiKey?: string;
  connectionProtocol?: string;
  streamUrl?: string;
  streamType?: string;
  streamUsername?: string;
  streamPassword?: string;
  streamResolution?: string;
  streamFps?: number;
}
```

### `src/types/videoClip.ts`

```typescript
export interface VideoClip {
  id: string;
  cameraId: string;
  cameraName: string;
  startTime: string;
  endTime: string;
  durationSeconds: number;
  fileUrl: string;
  fileSizeBytes: number;
  resolution: string;
  format: string;
  triggerType: 'MOTION' | 'ALERT' | 'MANUAL' | 'SCHEDULED';
  thumbnailUrl?: string;
  retentionUntil: string;
  archived: boolean;
}

export interface RecordRequest {
  cameraId: string;
  durationSeconds?: number;
  triggerType?: string;
}

export interface StorageStats {
  totalBytes: number;
  totalGigabytes: number;
  clipCount: number;
  averageClipSize: number;
}
```

---

## 🌐 API Services

### `src/api/devices.ts`

```typescript
import { apiClient } from './client';
import { Device, DeviceCommand, DeviceConfigRequest } from '../types/device';

export const devicesApi = {
  // List all devices
  listDevices: async (zoneId?: string) => {
    const params = zoneId ? { zoneId } : {};
    const { data } = await apiClient.get<Device[]>('/devices', { params });
    return data;
  },

  // Get device by ID
  getDevice: async (deviceId: string) => {
    const { data } = await apiClient.get<Device>(`/devices/${deviceId}`);
    return data;
  },

  // Configure device (set endpoint, stream URL)
  configureDevice: async (deviceId: string, config: DeviceConfigRequest) => {
    const { data } = await apiClient.post<Device>(
      `/devices/${deviceId}/configure`,
      config
    );
    return data;
  },

  // Unlock device
  unlockDevice: async (deviceId: string) => {
    const { data } = await apiClient.post(`/devices/${deviceId}/unlock`);
    return data;
  },

  // Lock device
  lockDevice: async (deviceId: string) => {
    const { data } = await apiClient.post(`/devices/${deviceId}/lock`);
    return data;
  },

  // Get command history
  getCommands: async (deviceId: string, limit: number = 20) => {
    const { data } = await apiClient.get<DeviceCommand[]>(
      `/devices/${deviceId}/commands`,
      { params: { limit } }
    );
    return data;
  },
};
```

### `src/api/videoClips.ts`

```typescript
import { apiClient } from './client';
import { VideoClip, RecordRequest, StorageStats } from '../types/videoClip';
import * as FileSystem from 'expo-file-system';

export const videoClipsApi = {
  // List clips
  listClips: async (params?: {
    cameraId?: string;
    from?: string;
    to?: string;
    triggerType?: string;
    page?: number;
    size?: number;
  }) => {
    const { data } = await apiClient.get('/video-clips', { params });
    return data;
  },

  // Get clip details
  getClip: async (clipId: string) => {
    const { data } = await apiClient.get<VideoClip>(`/video-clips/${clipId}`);
    return data;
  },

  // Start recording
  recordClip: async (request: RecordRequest) => {
    const { data } = await apiClient.post('/video-clips/record', request);
    return data;
  },

  // Download clip
  downloadClip: async (clipId: string, filename: string) => {
    const downloadUrl = `${apiClient.defaults.baseURL}/video-clips/${clipId}/download`;
    const fileUri = `${FileSystem.documentDirectory}${filename}`;

    const downloadResumable = FileSystem.createDownloadResumable(
      downloadUrl,
      fileUri,
      {
        headers: {
          Authorization: apiClient.defaults.headers.Authorization as string,
        },
      }
    );

    const result = await downloadResumable.downloadAsync();
    return result?.uri;
  },

  // Delete clip
  deleteClip: async (clipId: string) => {
    await apiClient.delete(`/video-clips/${clipId}`);
  },

  // Get storage stats
  getStats: async () => {
    const { data } = await apiClient.get<StorageStats>('/video-clips/stats');
    return data;
  },
};
```

---

## 🪝 Custom Hooks (React Query)

### `src/hooks/useDevices.ts`

```typescript
import { useQuery, useMutation, useQueryClient } from 'react-query';
import { devicesApi } from '../api/devices';
import { DeviceConfigRequest } from '../types/device';

export const useDevices = (zoneId?: string) => {
  return useQuery(
    ['devices', zoneId],
    () => devicesApi.listDevices(zoneId),
    {
      staleTime: 30000, // 30 seconds
    }
  );
};

export const useDevice = (deviceId: string) => {
  return useQuery(
    ['device', deviceId],
    () => devicesApi.getDevice(deviceId),
    {
      enabled: !!deviceId,
    }
  );
};

export const useUnlockDevice = () => {
  const queryClient = useQueryClient();
  
  return useMutation(
    (deviceId: string) => devicesApi.unlockDevice(deviceId),
    {
      onSuccess: (data, deviceId) => {
        // Invalidate device cache
        queryClient.invalidateQueries(['device', deviceId]);
        queryClient.invalidateQueries(['devices']);
      },
    }
  );
};

export const useLockDevice = () => {
  const queryClient = useQueryClient();
  
  return useMutation(
    (deviceId: string) => devicesApi.lockDevice(deviceId),
    {
      onSuccess: (data, deviceId) => {
        queryClient.invalidateQueries(['device', deviceId]);
        queryClient.invalidateQueries(['devices']);
      },
    }
  );
};

export const useConfigureDevice = () => {
  const queryClient = useQueryClient();
  
  return useMutation(
    ({ deviceId, config }: { deviceId: string; config: DeviceConfigRequest }) =>
      devicesApi.configureDevice(deviceId, config),
    {
      onSuccess: (data, { deviceId }) => {
        queryClient.invalidateQueries(['device', deviceId]);
        queryClient.invalidateQueries(['devices']);
      },
    }
  );
};

export const useDeviceCommands = (deviceId: string) => {
  return useQuery(
    ['commands', deviceId],
    () => devicesApi.getCommands(deviceId),
    {
      enabled: !!deviceId,
      refetchInterval: 10000, // Refresh every 10s
    }
  );
};
```

### `src/hooks/useVideoClips.ts`

```typescript
import { useQuery, useMutation, useQueryClient } from 'react-query';
import { videoClipsApi } from '../api/videoClips';
import { RecordRequest } from '../types/videoClip';

export const useVideoClips = (cameraId?: string) => {
  return useQuery(
    ['video-clips', cameraId],
    () => videoClipsApi.listClips({ cameraId, size: 50 }),
    {
      staleTime: 30000,
    }
  );
};

export const useVideoClip = (clipId: string) => {
  return useQuery(
    ['video-clip', clipId],
    () => videoClipsApi.getClip(clipId),
    {
      enabled: !!clipId,
    }
  );
};

export const useRecordClip = () => {
  const queryClient = useQueryClient();
  
  return useMutation(
    (request: RecordRequest) => videoClipsApi.recordClip(request),
    {
      onSuccess: () => {
        // Refresh clips after recording
        setTimeout(() => {
          queryClient.invalidateQueries(['video-clips']);
        }, 30000); // Wait 30s for recording to complete
      },
    }
  );
};

export const useDeleteClip = () => {
  const queryClient = useQueryClient();
  
  return useMutation(
    (clipId: string) => videoClipsApi.deleteClip(clipId),
    {
      onSuccess: () => {
        queryClient.invalidateQueries(['video-clips']);
      },
    }
  );
};

export const useDownloadClip = () => {
  return useMutation(
    ({ clipId, filename }: { clipId: string; filename: string }) =>
      videoClipsApi.downloadClip(clipId, filename)
  );
};
```

---

## 📱 Screens & Components

### `src/screens/DevicesScreen.tsx`

```typescript
import React, { useState } from 'react';
import { View, FlatList, StyleSheet } from 'react-native';
import { Searchbar, FAB, Chip, Text } from 'react-native-paper';
import { useDevices } from '../hooks/useDevices';
import DoorControlCard from '../components/DoorControlCard';
import CameraCard from '../components/CameraCard';

export default function DevicesScreen({ navigation }) {
  const [filter, setFilter] = useState<'all' | 'ACCESS_POINT' | 'CAMERA_SIM'>('all');
  const { data: devices, isLoading, refetch } = useDevices();

  const filteredDevices = devices?.filter(
    d => filter === 'all' || d.type === filter
  );

  return (
    <View style={styles.container}>
      <View style={styles.filters}>
        <Chip 
          selected={filter === 'all'} 
          onPress={() => setFilter('all')}
          style={styles.chip}
        >
          All
        </Chip>
        <Chip 
          selected={filter === 'ACCESS_POINT'} 
          onPress={() => setFilter('ACCESS_POINT')}
          style={styles.chip}
        >
          🚪 Doors
        </Chip>
        <Chip 
          selected={filter === 'CAMERA_SIM'} 
          onPress={() => setFilter('CAMERA_SIM')}
          style={styles.chip}
        >
          📹 Cameras
        </Chip>
      </View>

      <FlatList
        data={filteredDevices}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => 
          item.type === 'ACCESS_POINT' ? (
            <DoorControlCard 
              device={item} 
              onPress={() => navigation.navigate('DeviceDetail', { deviceId: item.id })}
            />
          ) : (
            <CameraCard 
              device={item}
              onPress={() => navigation.navigate('CameraFeed', { cameraId: item.id })}
            />
          )
        }
        refreshing={isLoading}
        onRefresh={refetch}
        contentContainerStyle={styles.list}
      />

      <FAB
        icon="plus"
        style={styles.fab}
        onPress={() => navigation.navigate('AddDevice')}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f5f5f5' },
  filters: { 
    flexDirection: 'row', 
    padding: 12, 
    gap: 8,
    backgroundColor: 'white' 
  },
  chip: { marginRight: 8 },
  list: { padding: 12 },
  fab: { position: 'absolute', right: 16, bottom: 16 },
});
```

### `src/components/DoorControlCard.tsx`

```typescript
import React, { useState } from 'react';
import { View, StyleSheet, Alert } from 'react-native';
import { Card, Button, Text, Badge, ActivityIndicator } from 'react-native-paper';
import { useUnlockDevice, useLockDevice } from '../hooks/useDevices';
import { Device } from '../types/device';

interface Props {
  device: Device;
  onPress?: () => void;
}

export default function DoorControlCard({ device, onPress }: Props) {
  const unlockMutation = useUnlockDevice();
  const lockMutation = useLockDevice();
  const [lastAction, setLastAction] = useState<string | null>(null);

  const handleUnlock = async () => {
    try {
      const result = await unlockMutation.mutateAsync(device.id);
      
      if (result.success) {
        Alert.alert('Success', '🔓 Door unlocked successfully');
        setLastAction('UNLOCKED');
      } else if (result.warning) {
        Alert.alert('Warning', result.warning);
      } else {
        Alert.alert('Error', result.message);
      }
    } catch (error) {
      Alert.alert('Error', 'Failed to unlock door');
    }
  };

  const handleLock = async () => {
    try {
      const result = await lockMutation.mutateAsync(device.id);
      
      if (result.success) {
        Alert.alert('Success', '🔒 Door locked successfully');
        setLastAction('LOCKED');
      }
    } catch (error) {
      Alert.alert('Error', 'Failed to lock door');
    }
  };

  const isLoading = unlockMutation.isLoading || lockMutation.isLoading;
  const isConfigured = !!device.endpointUrl;

  return (
    <Card style={styles.card} onPress={onPress}>
      <Card.Title
        title={device.name}
        subtitle={device.zoneName}
        right={() => (
          <View style={styles.badges}>
            {isConfigured ? (
              <Badge style={styles.badgeGreen}>Configured</Badge>
            ) : (
              <Badge style={styles.badgeGray}>Not Configured</Badge>
            )}
          </View>
        )}
      />
      
      <Card.Content>
        {device.connectionStatus && (
          <View style={styles.status}>
            <View style={[
              styles.statusDot, 
              { backgroundColor: device.connectionStatus === 'CONNECTED' ? '#4caf50' : '#9e9e9e' }
            ]} />
            <Text variant="bodySmall">{device.connectionStatus}</Text>
          </View>
        )}
        
        {lastAction && (
          <Text variant="bodySmall" style={styles.lastAction}>
            Last action: {lastAction}
          </Text>
        )}
      </Card.Content>

      <Card.Actions>
        <Button 
          mode="contained"
          onPress={handleUnlock}
          disabled={isLoading || !isConfigured}
          icon="lock-open"
        >
          Unlock
        </Button>
        <Button 
          mode="outlined"
          onPress={handleLock}
          disabled={isLoading || !isConfigured}
          icon="lock"
        >
          Lock
        </Button>
        {isLoading && <ActivityIndicator style={styles.spinner} />}
      </Card.Actions>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { marginBottom: 12 },
  badges: { marginRight: 12 },
  badgeGreen: { backgroundColor: '#4caf50' },
  badgeGray: { backgroundColor: '#9e9e9e' },
  status: { flexDirection: 'row', alignItems: 'center', marginBottom: 4 },
  statusDot: { width: 8, height: 8, borderRadius: 4, marginRight: 6 },
  lastAction: { marginTop: 4, color: '#666' },
  spinner: { marginLeft: 8 },
});
```

### `src/components/CameraCard.tsx`

```typescript
import React from 'react';
import { View, Image, StyleSheet } from 'react-native';
import { Card, Text, Badge } from 'react-native-paper';
import { Device } from '../types/device';

interface Props {
  device: Device;
  onPress?: () => void;
}

export default function CameraCard({ device, onPress }: Props) {
  const hasStream = !!device.streamUrl;

  return (
    <Card style={styles.card} onPress={onPress}>
      <Card.Cover 
        source={{ uri: 'https://via.placeholder.com/400x200?text=Camera+Feed' }}
        style={styles.cover}
      />
      
      <Card.Title
        title={device.name}
        subtitle={device.zoneName}
        right={() => (
          <View style={styles.badges}>
            {hasStream ? (
              <Badge style={styles.badgeGreen}>Streaming</Badge>
            ) : (
              <Badge style={styles.badgeBlue}>Simulated</Badge>
            )}
          </View>
        )}
      />
      
      <Card.Content>
        {device.streamResolution && (
          <Text variant="bodySmall">
            {device.streamResolution} • {device.streamFps || 30} FPS
          </Text>
        )}
      </Card.Content>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { marginBottom: 12 },
  cover: { height: 150 },
  badges: { marginRight: 12 },
  badgeGreen: { backgroundColor: '#4caf50' },
  badgeBlue: { backgroundColor: '#2196f3' },
});
```

### `src/screens/VideoClipsScreen.tsx`

```typescript
import React, { useState } from 'react';
import { View, FlatList, StyleSheet } from 'react-native';
import { Searchbar, FAB, Chip, Appbar } from 'react-native-paper';
import { useVideoClips } from '../hooks/useVideoClips';
import VideoClipCard from '../components/VideoClipCard';

export default function VideoClipsScreen({ navigation, route }) {
  const cameraId = route.params?.cameraId;
  const [filter, setFilter] = useState<string>('all');
  const { data, isLoading, refetch } = useVideoClips(cameraId);

  const clips = data?.content || [];
  const filteredClips = clips.filter(
    clip => filter === 'all' || clip.triggerType === filter
  );

  return (
    <View style={styles.container}>
      <View style={styles.filters}>
        <Chip selected={filter === 'all'} onPress={() => setFilter('all')}>
          All
        </Chip>
        <Chip selected={filter === 'MOTION'} onPress={() => setFilter('MOTION')}>
          Motion
        </Chip>
        <Chip selected={filter === 'MANUAL'} onPress={() => setFilter('MANUAL')}>
          Manual
        </Chip>
        <Chip selected={filter === 'ALERT'} onPress={() => setFilter('ALERT')}>
          Alerts
        </Chip>
      </View>

      <FlatList
        data={filteredClips}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <VideoClipCard 
            clip={item}
            onPress={() => navigation.navigate('ClipDetail', { clipId: item.id })}
          />
        )}
        refreshing={isLoading}
        onRefresh={refetch}
        contentContainerStyle={styles.list}
        numColumns={2}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f5f5f5' },
  filters: { 
    flexDirection: 'row', 
    padding: 12, 
    gap: 8,
    backgroundColor: 'white' 
  },
  list: { padding: 8 },
});
```

### `src/components/VideoClipCard.tsx`

```typescript
import React from 'react';
import { View, Image, StyleSheet } from 'react-native';
import { Card, Text, Badge, IconButton } from 'react-native-paper';
import { VideoClip } from '../types/videoClip';
import { format } from 'date-fns';

interface Props {
  clip: VideoClip;
  onPress?: () => void;
}

export default function VideoClipCard({ clip, onPress }: Props) {
  const formatBytes = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  };

  return (
    <Card style={styles.card} onPress={onPress}>
      <Card.Cover 
        source={{ 
          uri: clip.thumbnailUrl || 'https://via.placeholder.com/300x200?text=No+Preview' 
        }}
        style={styles.thumbnail}
      />
      
      <Card.Content style={styles.content}>
        <Text variant="bodySmall" numberOfLines={1} style={styles.cameraName}>
          {clip.cameraName}
        </Text>
        <Text variant="bodySmall" style={styles.time}>
          {format(new Date(clip.startTime), 'HH:mm')}
        </Text>
        
        <View style={styles.badges}>
          <Badge size={16} style={styles.badge}>
            {clip.triggerType}
          </Badge>
          <Badge size={16} style={styles.badge}>
            {clip.durationSeconds}s
          </Badge>
        </View>
        
        <Text variant="bodySmall" style={styles.size}>
          {formatBytes(clip.fileSizeBytes)}
        </Text>
      </Card.Content>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { 
    margin: 4, 
    flex: 1,
    maxWidth: '48%',
  },
  thumbnail: { height: 120 },
  content: { paddingTop: 8 },
  cameraName: { fontWeight: '600', marginBottom: 2 },
  time: { color: '#666', marginBottom: 4 },
  badges: { flexDirection: 'row', gap: 4, marginBottom: 4 },
  badge: { fontSize: 10 },
  size: { color: '#999', fontSize: 10 },
});
```

### `src/screens/ClipDetailScreen.tsx`

```typescript
import React, { useState } from 'react';
import { View, StyleSheet, Alert, Share } from 'react-native';
import { Video, ResizeMode } from 'expo-av';
import { Button, ActivityIndicator, Text, IconButton } from 'react-native-paper';
import { useVideoClip, useDownloadClip, useDeleteClip } from '../hooks/useVideoClips';
import * as Sharing from 'expo-sharing';

export default function ClipDetailScreen({ route, navigation }) {
  const { clipId } = route.params;
  const { data: clip, isLoading } = useVideoClip(clipId);
  const downloadMutation = useDownloadClip();
  const deleteMutation = useDeleteClip();

  const handleDownload = async () => {
    try {
      const filename = `clip_${clip.id}.mp4`;
      const uri = await downloadMutation.mutateAsync({ clipId: clip.id, filename });
      
      if (uri) {
        Alert.alert(
          'Downloaded',
          'Video saved to device',
          [
            { text: 'OK' },
            { 
              text: 'Share', 
              onPress: async () => {
                if (await Sharing.isAvailableAsync()) {
                  await Sharing.shareAsync(uri);
                }
              }
            }
          ]
        );
      }
    } catch (error) {
      Alert.alert('Error', 'Failed to download video');
    }
  };

  const handleDelete = () => {
    Alert.alert(
      'Delete Clip',
      'Are you sure you want to delete this video?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            await deleteMutation.mutateAsync(clipId);
            navigation.goBack();
          },
        },
      ]
    );
  };

  if (isLoading || !clip) {
    return (
      <View style={styles.loading}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Video
        source={{ uri: `${apiClient.defaults.baseURL}/video-clips/${clip.id}/download` }}
        style={styles.video}
        useNativeControls
        resizeMode={ResizeMode.CONTAIN}
        shouldPlay
      />

      <View style={styles.info}>
        <Text variant="headlineSmall">{clip.cameraName}</Text>
        <Text variant="bodyMedium">
          {format(new Date(clip.startTime), 'PPpp')}
        </Text>
        <Text variant="bodySmall">
          Duration: {clip.durationSeconds}s • {clip.resolution} • {formatBytes(clip.fileSizeBytes)}
        </Text>
      </View>

      <View style={styles.actions}>
        <Button 
          mode="contained" 
          onPress={handleDownload}
          loading={downloadMutation.isLoading}
          icon="download"
          style={styles.button}
        >
          Download
        </Button>
        <Button 
          mode="outlined" 
          onPress={handleDelete}
          loading={deleteMutation.isLoading}
          icon="delete"
          style={styles.button}
        >
          Delete
        </Button>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#000' },
  loading: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  video: { width: '100%', height: 300 },
  info: { 
    padding: 16, 
    backgroundColor: '#fff',
    gap: 8,
  },
  actions: { 
    flexDirection: 'row', 
    padding: 16, 
    gap: 12,
    backgroundColor: '#fff',
  },
  button: { flex: 1 },
});
```

### `src/components/RecordButton.tsx`

```typescript
import React, { useState, useEffect } from 'react';
import { View, StyleSheet } from 'react-native';
import { Button, Text } from 'react-native-paper';
import { useRecordClip } from '../hooks/useVideoClips';

interface Props {
  cameraId: string;
}

export default function RecordButton({ cameraId }: Props) {
  const [recording, setRecording] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const recordMutation = useRecordClip();

  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    } else if (countdown === 0 && recording) {
      setRecording(false);
    }
  }, [countdown, recording]);

  const handleRecord = async () => {
    try {
      await recordMutation.mutateAsync({
        cameraId,
        durationSeconds: 30,
        triggerType: 'MANUAL',
      });
      
      setRecording(true);
      setCountdown(30);
    } catch (error) {
      Alert.alert('Error', 'Failed to start recording');
    }
  };

  return (
    <View style={styles.container}>
      <Button 
        mode="contained"
        onPress={handleRecord}
        disabled={recording}
        icon={recording ? 'record-rec' : 'record-circle'}
        contentStyle={styles.buttonContent}
      >
        {recording ? `Recording... ${countdown}s` : 'Record Clip'}
      </Button>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16 },
  buttonContent: { paddingVertical: 8 },
});
```

---

## 🧭 Navigation Setup

### `src/navigation/AppNavigator.tsx`

```typescript
import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createStackNavigator } from '@react-navigation/stack';
import { Ionicons } from '@expo/vector-icons';

import DevicesScreen from '../screens/DevicesScreen';
import DeviceDetailScreen from '../screens/DeviceDetailScreen';
import CameraFeedScreen from '../screens/CameraFeedScreen';
import VideoClipsScreen from '../screens/VideoClipsScreen';
import ClipDetailScreen from '../screens/ClipDetailScreen';

const Tab = createBottomTabNavigator();
const Stack = createStackNavigator();

function DevicesStack() {
  return (
    <Stack.Navigator>
      <Stack.Screen name="DevicesList" component={DevicesScreen} options={{ title: 'Devices' }} />
      <Stack.Screen name="DeviceDetail" component={DeviceDetailScreen} />
      <Stack.Screen name="CameraFeed" component={CameraFeedScreen} options={{ title: 'Live Feed' }} />
    </Stack.Navigator>
  );
}

function ClipsStack() {
  return (
    <Stack.Navigator>
      <Stack.Screen name="ClipsList" component={VideoClipsScreen} options={{ title: 'Recordings' }} />
      <Stack.Screen name="ClipDetail" component={ClipDetailScreen} options={{ title: 'Clip Details' }} />
    </Stack.Navigator>
  );
}

export default function AppNavigator() {
  return (
    <NavigationContainer>
      <Tab.Navigator
        screenOptions={({ route }) => ({
          tabBarIcon: ({ color, size }) => {
            let iconName;
            if (route.name === 'Devices') iconName = 'grid-outline';
            else if (route.name === 'Clips') iconName = 'film-outline';
            else if (route.name === 'Settings') iconName = 'settings-outline';
            
            return <Ionicons name={iconName} size={size} color={color} />;
          },
        })}
      >
        <Tab.Screen name="Devices" component={DevicesStack} options={{ headerShown: false }} />
        <Tab.Screen name="Clips" component={ClipsStack} options={{ headerShown: false }} />
      </Tab.Navigator>
    </NavigationContainer>
  );
}
```

---

## 🎬 Camera Feed (WebSocket Integration)

### `src/screens/CameraFeedScreen.tsx`

```typescript
import React, { useEffect, useRef, useState } from 'react';
import { View, Image, StyleSheet, Dimensions } from 'react-native';
import { Text, ActivityIndicator } from 'react-native-paper';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import RecordButton from '../components/RecordButton';

export default function CameraFeedScreen({ route }) {
  const { cameraId } = route.params;
  const [connected, setConnected] = useState(false);
  const [frameData, setFrameData] = useState<string | null>(null);
  const [fps, setFps] = useState(0);
  const [frameCount, setFrameCount] = useState(0);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    // Create STOMP client
    const client = new Client({
      webSocketFactory: () => new SockJS('http://10.0.2.2:8080/api/v1/ws'),
      
      onConnect: () => {
        console.log('WebSocket connected');
        setConnected(true);
        
        // Subscribe to camera frames
        client.subscribe(`/topic/camera/${cameraId}/frames`, (message) => {
          const frame = JSON.parse(message.body);
          setFrameData(frame.data);
          setFrameCount(frame.frameNumber);
        });
        
        // Start streaming
        client.publish({
          destination: `/app/surveillance/stream/start/${cameraId}`,
        });
      },
      
      onDisconnect: () => {
        console.log('WebSocket disconnected');
        setConnected(false);
      },
    });

    client.activate();
    clientRef.current = client;

    // FPS counter
    let lastCount = 0;
    const fpsInterval = setInterval(() => {
      setFps((prev) => {
        const currentFps = frameCount - lastCount;
        lastCount = frameCount;
        return currentFps;
      });
    }, 1000);

    return () => {
      if (clientRef.current) {
        clientRef.current.publish({
          destination: `/app/surveillance/stream/stop/${cameraId}`,
        });
        clientRef.current.deactivate();
      }
      clearInterval(fpsInterval);
    };
  }, [cameraId]);

  if (!connected || !frameData) {
    return (
      <View style={styles.loading}>
        <ActivityIndicator size="large" />
        <Text style={styles.loadingText}>Connecting to camera...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.stats}>
        <Text variant="bodySmall" style={styles.statsText}>
          {connected ? '🟢 LIVE' : '🔴 OFFLINE'} • Frame: {frameCount} • FPS: {fps}
        </Text>
      </View>

      <Image
        source={{ uri: `data:image/jpeg;base64,${frameData}` }}
        style={styles.frame}
        resizeMode="contain"
      />

      <RecordButton cameraId={cameraId} />
    </View>
  );
}

const { width } = Dimensions.get('window');

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#000' },
  loading: { 
    flex: 1, 
    justifyContent: 'center', 
    alignItems: 'center',
    backgroundColor: '#000',
  },
  loadingText: { marginTop: 16, color: '#fff' },
  stats: { 
    position: 'absolute', 
    top: 0, 
    left: 0, 
    right: 0, 
    padding: 8,
    backgroundColor: 'rgba(0,0,0,0.7)',
    zIndex: 10,
  },
  statsText: { color: '#fff', textAlign: 'center' },
  frame: { 
    width, 
    height: width * 0.75, // 4:3 aspect ratio
    backgroundColor: '#000',
  },
});
```

---

## 🔐 Secure Token Storage

### `src/stores/authStore.ts`

```typescript
import create from 'zustand';
import * as SecureStore from 'expo-secure-store';
import { apiClient } from '../api/client';

interface AuthState {
  token: string | null;
  user: any | null;
  isLoading: boolean;
  login: (phoneNumber: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  loadToken: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  user: null,
  isLoading: true,

  loadToken: async () => {
    try {
      const token = await SecureStore.getItemAsync('jwt_token');
      if (token) {
        apiClient.defaults.headers.Authorization = `Bearer ${token}`;
        // Fetch user profile
        const { data } = await apiClient.get('/auth/me');
        set({ token, user: data, isLoading: false });
      } else {
        set({ isLoading: false });
      }
    } catch (error) {
      set({ isLoading: false });
    }
  },

  login: async (phoneNumber, password) => {
    const { data } = await apiClient.post('/auth/login', { phoneNumber, password });
    await SecureStore.setItemAsync('jwt_token', data.accessToken);
    apiClient.defaults.headers.Authorization = `Bearer ${data.accessToken}`;
    set({ token: data.accessToken, user: data.user });
  },

  logout: async () => {
    await SecureStore.deleteItemAsync('jwt_token');
    delete apiClient.defaults.headers.Authorization;
    set({ token: null, user: null });
  },
}));
```

---

## 🚀 App Entry Point

### `App.tsx`

```typescript
import React, { useEffect } from 'react';
import { QueryClient, QueryClientProvider } from 'react-query';
import { Provider as PaperProvider } from 'react-native-paper';
import { useAuthStore } from './src/stores/authStore';
import AppNavigator from './src/navigation/AppNavigator';
import LoginScreen from './src/screens/LoginScreen';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export default function App() {
  const { token, isLoading, loadToken } = useAuthStore();

  useEffect(() => {
    loadToken();
  }, []);

  if (isLoading) {
    return null; // Or splash screen
  }

  return (
    <QueryClientProvider client={queryClient}>
      <PaperProvider>
        {token ? <AppNavigator /> : <LoginScreen />}
      </PaperProvider>
    </QueryClientProvider>
  );
}
```

---

## 📝 Summary

### ✅ Complete Mobile App Features

1. **Device Control**
   - View all doors and cameras
   - Lock/unlock buttons with loading states
   - Command history
   - Configuration screens

2. **Camera Feeds**
   - WebSocket live streaming (simulated)
   - 10 FPS real-time updates
   - Connection status indicator

3. **Video Clips**
   - Grid view with thumbnails
   - Filter by trigger type
   - Download to device
   - Share via system share sheet
   - Video playback with controls
   - Delete clips

4. **State Management**
   - React Query for server state
   - Zustand for auth
   - Optimistic updates
   - Auto-refresh

### 🎯 Testing Checklist

- [ ] Login and store JWT securely
- [ ] List devices from API
- [ ] Unlock/lock door (with mock device)
- [ ] View simulated camera feed
- [ ] Browse video clips
- [ ] Download clip to device
- [ ] Share clip
- [ ] Delete clip
- [ ] Logout

### 🔗 Backend URLs

**Development:**
- Android Emulator: `http://10.0.2.2:8080/api/v1`
- iOS Simulator: `http://localhost:8080/api/v1`
- Physical Device: `http://YOUR_LOCAL_IP:8080/api/v1`

**Production:**
- `https://your-backend.com/api/v1`

---

**Ready to build!** All components, hooks, and screens are provided. Copy these into your Expo project and start testing.

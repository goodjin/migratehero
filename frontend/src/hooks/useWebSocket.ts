import { useEffect, useRef, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { MigrationProgress } from '../types';

export function useWebSocket(
  jobId: number | null,
  onProgress: (progress: MigrationProgress) => void
) {
  const clientRef = useRef<Client | null>(null);

  const connect = useCallback(() => {
    if (!jobId) return;

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    client.onConnect = () => {
      console.log('WebSocket connected');

      // Subscribe to progress updates
      client.subscribe(`/topic/migration/${jobId}/progress`, (message) => {
        try {
          const progress = JSON.parse(message.body) as MigrationProgress;
          onProgress(progress);
        } catch (e) {
          console.error('Failed to parse progress message', e);
        }
      });

      // Subscribe to status changes
      client.subscribe(`/topic/migration/${jobId}/status`, (message) => {
        console.log('Status update:', message.body);
      });
    };

    client.onStompError = (frame) => {
      console.error('STOMP error:', frame.headers['message']);
    };

    client.activate();
    clientRef.current = client;
  }, [jobId, onProgress]);

  useEffect(() => {
    connect();

    return () => {
      if (clientRef.current) {
        clientRef.current.deactivate();
        clientRef.current = null;
      }
    };
  }, [connect]);

  return clientRef.current;
}

package com.realtimeeditor.socket;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SocketHandler {

    private final SocketIOServer server;

    // Map to store socketId -> username
    private final Map<String, String> userSocketMap = new ConcurrentHashMap<>();

    @Autowired
    public SocketHandler(SocketIOServer server) {
        this.server = server;
    }

    @PostConstruct
    public void init() {
        server.addConnectListener(onConnected());
        server.addDisconnectListener(onDisconnected());
        server.addEventListener("join", JoinRequest.class, onJoin());
        server.addEventListener("code-change", CodeChangeRequest.class, onCodeChange());
        server.addEventListener("sync-code", SyncCodeRequest.class, onSyncCode());
        server.start();
        log.info("Socket.IO server started on port {}", server.getConfiguration().getPort());
    }

    @PreDestroy
    public void stop() {
        server.stop();
    }

    private ConnectListener onConnected() {
        return client -> log.info("Socket connected: {}", client.getSessionId().toString());
    }

    private DisconnectListener onDisconnected() {
        return client -> {
            String socketId = client.getSessionId().toString();
            String username = userSocketMap.remove(socketId);
            if (username != null) {
                for (String room : client.getAllRooms()) {
                    if (!room.equals(socketId)) {
                        // Notify others in room
                        client.getNamespace().getRoomOperations(room).sendEvent("disconnected", new DisconnectResponse(socketId, username));
                    }
                }
            }
            log.info("Socket disconnected: {}", socketId);
        };
    }

    private DataListener<JoinRequest> onJoin() {
        return (client, data, ackSender) -> {
            String socketId = client.getSessionId().toString();
            userSocketMap.put(socketId, data.getUsername());
            client.joinRoom(data.getRoomId());

            List<ClientInfo> clients = getAllConnectedClients(data.getRoomId());

            // Emit to all clients in the room
            server.getRoomOperations(data.getRoomId()).sendEvent("joined", new JoinedResponse(clients, data.getUsername(), socketId));
        };
    }

    private DataListener<CodeChangeRequest> onCodeChange() {
        return (client, data, ackSender) -> {
            // Broadcast to others in room
            String roomId = data.getRoomId();
            for (SocketIOClient otherClient : server.getRoomOperations(roomId).getClients()) {
                if (!otherClient.getSessionId().equals(client.getSessionId())) {
                    otherClient.sendEvent("code-change", data);
                }
            }
        };
    }

    private DataListener<SyncCodeRequest> onSyncCode() {
        return (client, data, ackSender) -> {
            SocketIOClient targetClient = server.getClient(java.util.UUID.fromString(data.getSocketId()));
            if (targetClient != null) {
                targetClient.sendEvent("code-change", new CodeChangeRequest(null, data.getCode()));
            }
        };
    }

    private List<ClientInfo> getAllConnectedClients(String roomId) {
        List<ClientInfo> clients = new ArrayList<>();
        for (SocketIOClient client : server.getRoomOperations(roomId).getClients()) {
            String socketId = client.getSessionId().toString();
            String username = userSocketMap.get(socketId);
            if (username != null) {
                clients.add(new ClientInfo(socketId, username));
            }
        }
        return clients;
    }

    // DTOs for Socket.IO Events
    @Data
    public static class JoinRequest {
        private String roomId;
        private String username;
    }

    @Data
    public static class JoinedResponse {
        private final List<ClientInfo> clients;
        private final String username;
        private final String socketId;
    }

    @Data
    public static class CodeChangeRequest {
        private String roomId;
        private String code;
        public CodeChangeRequest() {}
        public CodeChangeRequest(String roomId, String code) {
            this.roomId = roomId;
            this.code = code;
        }
    }

    @Data
    public static class SyncCodeRequest {
        private String socketId;
        private String code;
    }

    @Data
    public static class DisconnectResponse {
        private final String socketId;
        private final String username;
    }

    @Data
    public static class ClientInfo {
        private final String socketId;
        private final String username;
    }
}

package com.remote.history.dto;

import java.time.Instant;
import java.util.UUID;

public class HistoryResponse {

    private Long id;
    private UUID sessionId;
    private String username;
    private String pcName;
    private String clientIp;
    private String clientInfo;
    private String mode;
    private String profile;
    private Instant connectedAt;
    private Instant disconnectedAt;
    private Integer durationSeconds;
    private Double avgFps;
    private Double avgLatency;
    private Integer filesSent;
    private String issues;

    public HistoryResponse(
            Long id,
            UUID sessionId,
            String username,
            String pcName,
            String clientIp,
            String clientInfo,
            String mode,
            String profile,
            Instant connectedAt,
            Instant disconnectedAt,
            Integer durationSeconds,
            Double avgFps,
            Double avgLatency,
            Integer filesSent,
            String issues
    ) {
        this.id =
                id;

        this.sessionId =
                sessionId;

        this.username =
                username;

        this.pcName =
                pcName;

        this.clientIp =
                clientIp;

        this.clientInfo =
                clientInfo;

        this.mode =
                mode;

        this.profile =
                profile;

        this.connectedAt =
                connectedAt;

        this.disconnectedAt =
                disconnectedAt;

        this.durationSeconds =
                durationSeconds;

        this.avgFps =
                avgFps;

        this.avgLatency =
                avgLatency;

        this.filesSent =
                filesSent;

        this.issues =
                issues;
    }

    public Long getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getUsername() {
        return username;
    }

    public String getPcName() {
        return pcName;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getClientInfo() {
        return clientInfo;
    }

    public String getMode() {
        return mode;
    }

    public String getProfile() {
        return profile;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public Instant getDisconnectedAt() {
        return disconnectedAt;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public Double getAvgFps() {
        return avgFps;
    }

    public Double getAvgLatency() {
        return avgLatency;
    }

    public Integer getFilesSent() {
        return filesSent;
    }

    public String getIssues() {
        return issues;
    }
}
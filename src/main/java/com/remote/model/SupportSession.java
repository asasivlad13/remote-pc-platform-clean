package com.remote.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "support_sessions")
public class SupportSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_code", nullable = false, unique = true, length = 6)
    private String sessionCode;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id", nullable = false)
    private User operator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private User client;

    /*
     * ВАЖНО:
     * clientPc теперь НЕ задаётся оператором при создании сессии.
     * Он определяется автоматически, когда клиент входит по коду со своего ПК.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_pc_id")
    private Pc clientPc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportSessionStatus status = SupportSessionStatus.WAITING_CLIENT;

    @Column(name = "control_requested", nullable = false)
    private boolean controlRequested = false;

    @Column(name = "control_allowed", nullable = false)
    private boolean controlAllowed = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "control_requested_at")
    private LocalDateTime controlRequestedAt;

    @Column(name = "control_allowed_at")
    private LocalDateTime controlAllowedAt;

    public SupportSession() {
    }

    public Long getId() {
        return id;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public User getOperator() {
        return operator;
    }

    public void setOperator(User operator) {
        this.operator = operator;
    }

    public User getClient() {
        return client;
    }

    public void setClient(User client) {
        this.client = client;
    }

    public Pc getClientPc() {
        return clientPc;
    }

    public void setClientPc(Pc clientPc) {
        this.clientPc = clientPc;
    }

    public SupportSessionStatus getStatus() {
        return status;
    }

    public void setStatus(SupportSessionStatus status) {
        this.status = status;
    }

    public boolean isControlRequested() {
        return controlRequested;
    }

    public void setControlRequested(boolean controlRequested) {
        this.controlRequested = controlRequested;
    }

    public boolean isControlAllowed() {
        return controlAllowed;
    }

    public void setControlAllowed(boolean controlAllowed) {
        this.controlAllowed = controlAllowed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public LocalDateTime getControlRequestedAt() {
        return controlRequestedAt;
    }

    public void setControlRequestedAt(LocalDateTime controlRequestedAt) {
        this.controlRequestedAt = controlRequestedAt;
    }

    public LocalDateTime getControlAllowedAt() {
        return controlAllowedAt;
    }

    public void setControlAllowedAt(LocalDateTime controlAllowedAt) {
        this.controlAllowedAt = controlAllowedAt;
    }

    public void finish() {
        this.status = SupportSessionStatus.FINISHED;
        this.finishedAt = LocalDateTime.now();
        this.controlRequested = false;
        this.controlAllowed = false;
        this.controlRequestedAt = null;
        this.controlAllowedAt = null;
    }

    public void cancel() {
        this.status = SupportSessionStatus.CANCELLED;
        this.finishedAt = LocalDateTime.now();
        this.controlRequested = false;
        this.controlAllowed = false;
        this.controlRequestedAt = null;
        this.controlAllowedAt = null;
    }
}

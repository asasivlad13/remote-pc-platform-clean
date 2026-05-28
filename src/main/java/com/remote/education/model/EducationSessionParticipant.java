package com.remote.education.model;

import com.remote.core.model.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "education_session_participants")
public class EducationSessionParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "education_session_id", nullable = false)
    private EducationSession educationSession;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EducationParticipantStatus status = EducationParticipantStatus.WAITING;

    @Column(nullable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();

    private LocalDateTime approvedAt;

    @Column(nullable = false)
    private boolean controlRequested = false;

    @Column(nullable = false)
    private boolean hasControl = false;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    private LocalDateTime controlRequestedAt;

    private LocalDateTime controlGrantedAt;

    @Column(nullable = false)
    private boolean screenShareRequested = false;

    @Column(nullable = false)
    private boolean screenShareActive = false;

    private LocalDateTime screenShareRequestedAt;

    private LocalDateTime screenShareStartedAt;

    public Long getId() { return id; }

    public EducationSession getEducationSession() { return educationSession; }

    public void setEducationSession(EducationSession educationSession) { this.educationSession = educationSession; }

    public User getStudent() { return student; }

    public void setStudent(User student) { this.student = student; }

    public String getDisplayName() { return displayName; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public EducationParticipantStatus getStatus() { return status; }

    public void setStatus(EducationParticipantStatus status) { this.status = status; }

    public LocalDateTime getJoinedAt() { return joinedAt; }

    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }

    public LocalDateTime getApprovedAt() { return approvedAt; }

    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public boolean isControlRequested() { return controlRequested; }

    public void setControlRequested(boolean controlRequested) { this.controlRequested = controlRequested; }

    public boolean isHasControl() { return hasControl; }

    public void setHasControl(boolean hasControl) { this.hasControl = hasControl; }

    public LocalDateTime getControlRequestedAt() { return controlRequestedAt; }

    public void setControlRequestedAt(LocalDateTime controlRequestedAt) { this.controlRequestedAt = controlRequestedAt; }

    public LocalDateTime getControlGrantedAt() { return controlGrantedAt; }

    public void setControlGrantedAt(LocalDateTime controlGrantedAt) { this.controlGrantedAt = controlGrantedAt; }

    public LocalDateTime getLastActivityAt() { return lastActivityAt; }

    public void setLastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }

    public boolean isScreenShareRequested() { return screenShareRequested; }

    public void setScreenShareRequested(boolean screenShareRequested) { this.screenShareRequested = screenShareRequested; }

    public boolean isScreenShareActive() { return screenShareActive; }

    public void setScreenShareActive(boolean screenShareActive) { this.screenShareActive = screenShareActive; }

    public LocalDateTime getScreenShareRequestedAt() { return screenShareRequestedAt; }

    public void setScreenShareRequestedAt(LocalDateTime screenShareRequestedAt) { this.screenShareRequestedAt = screenShareRequestedAt; }

    public LocalDateTime getScreenShareStartedAt() { return screenShareStartedAt; }

    public void setScreenShareStartedAt(LocalDateTime screenShareStartedAt) { this.screenShareStartedAt = screenShareStartedAt; }
}

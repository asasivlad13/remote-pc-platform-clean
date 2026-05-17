package com.remote.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "education_sessions")
public class EducationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_code", nullable = false, unique = true, length = 6)
    private String sessionCode;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_pc_id", nullable = false)
    private Pc teacherPc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EducationSessionStatus status = EducationSessionStatus.ACTIVE;

    @Column(name = "max_students", nullable = false)
    private Integer maxStudents = 30;

    @Column(name = "allow_student_control", nullable = false)
    private Boolean allowStudentControl = false;

    @Column(name = "allow_file_transfer", nullable = false)
    private Boolean allowFileTransfer = false;

    @Column(name = "allow_student_screen_share", nullable = false)
    private Boolean allowStudentScreenShare = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public EducationSession() {
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

    public User getTeacher() {
        return teacher;
    }

    public void setTeacher(User teacher) {
        this.teacher = teacher;
    }

    public Pc getTeacherPc() {
        return teacherPc;
    }

    public void setTeacherPc(Pc teacherPc) {
        this.teacherPc = teacherPc;
    }

    public EducationSessionStatus getStatus() {
        return status;
    }

    public void setStatus(EducationSessionStatus status) {
        this.status = status;
    }

    public Integer getMaxStudents() {
        return maxStudents;
    }

    public void setMaxStudents(Integer maxStudents) {
        this.maxStudents = maxStudents;
    }

    public Boolean getAllowStudentControl() {
        return allowStudentControl;
    }

    public void setAllowStudentControl(Boolean allowStudentControl) {
        this.allowStudentControl = allowStudentControl;
    }

    public Boolean getAllowFileTransfer() {
        return allowFileTransfer;
    }

    public void setAllowFileTransfer(Boolean allowFileTransfer) {
        this.allowFileTransfer = allowFileTransfer;
    }

    public Boolean getAllowStudentScreenShare() {
        return allowStudentScreenShare;
    }

    public void setAllowStudentScreenShare(Boolean allowStudentScreenShare) {
        this.allowStudentScreenShare = allowStudentScreenShare;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void finish() {
        this.status = EducationSessionStatus.FINISHED;
        this.finishedAt = LocalDateTime.now();
    }
}
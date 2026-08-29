package com.remote.education.model;

import com.remote.core.model.User;
import com.remote.pc.model.Pc;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(
        name = "education_sessions",
        indexes = {
                @Index(name = "idx_education_sessions_teacher", columnList = "teacher_id"),
                @Index(name = "idx_education_sessions_teacher_pc", columnList = "teacher_pc_id"),
                @Index(name = "idx_education_sessions_status", columnList = "status"),
                @Index(name = "idx_education_sessions_created_at", columnList = "created_at")
        }
)
public class EducationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Pattern(regexp = "\\d{6}")
    @Column(name = "session_code", nullable = false, unique = true, length = 6)
    private String sessionCode;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String title;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @Size(max = 100)
    @Column(name = "teacher_display_name", length = 100)
    private String teacherDisplayName;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_pc_id", nullable = false)
    private Pc teacherPc;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EducationSessionStatus status = EducationSessionStatus.ACTIVE;

    @NotNull
    @Min(1)
    @Max(100)
    @Column(name = "max_students", nullable = false)
    private Integer maxStudents = 30;

    @NotNull
    @Column(name = "allow_student_control", nullable = false)
    private Boolean allowStudentControl = false;

    @NotNull
    @Column(name = "allow_file_transfer", nullable = false)
    private Boolean allowFileTransfer = false;

    @NotNull
    @Column(name = "allow_student_screen_share", nullable = false)
    private Boolean allowStudentScreenShare = false;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    public void finish() {
        this.status = EducationSessionStatus.FINISHED;
        this.finishedAt = Instant.now();
    }
}

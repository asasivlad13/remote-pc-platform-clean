package com.remote.education.controller;

import com.remote.auth.security.JwtUtil;
import com.remote.education.model.EducationParticipantStatus;
import com.remote.education.model.EducationSession;
import com.remote.education.model.EducationSessionParticipant;
import com.remote.pc.model.Pc;
import com.remote.core.model.User;
import com.remote.education.repository.EducationSessionParticipantRepository;
import com.remote.education.repository.EducationSessionRepository;
import com.remote.pc.repository.PcRepository;
import com.remote.core.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/education/screen-share")
public class EducationScreenShareController {

    private final PcRepository pcRepository;
    private final UserRepository userRepository;
    private final EducationSessionRepository sessionRepository;
    private final EducationSessionParticipantRepository participantRepository;
    private final JwtUtil jwtUtil;

    public EducationScreenShareController(PcRepository pcRepository,
                                          UserRepository userRepository,
                                          EducationSessionRepository sessionRepository,
                                          EducationSessionParticipantRepository participantRepository,
                                          JwtUtil jwtUtil) {
        this.pcRepository = pcRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/my-agent")
    public Map<String, Object> getMyAgent(HttpServletRequest request) {
        String username = extractUsername(request);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        return pcRepository.findByUser(user)
                .stream()
                .findFirst()
                .map(this::toAgentResponse)
                .orElseGet(this::noAgentResponse);
    }

    @GetMapping("/participant/{participantId}")
    public Map<String, Object> getParticipantAgent(@PathVariable Long participantId,
                                                   HttpServletRequest request) {
        String username = extractUsername(request);

        User teacher = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        EducationSessionParticipant participant = participantRepository.findWithDetailsById(participantId)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));

        if (!participant.getEducationSession().getTeacher().getId().equals(teacher.getId())) {
            throw new IllegalArgumentException("Только преподаватель может смотреть экран ученика");
        }

        return pcRepository.findByUser(participant.getStudent())
                .stream()
                .findFirst()
                .map(this::toAgentResponse)
                .orElseGet(this::noAgentResponse);
    }

    @GetMapping("/active/{sessionCode}")
    public Map<String, Object> getActiveScreenShare(@PathVariable String sessionCode,
                                                    HttpServletRequest request) {
        String username = extractUsername(request);

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        EducationSession session = sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new IllegalArgumentException("Учебная сессия не найдена"));

        boolean isTeacher = session.getTeacher().getId().equals(currentUser.getId());
        boolean isApprovedStudent = participantRepository
                .findByEducationSessionAndStudent(session, currentUser)
                .map(p -> p.getStatus() == EducationParticipantStatus.APPROVED)
                .orElse(false);

        if (!isTeacher && !isApprovedStudent) {
            throw new IllegalArgumentException("Нет доступа к демонстрации этой сессии");
        }

        EducationSessionParticipant activeParticipant = participantRepository
                .findByEducationSessionOrderByJoinedAtAsc(session)
                .stream()
                .filter(EducationSessionParticipant::isScreenShareActive)
                .findFirst()
                .orElse(null);

        if (activeParticipant == null) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("active", false);
            response.put("participantId", null);
            response.put("studentId", null);
            response.put("displayName", null);
            response.put("agent", null);
            return response;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("active", true);
        response.put("participantId", activeParticipant.getId());
        response.put("studentId", activeParticipant.getStudent().getId());
        response.put("displayName", activeParticipant.getDisplayName());
        response.put("agent", pcRepository.findByUser(activeParticipant.getStudent())
                .stream()
                .findFirst()
                .map(this::toAgentResponse)
                .orElseGet(this::noAgentResponse));

        return response;
    }

    private Map<String, Object> toAgentResponse(Pc pc) {
        boolean online = pc.getStatus() != null && "ONLINE".equalsIgnoreCase(pc.getStatus().name());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("hasAgent", true);
        response.put("pcId", pc.getId());
        response.put("pcName", pc.getName());
        response.put("status", pc.getStatus() != null ? pc.getStatus().name() : "UNKNOWN");
        response.put("canShareScreen", online);
        response.put("webrtcUrl", pc.getWebrtcUrl());
        response.put("streamName", pc.getStreamName());
        response.put("screenWidth", pc.getScreenWidth());
        response.put("screenHeight", pc.getScreenHeight());
        return response;
    }

    private Map<String, Object> noAgentResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("hasAgent", false);
        response.put("pcId", null);
        response.put("pcName", null);
        response.put("status", "NO_AGENT");
        response.put("canShareScreen", false);
        response.put("webrtcUrl", null);
        response.put("streamName", null);
        response.put("screenWidth", null);
        response.put("screenHeight", null);
        return response;
    }

    private String extractUsername(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization header is missing");
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.validateToken(token)) {
            throw new IllegalArgumentException("Invalid JWT token");
        }

        return jwtUtil.extractUsername(token);
    }
}

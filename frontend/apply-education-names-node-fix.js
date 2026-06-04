const fs = require("fs");
const path = require("path");

const frontendRoot = __dirname;
const projectRoot = path.dirname(frontendRoot);

function log(message) {
    console.log(`[education-names-node-fix] ${message}`);
}

function exists(filePath) {
    return fs.existsSync(filePath);
}

function read(filePath) {
    return fs.readFileSync(filePath, "utf8");
}

function write(filePath, text) {
    fs.writeFileSync(filePath, text, "utf8");
}

function firstExisting(paths) {
    return paths.find((item) => fs.existsSync(item));
}

function replaceOnce(text, pattern, replacement, label) {
    if (typeof pattern === "string") {
        if (!text.includes(pattern)) {
            log(`${label}: pattern not found`);
            return text;
        }

        log(`${label}: ok`);
        return text.replace(pattern, replacement);
    }

    if (!pattern.test(text)) {
        log(`${label}: pattern not found`);
        return text;
    }

    log(`${label}: ok`);
    return text.replace(pattern, replacement);
}

function ensureImport(text, importLine, alreadyRegex, insertRegex, label) {
    if (alreadyRegex.test(text)) {
        log(`${label}: import already exists`);
        return text;
    }

    if (insertRegex.test(text)) {
        log(`${label}: import added near existing imports`);
        return text.replace(insertRegex, (match) => `${match}\n${importLine}`);
    }

    log(`${label}: import added at top`);
    return `${importLine}\n${text}`;
}

// ---------------- FRONTEND ----------------

const educationApiPath = path.join(frontendRoot, "src", "features", "education", "educationApi.ts");
const educationTypesPath = path.join(frontendRoot, "src", "features", "education", "educationTypes.ts");

if (exists(educationApiPath)) {
    let text = read(educationApiPath);

    text = text.replace(
        'import { getUserDisplayName } from "./profile/userDisplayName";',
        'import { getUserDisplayName } from "../profile/userDisplayName";'
    );

    text = ensureImport(
        text,
        'import { getUserDisplayName } from "../profile/userDisplayName";',
        /getUserDisplayName/,
        /^import .*;$/m,
        "educationApi.ts"
    );

    if (!/teacherDisplayName\s*:\s*getUserDisplayName/.test(text)) {
        text = replaceOnce(
            text,
            /(\bteacherPcId\s*,)/,
            'teacherDisplayName: getUserDisplayName("Teacher"),\n            $1',
            "educationApi.ts teacherDisplayName"
        );
    } else {
        log("educationApi.ts teacherDisplayName: already exists");
    }

    text = text.replace(
        /const\s+username\s*=\s*localStorage\.getItem\("username"\)\s*\|\|\s*"[^"]*";/g,
        'const displayName = getUserDisplayName("Student");'
    );

    text = text.replace(/displayName\s*:\s*username/g, "displayName");

    write(educationApiPath, text);
    log("educationApi.ts saved");
} else {
    log("educationApi.ts not found");
}

if (exists(educationTypesPath)) {
    let text = read(educationTypesPath);

    if (!/teacherDisplayName\??\s*:/.test(text)) {
        text = text.replace(
            /(teacherUsername\??\s*:\s*[^;\n]+;)/,
            "$1\n    teacherDisplayName?: string | null;"
        );
        log("educationTypes.ts teacherDisplayName added");
    }

    if (!/senderDisplayName\??\s*:/.test(text)) {
        text = text.replace(
            /(senderUsername\??\s*:\s*[^;\n]+;)/,
            "$1\n    senderDisplayName?: string | null;"
        );
        log("educationTypes.ts senderDisplayName added");
    }

    if (!/recipientDisplayName\??\s*:/.test(text)) {
        text = text.replace(
            /(recipientUsername\??\s*:\s*[^;\n]+;)/,
            "$1\n    recipientDisplayName?: string | null;"
        );
        log("educationTypes.ts recipientDisplayName added");
    }

    write(educationTypesPath, text);
    log("educationTypes.ts saved");
} else {
    log("educationTypes.ts not found");
}

// ---------------- BACKEND ----------------

const backendRoots = [
    projectRoot,
    path.join(projectRoot, "backend"),
];

function backendFile(relativePath) {
    return firstExisting(backendRoots.map((root) => path.join(root, relativePath)));
}

const files = {
    createRequest: backendFile(path.join("src", "main", "java", "com", "remote", "education", "dto", "CreateEducationSessionRequest.java")),
    sessionResponse: backendFile(path.join("src", "main", "java", "com", "remote", "education", "dto", "EducationSessionResponse.java")),
    sessionEntity: backendFile(path.join("src", "main", "java", "com", "remote", "education", "model", "EducationSession.java")),
    sessionController: backendFile(path.join("src", "main", "java", "com", "remote", "education", "controller", "EducationSessionController.java")),
    sessionService: backendFile(path.join("src", "main", "java", "com", "remote", "education", "service", "EducationSessionService.java")),
    chatService: backendFile(path.join("src", "main", "java", "com", "remote", "education", "service", "EducationChatService.java")),
};

if (files.createRequest) {
    let text = read(files.createRequest);

    if (!/teacherDisplayName/.test(text)) {
        text = text.replace(
            /String\s+title\s*,/,
            "String title,\n\n        @Size(max = 100)\n        String teacherDisplayName,"
        );
        log("CreateEducationSessionRequest.java teacherDisplayName added");
    }

    write(files.createRequest, text);
} else {
    log("CreateEducationSessionRequest.java not found");
}

if (files.sessionEntity) {
    let text = read(files.sessionEntity);

    if (!/teacherDisplayName/.test(text)) {
        text = text.replace(
            /(\s*)@Column\(name = "finished_at"\)/,
            '$1@Size(max = 100)\n$1@Column(name = "teacher_display_name", length = 100)\n$1private String teacherDisplayName;\n\n$1@Column(name = "finished_at")'
        );
        log("EducationSession.java teacherDisplayName field added");
    }

    write(files.sessionEntity, text);
} else {
    log("EducationSession.java not found");
}

if (files.sessionResponse) {
    let text = read(files.sessionResponse);

    if (!/teacherDisplayName/.test(text)) {
        text = text.replace(
            /String\s+teacherUsername\s*,/,
            "String teacherUsername,\n        String teacherDisplayName,"
        );
        log("EducationSessionResponse.java teacherDisplayName added");
    }

    write(files.sessionResponse, text);
} else {
    log("EducationSessionResponse.java not found");
}

if (files.sessionController) {
    let text = read(files.sessionController);

    if (!/request\.teacherDisplayName\(\)/.test(text)) {
        text = text.replace(
            /request\.title\(\),\s*request\.maxStudents\(\),/,
            "request.title(),\n                request.teacherDisplayName(),\n                request.maxStudents(),"
        );
        log("EducationSessionController.java teacherDisplayName passed");
    }

    write(files.sessionController, text);
} else {
    log("EducationSessionController.java not found");
}

if (files.sessionService) {
    let text = read(files.sessionService);

    text = text.replace(
        /(createSession\s*\([^)]*String\s+title\s*,)/s,
        "$1\n                                String teacherDisplayName,"
    );

    text = text.replace(
        /(createSessionResponse\s*\([^)]*String\s+title\s*,)/s,
        "$1\n                                        String teacherDisplayName,"
    );

    text = text.replace(/String teacherDisplayName,\s*String teacherDisplayName,/g, "String teacherDisplayName,");

    if (!/setTeacherDisplayName/.test(text)) {
        text = text.replace(
            /session\.setTeacher\(teacher\);/,
            'session.setTeacher(teacher);\n        session.setTeacherDisplayName(resolveDisplayName(teacherDisplayName, username));'
        );
        log("EducationSessionService.java setTeacherDisplayName added");
    }

    text = text.replace(
        /title,\s*maxStudents,/,
        "title,\n                teacherDisplayName,\n                maxStudents,"
    );

    if (!/resolveDisplayName\(session\.getTeacherDisplayName/.test(text)) {
        text = text.replace(
            /session\.getTeacher\(\)\s*!=\s*null\s*\?\s*session\.getTeacher\(\)\.getUsername\(\)\s*:\s*null\s*,/,
            'session.getTeacher() != null ? session.getTeacher().getUsername() : null,\n                resolveDisplayName(session.getTeacherDisplayName(), session.getTeacher() != null ? session.getTeacher().getUsername() : null),'
        );
        log("EducationSessionService.java response teacherDisplayName added");
    }

    if (!/private\s+String\s+resolveDisplayName/.test(text)) {
        const method = `
    private String resolveDisplayName(String displayName, String fallbackUsername) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }

        if (fallbackUsername != null && !fallbackUsername.isBlank()) {
            return fallbackUsername.trim();
        }

        return "User";
    }
`;

        const lastBrace = text.lastIndexOf("}");
        if (lastBrace >= 0) {
            text = `${text.slice(0, lastBrace)}${method}\n${text.slice(lastBrace)}`;
            log("EducationSessionService.java resolveDisplayName added");
        }
    }

    write(files.sessionService, text);
} else {
    log("EducationSessionService.java not found");
}

if (files.chatService) {
    let text = read(files.chatService);

    if (!/EducationSessionParticipantRepository/.test(text)) {
        text = text.replace(
            /import com\.remote\.education\.repository\.EducationSessionRepository;/,
            "import com.remote.education.repository.EducationSessionRepository;\nimport com.remote.education.repository.EducationSessionParticipantRepository;"
        );
        log("EducationChatService.java participant repository import added");
    }

    if (!/participantRepository/.test(text)) {
        text = text.replace(
            /private final EducationSessionRepository sessionRepository;/,
            "private final EducationSessionRepository sessionRepository;\n    private final EducationSessionParticipantRepository participantRepository;"
        );
        log("EducationChatService.java participantRepository field added");
    }

    if (/EducationChatService\s*\([^)]*EducationSessionRepository\s+sessionRepository,\s*UserRepository\s+userRepository,/s.test(text)) {
        text = text.replace(
            /EducationSessionRepository\s+sessionRepository,\s*UserRepository\s+userRepository,/s,
            "EducationSessionRepository sessionRepository,\n                                EducationSessionParticipantRepository participantRepository,\n                                UserRepository userRepository,"
        );

        text = text.replace(
            /this\.sessionRepository\s*=\s*sessionRepository;/,
            "this.sessionRepository = sessionRepository;\n        this.participantRepository = participantRepository;"
        );

        log("EducationChatService.java constructor patched");
    }

    if (!/senderDisplayName/.test(text)) {
        text = text.replace(
            /response\.put\("senderUsername",\s*message\.getSender\(\)\.getUsername\(\)\);/,
            'response.put("senderUsername", message.getSender().getUsername());\n        response.put("senderDisplayName", resolveChatDisplayName(message.getEducationSession(), message.getSender()));'
        );

        text = text.replace(
            /response\.put\("recipientUsername",\s*message\.getRecipient\(\)\s*!=\s*null\s*\?\s*message\.getRecipient\(\)\.getUsername\(\)\s*:\s*null\);/,
            'response.put("recipientUsername", message.getRecipient() != null ? message.getRecipient().getUsername() : null);\n        response.put("recipientDisplayName", message.getRecipient() != null ? resolveChatDisplayName(message.getEducationSession(), message.getRecipient()) : null);'
        );

        log("EducationChatService.java chat display names added");
    }

    if (!/private\s+String\s+resolveChatDisplayName/.test(text)) {
        const method = `
    private String resolveChatDisplayName(EducationSession session, User user) {
        if (session == null || user == null) {
            return "User";
        }

        if (session.getTeacher() != null && session.getTeacher().getId().equals(user.getId())) {
            String teacherDisplayName = session.getTeacherDisplayName();

            if (teacherDisplayName != null && !teacherDisplayName.isBlank()) {
                return teacherDisplayName.trim();
            }

            return user.getUsername();
        }

        return participantRepository.findByEducationSessionAndStudent(session, user)
                .map(participant -> {
                    String displayName = participant.getDisplayName();

                    if (displayName != null && !displayName.isBlank()) {
                        return displayName.trim();
                    }

                    return user.getUsername();
                })
                .orElse(user.getUsername());
    }
`;

        const lastBrace = text.lastIndexOf("}");
        if (lastBrace >= 0) {
            text = `${text.slice(0, lastBrace)}${method}\n${text.slice(lastBrace)}`;
            log("EducationChatService.java resolveChatDisplayName added");
        }
    }

    write(files.chatService, text);
} else {
    log("EducationChatService.java not found");
}

log("Patch finished.");
log("Now run SQL migration and restart backend/frontend.");

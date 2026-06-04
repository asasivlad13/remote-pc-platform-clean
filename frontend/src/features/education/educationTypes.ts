export type EducationParticipantStatus =
    | "ONLINE"
    | "WAITING"
    | "PENDING"
    | "REQUESTED"
    | "APPROVED"
    | "ACCEPTED"
    | "ACTIVE"
    | "CONNECTED"
    | "REJECTED"
    | "DECLINED"
    | "DENIED"
    | "SCREEN_SHARE_REQUESTED"
    | "SCREEN_SHARING"
    | "CONTROL_REQUESTED"
    | "CONTROL_ALLOWED"
    | "CONTROL_GRANTED"
    | "OFFLINE"
    | string;

export type EducationParticipantResponse = {
    id?: number;
    userId?: number;
    studentId?: number;

    username?: string;
    fullName?: string;
    displayName?: string;

    role?: string;
    status?: EducationParticipantStatus;
    online?: boolean;

    controlRequested?: boolean;
    controlAllowed?: boolean;
    hasControl?: boolean;

    screenSharing?: boolean;
    screenShareRequested?: boolean;
    screenSharingRequested?: boolean;
    screenShareActive?: boolean;
};

export type EducationSessionResponse = {
    id?: number;
    sessionCode: string;
    title?: string;
    status?: string;
    startedAt?: string;
    createdAt?: string;
    finishedAt?: string;

    allowStudentControl?: boolean;
    allowFileTransfer?: boolean;
    allowStudentScreenShare?: boolean;

    teacherId?: number;
    teacherUsername?: string;
    teacherFullName?: string;
    teacherDisplayName?: string;

    teacherPcId?: number;
    teacherPcName?: string;
    teacherPcMacAddress?: string;
    teacherPcStatus?: string;
    teacherPcWebrtcUrl?: string;
    teacherPcStreamName?: string;
    teacherPcScreenWidth?: number;
    teacherPcScreenHeight?: number;

    pcId?: number;
    pcName?: string;
    pcMacAddress?: string;
    pcStatus?: string;
    webrtcUrl?: string;
    streamName?: string;
    screenWidth?: number;
    screenHeight?: number;

    participants?: EducationParticipantResponse[];
    students?: EducationParticipantResponse[];
    connectedStudents?: EducationParticipantResponse[];
};

export type EducationEventResponse = {
    id?: number;
    type?: string;
    message?: string;
    actorUsername?: string;
    actorDisplayName?: string;
    createdAt?: string;
};

export type EducationChatMessageResponse = {
    id?: number;

    senderId?: number;
    senderUsername?: string;
    senderFullName?: string;
    senderDisplayName?: string;

    recipientId?: number | null;
    recipientUsername?: string | null;
    recipientFullName?: string | null;
    recipientDisplayName?: string | null;

    username?: string;
    displayName?: string;

    message?: string;
    text?: string;
    createdAt?: string;
};

export type EducationFileResponse = {
    id?: number;
    fileId?: number;
    sessionCode?: string;

    originalFilename?: string;
    filename?: string;
    fileName?: string;

    contentType?: string;
    sizeBytes?: number;

    senderId?: number;
    senderUsername?: string;
    senderFullName?: string;
    senderDisplayName?: string;

    recipientId?: number | null;
    recipientUsername?: string | null;
    recipientFullName?: string | null;
    recipientDisplayName?: string | null;

    createdAt?: string;
};

export type EducationAgentResponse = {
    pcId?: number;
    id?: number;

    pcName?: string;
    name?: string;

    pcMacAddress?: string;
    macAddress?: string;

    pcStatus?: string;
    status?: string;

    webrtcUrl?: string;
    streamName?: string;

    screenWidth?: number;
    screenHeight?: number;

    hasAgent?: boolean;
    canShareScreen?: boolean;
};

export type ActiveScreenShareResponse = {
    active?: boolean;

    participantId?: number;
    participantUsername?: string;
    participantFullName?: string;
    participantDisplayName?: string;

    username?: string;
    fullName?: string;
    displayName?: string;

    agent?: EducationAgentResponse;

    pcId?: number;
    id?: number;

    pcName?: string;
    name?: string;

    pcMacAddress?: string;
    macAddress?: string;

    pcStatus?: string;
    status?: string;

    webrtcUrl?: string;
    streamName?: string;

    screenWidth?: number;
    screenHeight?: number;
};
export type SupportSessionStatus =
    | "WAITING_CLIENT"
    | "ACTIVE"
    | "FINISHED"
    | "CANCELLED"
    | string;

export type SupportSessionResponse = {
    id?: number;
    sessionCode: string;
    title?: string;
    status?: SupportSessionStatus;

    operatorId?: number;
    operatorUsername?: string;

    clientId?: number;
    clientUsername?: string;

    clientPcId?: number;
    clientPcName?: string;
    clientPcMacAddress?: string;
    clientPcStatus?: string;
    clientPcWebrtcUrl?: string;
    clientPcStreamName?: string;
    clientPcScreenWidth?: number;
    clientPcScreenHeight?: number;

    clientScreenWidth?: number;
    clientScreenHeight?: number;
    screenWidth?: number;
    screenHeight?: number;

    controlRequested?: boolean;
    controlAllowed?: boolean;

    createdAt?: string;
    joinedAt?: string;
    finishedAt?: string;
};

export type SupportChatMessageResponse = {
    id?: number;
    senderId?: number;
    senderUsername?: string;
    message?: string;
    mine?: boolean;
    createdAt?: string;
    createdAtText?: string;
};

export type SupportFileResponse = {
    id: number;

    originalFilename?: string;
    filename?: string;
    name?: string;

    contentType?: string;
    sizeBytes?: number;
    sizeText?: string;

    status?: "PENDING" | "ACCEPTED" | "REJECTED" | string;

    senderUsername?: string;
    recipientUsername?: string | null;

    createdAt?: string;
};
export type AuthRequest = {
    username: string;
    password: string;
};

export type AuthTokenResponse = {
    token: string;
};

export type AuthMessageResponse = {
    message: string;
};
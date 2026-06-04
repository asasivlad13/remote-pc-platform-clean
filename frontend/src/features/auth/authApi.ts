import { apiClient } from "../../shared/api/apiClient";
import type { AuthMessageResponse, AuthRequest, AuthTokenResponse } from "./authTypes";

export async function loginRequest(payload: AuthRequest): Promise<AuthTokenResponse> {
    const response = await apiClient.post<AuthTokenResponse>("/auth/login", payload);
    return response.data;
}

export async function registerRequest(payload: AuthRequest): Promise<AuthMessageResponse> {
    const response = await apiClient.post<AuthMessageResponse>("/auth/register", payload);
    return response.data;
}
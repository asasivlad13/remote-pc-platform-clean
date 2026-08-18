package com.remote.auth.model;

public enum AuthSessionRevokeReason {

    LOGOUT,

    LOGOUT_ALL,

    PASSWORD_CHANGED,

    PASSWORD_RESET,

    ACCOUNT_DISABLED,

    SECURITY_REVOKE,

    REFRESH_TOKEN_REUSE
}
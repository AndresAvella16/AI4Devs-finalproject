package com.jobpilot.auth.service;

/** JWT emitido y sus segundos de validez. */
public record IssuedToken(String value, long expiresInSeconds) {
}

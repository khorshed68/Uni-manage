package com.university.ums.dto;

import jakarta.validation.groups.Default;

/**
 * Validation group for POST requests. Extends {@link Default} so that every
 * normal constraint still runs, plus the ones that only apply on create
 * (e.g. the password is mandatory when creating but optional when updating).
 */
public interface OnCreate extends Default {
}

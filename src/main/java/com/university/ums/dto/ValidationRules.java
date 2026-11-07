package com.university.ums.dto;

final class ValidationRules {

    static final String USERNAME_REGEX = "^[A-Za-z0-9._-]{3,50}$";
    static final String USERNAME_MESSAGE = "must be 3-50 characters: letters, digits, '.', '_' or '-'";
    static final String PHONE_REGEX = "^$|^\\+?[0-9 -]{6,20}$";
    static final String PHONE_MESSAGE = "must be 6-20 digits (may start with + and contain spaces or dashes)";

    private ValidationRules() {
    }
}

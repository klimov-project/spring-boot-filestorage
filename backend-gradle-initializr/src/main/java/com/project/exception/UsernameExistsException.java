package com.project.exception;

public class UsernameExistsException extends RuntimeException {

    public UsernameExistsException() {
        super();
    }

    public UsernameExistsException(String message) {
        super(message);
    }

}

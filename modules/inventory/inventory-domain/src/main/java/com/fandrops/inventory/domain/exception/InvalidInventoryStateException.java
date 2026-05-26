package com.fandrops.inventory.domain.exception;

public class InvalidInventoryStateException extends RuntimeException {

    public InvalidInventoryStateException(String message) {
        super(message);
    }
}

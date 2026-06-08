package com.fandrops.user.domain;

public class AgencyApplicationAlreadyReviewedException extends RuntimeException {
    public AgencyApplicationAlreadyReviewedException(String message) {
        super(message);
    }
}

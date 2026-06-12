package com.fandrops.user.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fandrops.s3")
public class S3Properties {

    private String bucket;
    private String region;
    private int presignedUrlExpiryMinutes = 10;
    private String uploadPrefix = "uploads/banners";

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public int getPresignedUrlExpiryMinutes() { return presignedUrlExpiryMinutes; }
    public void setPresignedUrlExpiryMinutes(int minutes) { this.presignedUrlExpiryMinutes = minutes; }

    public String getUploadPrefix() { return uploadPrefix; }
    public void setUploadPrefix(String uploadPrefix) { this.uploadPrefix = uploadPrefix; }
}

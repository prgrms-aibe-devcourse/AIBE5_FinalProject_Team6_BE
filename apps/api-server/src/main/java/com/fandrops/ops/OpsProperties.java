package com.fandrops.ops;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fandrops.ops")
public class OpsProperties {

    private final Trace trace = new Trace();

    public Trace getTrace() {
        return trace;
    }

    public static class Trace {

        public static final String DEFAULT_HEADER = "X-Trace-Id";

        private boolean responseHeaderEnabled = true;
        private String headerName = DEFAULT_HEADER;

        public boolean isResponseHeaderEnabled() {
            return responseHeaderEnabled;
        }

        public void setResponseHeaderEnabled(boolean responseHeaderEnabled) {
            this.responseHeaderEnabled = responseHeaderEnabled;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }
    }
}

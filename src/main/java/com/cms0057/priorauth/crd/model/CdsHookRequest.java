package com.cms0057.priorauth.crd.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class CdsHookRequest {

    @JsonProperty("hookInstance")
    private String hookInstance;

    @JsonProperty("hook")
    private String hook;

    @JsonProperty("fhirServer")
    private String fhirServer;

    @JsonProperty("fhirAuthorization")
    private FhirAuthorization fhirAuthorization;

    @JsonProperty("context")
    private Map<String, Object> context;

    @JsonProperty("prefetch")
    private Map<String, Object> prefetch;

    public String getHookInstance() { return hookInstance; }
    public void setHookInstance(String v) { this.hookInstance = v; }
    public String getHook() { return hook; }
    public void setHook(String v) { this.hook = v; }
    public String getFhirServer() { return fhirServer; }
    public void setFhirServer(String v) { this.fhirServer = v; }
    public FhirAuthorization getFhirAuthorization() { return fhirAuthorization; }
    public void setFhirAuthorization(FhirAuthorization v) { this.fhirAuthorization = v; }
    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> v) { this.context = v; }
    public Map<String, Object> getPrefetch() { return prefetch; }
    public void setPrefetch(Map<String, Object> v) { this.prefetch = v; }

    public static class FhirAuthorization {
        @JsonProperty("access_token")
        private String accessToken;
        @JsonProperty("token_type")
        private String tokenType;
        @JsonProperty("expires_in")
        private Integer expiresIn;
        @JsonProperty("scope")
        private String scope;
        @JsonProperty("subject")
        private String subject;

        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String v) { this.accessToken = v; }
        public String getTokenType() { return tokenType; }
        public void setTokenType(String v) { this.tokenType = v; }
        public Integer getExpiresIn() { return expiresIn; }
        public void setExpiresIn(Integer v) { this.expiresIn = v; }
        public String getScope() { return scope; }
        public void setScope(String v) { this.scope = v; }
        public String getSubject() { return subject; }
        public void setSubject(String v) { this.subject = v; }
    }
}

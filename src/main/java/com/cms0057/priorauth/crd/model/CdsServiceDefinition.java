package com.cms0057.priorauth.crd.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CdsServiceDefinition {

    @JsonProperty("hook") private String hook;
    @JsonProperty("title") private String title;
    @JsonProperty("description") private String description;
    @JsonProperty("id") private String id;
    @JsonProperty("prefetch") private Map<String, String> prefetch;
    @JsonProperty("usageRequirements") private String usageRequirements;

    private CdsServiceDefinition() {}

    public String getHook() { return hook; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getId() { return id; }
    public Map<String, String> getPrefetch() { return prefetch; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final CdsServiceDefinition d = new CdsServiceDefinition();
        public Builder hook(String v) { d.hook = v; return this; }
        public Builder title(String v) { d.title = v; return this; }
        public Builder description(String v) { d.description = v; return this; }
        public Builder id(String v) { d.id = v; return this; }
        public Builder prefetch(Map<String, String> v) { d.prefetch = v; return this; }
        public Builder usageRequirements(String v) { d.usageRequirements = v; return this; }
        public CdsServiceDefinition build() { return d; }
    }

    public static class CdsServicesResponse {
        @JsonProperty("services") private List<CdsServiceDefinition> services;

        private CdsServicesResponse() {}

        public static CdsServicesResponse of(List<CdsServiceDefinition> services) {
            CdsServicesResponse r = new CdsServicesResponse();
            r.services = services;
            return r;
        }

        public List<CdsServiceDefinition> getServices() { return services; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private List<CdsServiceDefinition> services;
            public Builder services(List<CdsServiceDefinition> v) { this.services = v; return this; }
            public CdsServicesResponse build() { return CdsServicesResponse.of(services); }
        }
    }
}

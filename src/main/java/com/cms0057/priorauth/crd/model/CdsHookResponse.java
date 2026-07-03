package com.cms0057.priorauth.crd.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CdsHookResponse {

    @JsonProperty("cards")
    private List<Card> cards;

    @JsonProperty("systemActions")
    private List<SystemAction> systemActions;

    private CdsHookResponse() {}

    public static CdsHookResponse of(List<Card> cards) {
        CdsHookResponse r = new CdsHookResponse();
        r.cards = cards;
        return r;
    }

    public static CdsHookResponse of(List<Card> cards, List<SystemAction> systemActions) {
        CdsHookResponse r = new CdsHookResponse();
        r.cards = cards;
        r.systemActions = systemActions;
        return r;
    }

    public List<Card> getCards() { return cards; }
    public List<SystemAction> getSystemActions() { return systemActions; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Card {
        @JsonProperty("uuid") private String uuid;
        @JsonProperty("summary") private String summary;
        @JsonProperty("detail") private String detail;
        @JsonProperty("indicator") private String indicator;
        @JsonProperty("source") private Source source;
        @JsonProperty("suggestions") private List<Suggestion> suggestions;
        @JsonProperty("selectionBehavior") private String selectionBehavior;
        @JsonProperty("overrideReasons") private List<OverrideReason> overrideReasons;
        @JsonProperty("links") private List<Link> links;

        private Card() {}

        public static Builder builder() { return new Builder(); }
        public String getUuid() { return uuid; }
        public String getSummary() { return summary; }
        public String getDetail() { return detail; }
        public String getIndicator() { return indicator; }
        public Source getSource() { return source; }
        public List<Suggestion> getSuggestions() { return suggestions; }
        public List<Link> getLinks() { return links; }

        public static class Builder {
            private final Card card = new Card();
            public Builder uuid(String v) { card.uuid = v; return this; }
            public Builder summary(String v) { card.summary = v; return this; }
            public Builder detail(String v) { card.detail = v; return this; }
            public Builder indicator(String v) { card.indicator = v; return this; }
            public Builder source(Source v) { card.source = v; return this; }
            public Builder suggestions(List<Suggestion> v) { card.suggestions = v; return this; }
            public Builder selectionBehavior(String v) { card.selectionBehavior = v; return this; }
            public Builder overrideReasons(List<OverrideReason> v) { card.overrideReasons = v; return this; }
            public Builder links(List<Link> v) { card.links = v; return this; }
            public Card build() { return card; }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Source {
        @JsonProperty("label") private String label;
        @JsonProperty("url") private String url;
        @JsonProperty("icon") private String icon;
        @JsonProperty("topic") private Map<String, String> topic;

        private Source() {}
        public static Builder builder() { return new Builder(); }
        public String getLabel() { return label; }
        public String getUrl() { return url; }

        public static class Builder {
            private final Source s = new Source();
            public Builder label(String v) { s.label = v; return this; }
            public Builder url(String v) { s.url = v; return this; }
            public Builder icon(String v) { s.icon = v; return this; }
            public Builder topic(Map<String, String> v) { s.topic = v; return this; }
            public Source build() { return s; }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Suggestion {
        @JsonProperty("label") private String label;
        @JsonProperty("uuid") private String uuid;
        @JsonProperty("isRecommended") private Boolean isRecommended;
        @JsonProperty("actions") private List<Action> actions;

        private Suggestion() {}
        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final Suggestion s = new Suggestion();
            public Builder label(String v) { s.label = v; return this; }
            public Builder uuid(String v) { s.uuid = v; return this; }
            public Builder isRecommended(Boolean v) { s.isRecommended = v; return this; }
            public Builder actions(List<Action> v) { s.actions = v; return this; }
            public Suggestion build() { return s; }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Action {
        @JsonProperty("type") private String type;
        @JsonProperty("description") private String description;
        @JsonProperty("resource") private Object resource;
        @JsonProperty("resourceId") private String resourceId;

        private Action() {}
    }

    public static class OverrideReason {
        @JsonProperty("code") private Map<String, Object> code;
        private OverrideReason() {}
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Link {
        @JsonProperty("label") private String label;
        @JsonProperty("url") private String url;
        @JsonProperty("type") private String type;
        @JsonProperty("appContext") private String appContext;

        private Link() {}
        public static Builder builder() { return new Builder(); }
        public String getLabel() { return label; }
        public String getUrl() { return url; }

        public static class Builder {
            private final Link l = new Link();
            public Builder label(String v) { l.label = v; return this; }
            public Builder url(String v) { l.url = v; return this; }
            public Builder type(String v) { l.type = v; return this; }
            public Builder appContext(String v) { l.appContext = v; return this; }
            public Link build() { return l; }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SystemAction {
        @JsonProperty("type") private String type;
        @JsonProperty("description") private String description;
        @JsonProperty("resource") private Object resource;

        private SystemAction() {}
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private List<Card> cards;
        private List<SystemAction> systemActions;
        public Builder cards(List<Card> v) { this.cards = v; return this; }
        public Builder systemActions(List<SystemAction> v) { this.systemActions = v; return this; }
        public CdsHookResponse build() { return CdsHookResponse.of(cards, systemActions); }
    }
}

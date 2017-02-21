package com.unidev.polyinsights.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Document(collection = "tenant")
public class Tenant {

    @Id
    private String tenant;

    private Map<String, InsightType> types;

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("Tenant{");
        sb.append("tenant='").append(tenant).append('\'');
        sb.append(", types=").append(types);
        sb.append('}');
        return sb.toString();
    }

    public void addType(InsightType type) {
        if (types == null) {
            types = new HashMap<>();
        }
        types.put(type.getName(), type);
    }

    public Optional<InsightType> fetchInsight(String type) {
        if (types == null) {
            return Optional.empty();
        }
        if (!types.containsKey(type)) {
            return Optional.empty();
        }
        InsightType insightType = types.get(type);
        return Optional.of(insightType);
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public Map<String, InsightType> getTypes() {
        return types;
    }

    public void setTypes(Map<String, InsightType> types) {
        this.types = types;
    }
}

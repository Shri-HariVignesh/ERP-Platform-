package com.campusos.portal.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "tenants")
public class Tenant {
    @Id
    public String id;
    public String name;
    public String shortName;
    public String city;
    public String accent;

    public Tenant() {}

    public Tenant(String id, String name, String shortName, String city, String accent) {
        this.id = id; this.name = name; this.shortName = shortName; this.city = city; this.accent = accent;
    }
}

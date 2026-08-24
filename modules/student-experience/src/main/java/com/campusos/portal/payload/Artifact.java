package com.campusos.portal.payload;

/** A side-effect made visible: verify id, serial number, a link to a generated doc. */
public record Artifact(String kind, String label, String value, String href) {
    public static Artifact of(String kind, String label, String value) {
        return new Artifact(kind, label, value, null);
    }
}

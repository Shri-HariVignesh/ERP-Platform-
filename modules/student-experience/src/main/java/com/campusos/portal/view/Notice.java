package com.campusos.portal.view;

/** One in-app notification line. Derived from existing audit rows — no notification table. */
public record Notice(String kind, String title, String detail, String who, String at, String href) {}

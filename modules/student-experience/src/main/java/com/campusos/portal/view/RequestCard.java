package com.campusos.portal.view;

import com.campusos.portal.payload.Artifact;
import java.util.List;

/**
 * The normalized read model. Home and My Requests both consume exactly this and
 * nothing else — which is why neither template contains a per-type branch.
 * Every field is display-ready: the templates never see a raw enum constant.
 */
public record RequestCard(
        String id,
        String type,
        String typeLabel,
        String title,
        String subtitle,
        String state,
        String stateLabel,
        String badgeTone,
        String headline,
        List<TimelineStep> steps,
        ActionButton studentAction,
        List<ActionButton> staffActions,
        List<Artifact> artifacts,
        String createdAt,
        String updatedAt,
        List<TrailEntry> trail) {

    public boolean isOpen() {
        return !"success".equals(badgeTone) && !"danger".equals(badgeTone);
    }
}

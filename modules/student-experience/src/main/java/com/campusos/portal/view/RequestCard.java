package com.campusos.portal.view;

import com.campusos.portal.payload.Artifact;
import java.util.List;

/**
 * The normalized read model. Home and My Requests both consume exactly this and
 * nothing else — which is why neither template contains a per-type branch.
 * Every field is display-ready: the templates never see a raw enum constant.
 *
 * There is no staffActions field. There used to be: it fed the /sim demo hook, which rendered
 * a button per staff edge with the actor in a hidden input. That hook is retired, and leaving
 * an un-role-filtered list of staff moves hanging off the STUDENT read model would be an
 * invitation to re-render it. Staff actions are built on the staff side, from the principal's
 * own roles — see view/StaffAction and FacultyService.
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
        List<Artifact> artifacts,
        String createdAt,
        String updatedAt,
        List<TrailEntry> trail) {

    public boolean isOpen() {
        return !"success".equals(badgeTone) && !"danger".equals(badgeTone);
    }
}

package com.campusos.portal.view;

import java.util.List;

/**
 * One row of the staff inbox: the SAME normalized RequestCard a student sees, plus who it is
 * about and what this particular staff member may do about it. Reusing RequestCard is what
 * keeps the timeline, the trail and the labels identical on both sides of the portal.
 */
public record FacultyTask(RequestCard card, String studentId, String studentName,
                          String rollNo, String className, List<StaffAction> actions) {

    public boolean actionable() { return !actions.isEmpty(); }
}

package com.campusos.portal.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusos.portal.domain.Actor;
import com.campusos.portal.domain.Request;
import com.campusos.portal.domain.RequestState;
import com.campusos.portal.domain.RequestType;
import com.campusos.portal.engine.EngineTestBase;
import com.campusos.portal.payload.GrievancePayload.Category;
import com.campusos.portal.service.PresentationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * FINDING: AUTO_ASSIGN declares no side effects, so nothing routes a grievance anywhere — yet
 * the card claimed it was "currently with Class Advisor", who is the matrix actor and never a
 * desk. The desk name is now derived from the category in the display layer.
 *
 * These tests pin the honest version: the badge and the headline name the SAME desk, and no
 * grievance is ever attributed to a matrix actor. They assert display only — no engine
 * behaviour, no state and no edge is claimed to have changed.
 */
class GrievanceDeskTest extends EngineTestBase {

    @Autowired
    private PresentationService presentation;

    @Test
    @DisplayName("each category maps to its documented desk")
    void desksAreTheDocumentedOnes() {
        assertThat(DisplayLabels.desk(Category.ACADEMIC)).isEqualTo("Academic Office");
        assertThat(DisplayLabels.desk(Category.EXAM)).isEqualTo("Examination Office");
        assertThat(DisplayLabels.desk(Category.FEES)).isEqualTo("Accounts Office");
        assertThat(DisplayLabels.desk(Category.HOSTEL)).isEqualTo("Hostel Warden Office");
        assertThat(DisplayLabels.desk(Category.OTHER)).isEqualTo("Student Services");
    }

    @Test
    @DisplayName("a missing category degrades to a desk, never to a matrix actor")
    void nullCategoryStillNamesADesk() {
        assertThat(DisplayLabels.desk(null)).isEqualTo("Student Services");
    }

    /**
     * Only the approver roles are excluded. Actor.OFFICE displays as "Examination Office",
     * which is also the correct desk for an EXAM grievance — that overlap is the same real
     * office under one name, not the bug. The bug was attributing a hostel or fees complaint
     * to the Class Advisor, so that is what this pins.
     */
    @ParameterizedTest
    @EnumSource(Category.class)
    @DisplayName("no category is ever displayed as an approver role")
    void noDeskIsAnApproverRole(Category category) {
        for (Actor a : new Actor[] {Actor.FACULTY, Actor.HOD, Actor.STUDENT, Actor.SYSTEM}) {
            assertThat(DisplayLabels.desk(category))
                    .as("%s must not be shown as the %s", category, a)
                    .isNotEqualTo(a.display());
        }
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    @DisplayName("the badge and the headline name the same desk, for every category")
    void badgeAndHeadlineAgree(Category category) {
        Fixture f = fixture("desk");
        Request created = machine.create(f.scope(), RequestType.GRIEVANCE, grievance(category));
        Request r = requests.findByIdAndTenantIdAndStudentId(
                created.id, f.scope().tenantId(), f.scope().studentId()).orElseThrow();

        assertThat(r.state).as("autopilot fires AUTO_ASSIGN unchanged").isEqualTo(RequestState.ASSIGNED);

        RequestCard card = presentation.card(f.scope(), r);
        String desk = DisplayLabels.desk(category);

        assertThat(card.stateLabel()).isEqualTo("Assigned to " + desk);
        assertThat(card.headline()).isEqualTo("Currently with " + desk + ".");
        assertThat(card.subtitle()).contains(desk);
        assertThat(card.stateLabel()).doesNotContain("Class Advisor");
        assertThat(card.headline()).doesNotContain("Class Advisor");
    }

    @Test
    @DisplayName("a non-grievance keeps the matrix actor — the desk mapping is grievance-only")
    void otherTypesAreUntouched() {
        Fixture f = fixture("desk");
        Request r = drive(f, "DOCUMENT_MANUAL");
        RequestCard card = presentation.card(f.scope(), r);

        assertThat(card.stateLabel()).isEqualTo("With office");
        assertThat(card.headline()).isEqualTo("Currently with " + Actor.OFFICE.display() + ".");
    }
}

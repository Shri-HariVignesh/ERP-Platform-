package com.campusos.portal.web;

import com.campusos.portal.domain.*;
import com.campusos.portal.repo.*;
import com.campusos.portal.service.*;
import com.campusos.portal.view.MarkRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * The 8 faculty views. Every handler starts by resolving a StaffScope from the AUTHENTICATED
 * principal — no handler reads a tenantId, staffId or actor from the request.
 *
 * The role/assignment flags added by base() drive which nav tabs and entry surfaces exist.
 * That is presentation only: every POST re-derives the scope and re-checks server-side, so
 * hiding a button is a courtesy and never the control.
 */
@Controller
@RequestMapping("/faculty")
public class FacultyController {

    private final FacultyService faculty;
    private final AcademicWriteService writes;
    private final StaffScopeResolver staffScopes;
    private final AcademicService academic;
    private final TenantRepository tenants;
    private final SubjectMarkRepository marks;
    private final AttendanceRepository attendance;

    public FacultyController(FacultyService faculty, AcademicWriteService writes,
                             StaffScopeResolver staffScopes, AcademicService academic,
                             TenantRepository tenants, SubjectMarkRepository marks,
                             AttendanceRepository attendance) {
        this.faculty = faculty;
        this.writes = writes;
        this.staffScopes = staffScopes;
        this.academic = academic;
        this.tenants = tenants;
        this.marks = marks;
        this.attendance = attendance;
    }

    /* --------------------------------- shared --------------------------------- */

    private StaffScope base(Model model, Authentication auth, String nav) {
        StaffScope scope = staffScopes.current(auth);
        model.addAttribute("staff", scope);
        model.addAttribute("tenant", tenants.findById(scope.tenantId()).orElseThrow());
        model.addAttribute("nav", nav);
        model.addAttribute("roleLabels", scope.roles().stream().map(StaffRole::display).toList());
        // Pass 3: a surface exists only where the principal is authorized for it.
        model.addAttribute("canLeave",
                scope.hasRole(StaffRole.FACULTY) || scope.hasRole(StaffRole.HOD));
        model.addAttribute("canInternship",
                scope.hasRole(StaffRole.FACULTY) || scope.hasRole(StaffRole.INSTITUTION));
        model.addAttribute("canAuthor", scope.authors());
        model.addAttribute("pending", faculty.inbox(scope).size());
        return scope;
    }

    /* -------------------------------- 1. HOME -------------------------------- */

    @GetMapping
    public String home(Model model, Authentication auth) {
        StaffScope scope = base(model, auth, "home");
        model.addAttribute("tasks", faculty.inbox(scope).stream().limit(5).toList());
        model.addAttribute("recent", faculty.notifications(scope, 6));
        model.addAttribute("roster", staffScopes.roster(scope).size());
        model.addAttribute("classes", scope.classes());
        return "faculty/home";
    }

    /* ------------------------------ 2. MY TASKS ------------------------------ */

    @GetMapping("/tasks")
    public String tasks(@RequestParam(required = false) String filter,
                        Model model, Authentication auth) {
        StaffScope scope = base(model, auth, "tasks");
        RequestType type = null;
        if (filter != null && !filter.isBlank() && !"ALL".equals(filter)) {
            type = RequestType.valueOf(filter);
        }
        model.addAttribute("tasks", faculty.inbox(scope, type));
        model.addAttribute("filter", type == null ? "ALL" : type.name());
        model.addAttribute("types", RequestType.values());
        model.addAttribute("back", "/faculty/tasks");
        return "faculty/tasks";
    }

    /* ------------------------------ 3. STUDENTS ------------------------------ */

    @GetMapping("/students")
    public String students(@RequestParam(required = false) String q, Model model, Authentication auth) {
        StaffScope scope = base(model, auth, "students");
        List<Student> roster = staffScopes.roster(scope);
        if (q != null && !q.isBlank()) {
            String needle = q.trim().toLowerCase();
            roster = roster.stream()
                    .filter(s -> s.name.toLowerCase().contains(needle)
                            || s.rollNo.toLowerCase().contains(needle))
                    .toList();
        }
        model.addAttribute("roster", roster);
        model.addAttribute("q", q == null ? "" : q);
        return "faculty/students";
    }

    @GetMapping("/students/{id}")
    public String student(@PathVariable String id, Model model, Authentication auth) {
        StaffScope scope = base(model, auth, "students");
        Student s = staffScopes.studentInScope(scope, id);
        Scope studentScope = new Scope(s.tenantId, s.id);

        model.addAttribute("s", s);
        model.addAttribute("className", ClassKey.of(s).label());
        model.addAttribute("attendancePct", academic.attendancePct(studentScope));
        model.addAttribute("approvedLeaveDays", academic.approvedLeaveDays(studentScope));
        model.addAttribute("results", academic.results(studentScope));
        model.addAttribute("cgpa", academic.cgpa(studentScope));
        // Read-only, and only what is PUBLISHED — a colleague's unfinalized drafts are theirs.
        model.addAttribute("marks", academic.publishedMarks(studentScope));
        model.addAttribute("records", academic.records(studentScope));
        model.addAttribute("documents", academic.documents(studentScope));
        model.addAttribute("cards", faculty.requestsOf(scope, s));
        return "faculty/student";
    }

    /* --------------------------- 4/5. LEAVE, INTERNSHIP --------------------------- */

    @GetMapping("/leave")
    public String leave(Model model, Authentication auth) {
        StaffScope scope = base(model, auth, "leave");
        model.addAttribute("tasks", faculty.inbox(scope, RequestType.LEAVE));
        model.addAttribute("workflow", "Leave");
        model.addAttribute("back", "/faculty/leave");
        return "faculty/workflow";
    }

    @GetMapping("/internship")
    public String internship(Model model, Authentication auth) {
        StaffScope scope = base(model, auth, "internship");
        model.addAttribute("tasks", faculty.inbox(scope, RequestType.INTERNSHIP));
        model.addAttribute("workflow", "Internship");
        model.addAttribute("back", "/faculty/internship");
        return "faculty/workflow";
    }

    /* ------------------------------ 6. ATTENDANCE ------------------------------ */

    @GetMapping("/attendance")
    public String attendance(@RequestParam(required = false) String clazz,
                             @RequestParam(required = false) String subject,
                             @RequestParam(required = false) String date,
                             Model model, Authentication auth) {
        StaffScope scope = base(model, auth, "attendance");
        if (scope.classes().isEmpty()) return "faculty/no-classes";

        ClassKey key = clazz == null || clazz.isBlank()
                ? scope.classes().get(0) : ClassKey.parse(clazz);
        if (!scope.teaches(key)) throw new StaffAccessException("not a class you teach");

        List<TeachingAssignment> subjects = scope.subjectsIn(key);
        String subjectCode = subject == null || subject.isBlank()
                ? subjects.get(0).subjectCode : subject;
        if (!scope.teaches(key, subjectCode)) throw new StaffAccessException("not a subject you teach");

        LocalDate day = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        List<Student> roster = staffScopes.classRoster(scope, key);

        // Existing state for that day, so the screen shows marking AND viewing/updating.
        Map<String, String> current = new LinkedHashMap<>();
        Map<String, Double> pct = new LinkedHashMap<>();
        for (Student s : roster) {
            attendance.findByTenantIdAndStudentIdAndDate(scope.tenantId(), s.id, day)
                    .ifPresent(r -> current.put(s.id, r.status.name()));
            pct.put(s.id, academic.attendancePct(new Scope(s.tenantId, s.id)));
        }

        model.addAttribute("classes", scope.classes());
        model.addAttribute("subjects", subjects);
        model.addAttribute("clazz", key);
        model.addAttribute("subject", subjectCode);
        model.addAttribute("date", day);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("roster", roster);
        model.addAttribute("current", current);
        model.addAttribute("pct", pct);
        return "faculty/attendance";
    }

    @PostMapping("/attendance")
    public String markAttendance(@RequestParam String clazz, @RequestParam String subject,
                                 @RequestParam String date, @RequestParam Map<String, String> params,
                                 Authentication auth,
                                 org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        StaffScope scope = staffScopes.current(auth);
        ClassKey key = ClassKey.parse(clazz);

        Map<String, AttendanceRecord.Status> input = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!e.getKey().startsWith("status_")) continue;
            String studentId = e.getKey().substring("status_".length());
            String v = e.getValue();
            if (v == null || v.isBlank()) continue;
            input.put(studentId, AttendanceRecord.Status.valueOf(v));
        }

        AcademicWriteService.AttendanceWrite w =
                writes.markAttendance(scope, key, subject, LocalDate.parse(date), input);
        String msg = w.marked() + " student(s) marked for " + date + ".";
        if (w.leaveProtected() > 0) {
            msg += " " + w.leaveProtected() + " left untouched — approved leave is owned by the "
                    + "leave workflow, not the register.";
        }
        ra.addFlashAttribute("flash", msg);
        return "redirect:/faculty/attendance?clazz=" + enc(clazz) + "&subject=" + enc(subject)
                + "&date=" + enc(date);
    }

    /* --------------------------- 7. MARKS & RESULTS --------------------------- */

    @GetMapping("/marks")
    public String marks(@RequestParam(required = false) String clazz,
                        @RequestParam(required = false) String subject,
                        Model model, Authentication auth) {
        StaffScope scope = base(model, auth, "marks");
        if (scope.classes().isEmpty()) return "faculty/no-classes";

        ClassKey key = clazz == null || clazz.isBlank()
                ? scope.classes().get(0) : ClassKey.parse(clazz);
        if (!scope.teaches(key)) throw new StaffAccessException("not a class you teach");

        List<TeachingAssignment> subjects = scope.subjectsIn(key);
        String subjectCode = subject == null || subject.isBlank()
                ? subjects.get(0).subjectCode : subject;
        if (!scope.teaches(key, subjectCode)) throw new StaffAccessException("not a subject you teach");

        List<Student> roster = staffScopes.classRoster(scope, key);
        Map<String, MarkRow> current = new LinkedHashMap<>();
        for (Student s : roster) {
            marks.findByTenantIdAndStudentIdAndSemesterAndSubjectCode(
                            scope.tenantId(), s.id, key.semester(), subjectCode)
                    .ifPresent(m -> current.put(s.id, new MarkRow(m.semester, m.subjectCode,
                            m.subjectName, m.internal, m.external, m.total(),
                            SgpaMath.grade(m.total()), m.credits,
                            m.finalized() ? "Finalized" : "Draft", m.finalized())));
        }

        model.addAttribute("classes", scope.classes());
        model.addAttribute("subjects", subjects);
        model.addAttribute("clazz", key);
        model.addAttribute("subject", subjectCode);
        model.addAttribute("roster", roster);
        model.addAttribute("current", current);
        model.addAttribute("maxInternal", SgpaMath.MAX_INTERNAL);
        model.addAttribute("maxExternal", SgpaMath.MAX_EXTERNAL);
        return "faculty/marks";
    }

    @PostMapping("/marks")
    public String saveMarks(@RequestParam String clazz, @RequestParam String subject,
                            @RequestParam String action, @RequestParam Map<String, String> params,
                            Authentication auth,
                            org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        StaffScope scope = staffScopes.current(auth);
        ClassKey key = ClassKey.parse(clazz);
        MarkStatus target = "finalize".equals(action) ? MarkStatus.FINALIZED : MarkStatus.DRAFT;

        Map<String, AcademicWriteService.MarkInput> input = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!e.getKey().startsWith("internal_")) continue;
            String studentId = e.getKey().substring("internal_".length());
            String internal = e.getValue();
            String external = params.get("external_" + studentId);
            if (internal == null || internal.isBlank() || external == null || external.isBlank()) continue;
            input.put(studentId, new AcademicWriteService.MarkInput(
                    Integer.parseInt(internal.trim()), Integer.parseInt(external.trim())));
        }

        AcademicWriteService.MarksWrite w = writes.saveMarks(scope, key, subject, input, target);
        String msg = w.saved() + " entr(ies) "
                + (target == MarkStatus.FINALIZED ? "finalized." : "saved as draft — not visible to students.");
        if (!w.recomputed().isEmpty()) {
            msg += " " + w.recomputed().size()
                    + " semester result(s) republished — every subject for the semester is now finalized.";
        } else if (target == MarkStatus.FINALIZED) {
            msg += " Semester results are unchanged until every subject of the semester is finalized.";
        }
        ra.addFlashAttribute("flash", msg);
        return "redirect:/faculty/marks?clazz=" + enc(clazz) + "&subject=" + enc(subject);
    }

    /* ---------------------------- 8. NOTIFICATIONS ---------------------------- */

    @GetMapping("/notifications")
    public String notifications(Model model, Authentication auth) {
        StaffScope scope = base(model, auth, "notifications");
        model.addAttribute("notices", faculty.notifications(scope, 60));
        return "faculty/notifications";
    }

    /* ------------------------- the one action endpoint ------------------------- */

    /**
     * EVERY staff decision, for every workflow, goes through here.
     *
     * The body carries an event and an optional note. It does NOT carry an actor, a tenant or
     * a student — those are derived from the session, which is what makes the actor
     * identity-bound rather than merely validated.
     */
    @PostMapping("/requests/{id}/act")
    public String act(@PathVariable String id, @RequestParam Event event,
                      @RequestParam(required = false) String note,
                      @RequestParam(defaultValue = "/faculty/tasks") String back,
                      Authentication auth,
                      org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        StaffScope scope = staffScopes.current(auth);
        try {
            faculty.act(scope, id, event, note);
            ra.addFlashAttribute("flash", "Done — the request has moved on.");
        } catch (com.campusos.portal.engine.IllegalTransitionException e) {
            ra.addFlashAttribute("error", "That move is not allowed from this stage.");
        }
        return "redirect:" + SafeRedirect.resolveStaff(back);
    }

    private static String enc(String v) {
        return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
    }
}

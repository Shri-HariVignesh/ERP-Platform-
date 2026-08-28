-- Mirrors the JPA @Entity tables 1:1. In-memory by default (see db.js), so a restart
-- reseeds — same behaviour as the Java module's H2 `create-drop`.

CREATE TABLE tenants (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  shortName TEXT NOT NULL,
  city TEXT NOT NULL,
  accent TEXT NOT NULL
);

CREATE TABLE students (
  id TEXT PRIMARY KEY,
  tenantId TEXT NOT NULL,
  rollNo TEXT NOT NULL,
  name TEXT NOT NULL,
  email TEXT NOT NULL,
  program TEXT NOT NULL,
  department TEXT NOT NULL,
  semester INTEGER NOT NULL,
  section TEXT NOT NULL,
  feeDues INTEGER NOT NULL DEFAULT 0,
  active INTEGER NOT NULL DEFAULT 1,
  leaveBalance INTEGER NOT NULL DEFAULT 12,
  advisorName TEXT,
  hodName TEXT,
  -- UGC Anti-Ragging Regulations, 2009: an annual acknowledgment, not a one-time signup step.
  -- NULL means not yet acknowledged for the current cycle.
  antiRaggingAffidavitAt TEXT,
  -- NEP 2020 / Academic Bank of Credits: a 12-digit ABC ID, once the student is registered
  -- with DigiLocker/ABC. NULL means not yet registered — display-only, nothing reads it back.
  abcId TEXT
);

CREATE TABLE student_accounts (
  id TEXT PRIMARY KEY,
  tenantId TEXT NOT NULL,
  studentId TEXT NOT NULL UNIQUE,
  username TEXT NOT NULL UNIQUE,
  passwordHash TEXT NOT NULL,
  active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE staff_users (
  id TEXT PRIMARY KEY,
  tenantId TEXT NOT NULL,
  username TEXT NOT NULL UNIQUE,
  passwordHash TEXT NOT NULL,
  name TEXT NOT NULL,
  email TEXT,
  department TEXT,
  active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE staff_user_roles (
  staffId TEXT NOT NULL,
  role TEXT NOT NULL,
  PRIMARY KEY (staffId, role)
);

CREATE TABLE teaching_assignments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tenantId TEXT NOT NULL,
  staffId TEXT NOT NULL,
  department TEXT NOT NULL,
  semester INTEGER NOT NULL,
  section TEXT NOT NULL,
  subjectCode TEXT NOT NULL,
  subjectName TEXT,
  credits INTEGER NOT NULL DEFAULT 3
);
CREATE INDEX ix_ta_staff ON teaching_assignments(tenantId, staffId);
CREATE INDEX ix_ta_class ON teaching_assignments(tenantId, department, semester, section);

CREATE TABLE requests (
  id TEXT PRIMARY KEY,
  tenantId TEXT NOT NULL,
  studentId TEXT NOT NULL,
  type TEXT NOT NULL,
  state TEXT NOT NULL,
  payload TEXT NOT NULL,
  createdAt TEXT NOT NULL,
  updatedAt TEXT NOT NULL
);
CREATE INDEX ix_requests_scope ON requests(tenantId, studentId, createdAt);

CREATE TABLE request_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  requestId TEXT NOT NULL,
  tenantId TEXT NOT NULL,
  studentId TEXT NOT NULL,
  fromState TEXT,
  toState TEXT NOT NULL,
  actor TEXT NOT NULL,
  at TEXT NOT NULL,
  note TEXT,
  effects TEXT DEFAULT '',
  effectLog TEXT DEFAULT '',
  -- Which specific staff member fired this transition, not just their Actor role (FACULTY,
  -- HOD, ...) — the role alone can't answer "who approved this." NULL for STUDENT/SYSTEM-fired
  -- rows and for every row written before this column existed (this is an in-memory demo DB
  -- reseeded on every boot, so there is no real backfill to do). Name is denormalized alongside
  -- the id, same as academic_audit.staffId/staffName below, to avoid a join on every read.
  actedByStaffId TEXT,
  actedByStaffName TEXT
);
CREATE INDEX ix_history_request ON request_history(requestId);

CREATE TABLE attendance (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tenantId TEXT NOT NULL,
  studentId TEXT NOT NULL,
  date TEXT NOT NULL,
  status TEXT NOT NULL,
  sourceRequestId TEXT,
  markedByStaffId TEXT
);
CREATE INDEX ix_attendance_scope ON attendance(tenantId, studentId);

CREATE TABLE academic_record (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tenantId TEXT NOT NULL,
  studentId TEXT NOT NULL,
  kind TEXT NOT NULL,
  title TEXT NOT NULL,
  subtitle TEXT,
  credits INTEGER NOT NULL DEFAULT 0,
  verifyId TEXT,
  sourceRequestId TEXT,
  recordedAt TEXT NOT NULL
);
CREATE INDEX ix_academic_scope ON academic_record(tenantId, studentId);

CREATE TABLE documents (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tenantId TEXT NOT NULL,
  studentId TEXT NOT NULL,
  serialNo TEXT NOT NULL UNIQUE,
  docType TEXT NOT NULL,
  title TEXT NOT NULL,
  html TEXT NOT NULL,
  verifyId TEXT,
  sourceRequestId TEXT,
  issuedAt TEXT NOT NULL
);
CREATE INDEX ix_documents_scope ON documents(tenantId, studentId);

CREATE TABLE verifications (
  verifyId TEXT PRIMARY KEY,
  tenantId TEXT NOT NULL,
  studentId TEXT NOT NULL,
  kind TEXT NOT NULL,
  subject TEXT NOT NULL,
  detail TEXT,
  sourceRequestId TEXT,
  issuedAt TEXT NOT NULL
);

CREATE TABLE exam_terms (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tenantId TEXT NOT NULL,
  name TEXT NOT NULL,
  startDate TEXT NOT NULL,
  endDate TEXT NOT NULL,
  hallTicketReleased INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE subject_marks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tenantId TEXT NOT NULL,
  studentId TEXT NOT NULL,
  semester INTEGER NOT NULL,
  subjectCode TEXT NOT NULL,
  subjectName TEXT,
  internal INTEGER NOT NULL DEFAULT 0,
  external INTEGER NOT NULL DEFAULT 0,
  credits INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'DRAFT',
  enteredByStaffId TEXT,
  updatedAt TEXT NOT NULL
);
CREATE INDEX ix_marks_scope ON subject_marks(tenantId, studentId, semester);
CREATE UNIQUE INDEX ix_marks_upsert ON subject_marks(tenantId, studentId, semester, subjectCode);

CREATE TABLE semester_results (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tenantId TEXT NOT NULL,
  studentId TEXT NOT NULL,
  semester INTEGER NOT NULL,
  sgpa REAL NOT NULL DEFAULT 0,
  credits INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX ix_results_scope ON semester_results(tenantId, studentId);
CREATE UNIQUE INDEX ix_results_upsert ON semester_results(tenantId, studentId, semester);

CREATE TABLE academic_audit (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tenantId TEXT NOT NULL,
  studentId TEXT NOT NULL,
  staffId TEXT NOT NULL,
  staffName TEXT,
  kind TEXT NOT NULL,
  subjectCode TEXT,
  detail TEXT,
  at TEXT NOT NULL
);
CREATE INDEX ix_audit_student ON academic_audit(tenantId, studentId);
CREATE INDEX ix_audit_staff ON academic_audit(tenantId, staffId);

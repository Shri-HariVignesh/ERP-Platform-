import express from 'express';
import session from 'express-session';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { db } from './db/db.js';
import { runSeed } from './config/seed.js';
import { securityHeaders } from './web/middleware/securityHeaders.js';
import { csrfToken, csrfProtect } from './web/middleware/csrf.js';
import { errorHandler, notFoundHandler } from './web/middleware/errorHandler.js';
import { loginRoutes } from './web/loginRoutes.js';
import { portalRoutes } from './web/portalRoutes.js';
import { actionsRoutes } from './web/actionsRoutes.js';
import { verifyRoutes } from './web/verifyRoutes.js';
import { facultyRoutes } from './web/facultyRoutes.js';
import { DisplayLabels } from './view/DisplayLabels.js';
import { DocType } from './domain/enums.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = process.env.PORT || 8080;

// Demo data on every start, via the REAL state machine — same behaviour as the Java module's
// CommandLineRunner against an in-memory H2 database.
runSeed();

const app = express();
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, '..', 'views'));
app.disable('x-powered-by');

// Available to every template without threading it through each render() call — the
// EJS equivalent of Thymeleaf's `T(...)` static-method access to DisplayLabels/DocType.
app.locals.DisplayLabels = DisplayLabels;
app.locals.DocType = DocType;

// SecurityHeaders.java runs at HIGHEST_PRECEDENCE, before anything that might short-circuit
// the chain — headers must land on every response, including denials and redirects.
app.use(securityHeaders);

app.use(express.static(path.join(__dirname, '..', 'public')));
app.use(express.urlencoded({ extended: false, limit: '64kb' }));

app.use(session({
  secret: process.env.CAMPUSOS_SESSION_SECRET || 'dev-only-secret-change-in-production',
  resave: false,
  saveUninitialized: false,
  // SECURITY: SameSite=Lax stops the session cookie riding along on cross-site POSTs — the
  // CSRF vector for every state-changing form in this prototype — and httpOnly keeps script
  // access out even though this app ships none.
  cookie: { httpOnly: true, sameSite: 'lax', secure: false },
}));

app.use(csrfToken);
app.use(csrfProtect);

app.use(loginRoutes);
app.use(portalRoutes);
app.use('/actions', actionsRoutes);
app.use('/verify', verifyRoutes);
app.use('/faculty', facultyRoutes);

app.use(notFoundHandler);
app.use(errorHandler);

app.listen(PORT, () => {
  console.log(`CampusOS (JS) listening on http://localhost:${PORT}`);
});

process.on('SIGINT', () => { db.close(); process.exit(0); });

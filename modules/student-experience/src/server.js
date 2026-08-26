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
import { langRoutes, readLocaleCookie } from './web/langRoutes.js';
import { DisplayLabels } from './view/DisplayLabels.js';
import { DocType } from './domain/enums.js';
import { I18n } from './view/i18n.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = process.env.PORT || 8080;

// Demo data on every start, via the REAL state machine — an in-memory database means a
// restart reseeds.
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

// Locale lives in its own cookie (langRoutes.js), not the session — see the comment there for
// why: session.regenerate()/destroy() at login/logout would otherwise silently reset it.
app.use((req, res, next) => {
  const locale = readLocaleCookie(req) === 'hi' ? 'hi' : 'en';
  res.locals.locale = locale;
  res.locals.t = (key) => I18n.t(locale, key);
  res.locals.currentPath = req.originalUrl;
  next();
});

app.use(loginRoutes);
app.use(portalRoutes);
app.use('/actions', actionsRoutes);
app.use('/verify', verifyRoutes);
app.use('/faculty', facultyRoutes);
app.use(langRoutes);

app.use(notFoundHandler);
app.use(errorHandler);

app.listen(PORT, () => {
  console.log(`CampusOS (JS) listening on http://localhost:${PORT}`);
});

process.on('SIGINT', () => { db.close(); process.exit(0); });

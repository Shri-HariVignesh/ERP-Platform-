import Database from 'better-sqlite3';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// In-memory, exactly like the Java module's `jdbc:h2:mem:...;DB_CLOSE_DELAY=-1` — a restart
// reseeds. Set CAMPUSOS_DB_FILE to persist across restarts instead.
const target = process.env.CAMPUSOS_DB_FILE || ':memory:';

export const db = new Database(target);
// WAL needs a real file; an in-memory database ignores it.
if (target !== ':memory:') db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

const schema = readFileSync(path.join(__dirname, 'schema.sql'), 'utf8');
db.exec(schema);

export default db;

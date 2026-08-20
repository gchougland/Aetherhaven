import fs from "node:fs";
import path from "node:path";
import session from "express-session";
import FileStoreFactory from "session-file-store";

/** Keep players signed in for 30 days (cookie + on-disk session file). */
const SESSION_TTL_SECONDS = 60 * 60 * 24 * 30;
const SESSION_COOKIE_MAX_AGE_MS = SESSION_TTL_SECONDS * 1000;

/**
 * File-backed sessions for production (Railway volume under DATA_DIR).
 * Avoids express-session's default MemoryStore memory leak warning.
 */
export function createSessionMiddleware({ secret, isProduction, dataDir }) {
  const sessionDir = path.join(dataDir, ".sessions");
  fs.mkdirSync(sessionDir, { recursive: true });

  const FileStore = FileStoreFactory(session);
  const store = new FileStore({
    path: sessionDir,
    ttl: SESSION_TTL_SECONDS,
    reapInterval: 3600,
    logFn: () => {},
  });

  return session({
    secret,
    store,
    resave: false,
    saveUninitialized: false,
    rolling: true,
    cookie: {
      httpOnly: true,
      sameSite: "lax",
      secure: isProduction,
      maxAge: SESSION_COOKIE_MAX_AGE_MS,
    },
  });
}

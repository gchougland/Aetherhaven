import fs from "node:fs";
import path from "node:path";
import session from "express-session";
import FileStoreFactory from "session-file-store";

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
    ttl: 86400,
    reapInterval: 3600,
    logFn: () => {},
  });

  return session({
    secret,
    store,
    resave: false,
    saveUninitialized: false,
    cookie: {
      httpOnly: true,
      sameSite: "lax",
      secure: isProduction,
      maxAge: 86400000,
    },
  });
}

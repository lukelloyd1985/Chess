import { TablesDB, Users, Query } from "node-appwrite";
import { DATABASE_ID, GAMES_TABLE_ID, GameRow, serverClient } from "./games";
import { listAllRows } from "./listAll";
import type { FunctionContext } from "./context";

/**
 * Deletes the caller's own account: every friend game they are seated in, then their Appwrite
 * Auth user. Invoked by the app via functions.createExecution() (never a direct client write)
 * because one user must never be able to delete another's account, and only this trusted
 * server-side function (using the per-execution API key) can delete an Auth user.
 *
 * HTTP invocation - see main.ts.
 */
export async function deleteAccount({ req, res, log, error }: FunctionContext) {
  // Appwrite injects the calling user's ID for an authenticated execution; never trust the body.
  const uid = req.headers["x-appwrite-user-id"];
  if (!uid) {
    return res.json({ success: false, message: "You must be signed in to delete your account." }, 401);
  }

  const client = serverClient(req.headers);
  const tablesDB = new TablesDB(client);
  const users = new Users(client);

  const [asWhite, asBlack] = await Promise.all([
    listAllRows<GameRow>(tablesDB, DATABASE_ID, GAMES_TABLE_ID, [Query.equal("whiteUid", uid)]),
    listAllRows<GameRow>(tablesDB, DATABASE_ID, GAMES_TABLE_ID, [Query.equal("blackUid", uid)]),
  ]);
  const games = [...new Map([...asWhite, ...asBlack].map((g) => [g.$id, g])).values()];
  await Promise.all(
    games.map((g) => tablesDB.deleteRow({ databaseId: DATABASE_ID, tableId: GAMES_TABLE_ID, rowId: g.$id })),
  );

  try {
    await users.delete(uid);
  } catch (err) {
    error(
      `Deleted games for ${uid} but failed to delete their Auth account: ${
        err instanceof Error ? err.stack ?? err.message : err
      }`,
    );
    return res.json(
      { success: false, message: "Your games were deleted, but removing your sign-in account failed. Please contact support." },
      500,
    );
  }

  log(`Deleted account and ${games.length} game(s) for ${uid}`);
  return res.json({ success: true });
}

import { AppwriteException, TablesDB, Users } from "node-appwrite";
import { DATABASE_ID, GAMES_TABLE_ID, GameRow, gamePermissions, serverClient } from "./games";
import type { FunctionContext } from "./context";

/**
 * Seats the signed-in caller in the open seat of the friend game whose share code is in the
 * request body ({ "code": "ABC123" }). This runs server-side because a joining player has no
 * permission on the row until they are seated: the function records them and widens the row's
 * permissions so both players can read and update it.
 *
 * HTTP-invoked at path "/join-game" - see main.ts.
 */
export async function joinGame({ req, res, error }: FunctionContext) {
  const uid = req.headers["x-appwrite-user-id"];
  if (!uid) {
    return res.json({ success: false, message: "You must be signed in to join a game." }, 401);
  }
  const rawCode = (req.bodyJson as { code?: string } | undefined)?.code;
  const code = String(rawCode ?? "").trim().toUpperCase();
  if (!/^[A-Z0-9]{6}$/.test(code)) {
    return res.json({ success: false, message: "That is not a valid game code." }, 400);
  }

  const client = serverClient(req.headers);
  const tablesDB = new TablesDB(client);
  const users = new Users(client);

  let game: GameRow;
  try {
    game = await tablesDB.getRow<GameRow>({ databaseId: DATABASE_ID, tableId: GAMES_TABLE_ID, rowId: code });
  } catch (err) {
    if (err instanceof AppwriteException && err.code === 404) {
      return res.json({ success: false, message: `No game with code ${code}.` }, 404);
    }
    error(`joinGame: failed to load ${code}: ${err instanceof Error ? err.stack ?? err.message : err}`);
    return res.json({ success: false, message: "Could not load that game." }, 500);
  }

  if (game.whiteUid === uid || game.blackUid === uid) {
    return res.json({ success: true, code }); // already seated: rejoin
  }
  if (game.whiteUid && game.blackUid) {
    return res.json({ success: false, message: "That game already has two players." }, 409);
  }

  try {
    const user = await users.get({ userId: uid });
    const name = user.name || user.email || "Player";
    const seatWhite = !game.whiteUid;
    const creatorUid = (seatWhite ? game.blackUid : game.whiteUid) ?? uid;
    await tablesDB.updateRow({
      databaseId: DATABASE_ID,
      tableId: GAMES_TABLE_ID,
      rowId: code,
      data: seatWhite
        ? { whiteUid: uid, whiteName: name, status: "active" }
        : { blackUid: uid, blackName: name, status: "active" },
      permissions: gamePermissions(creatorUid, uid),
    });
  } catch (err) {
    error(`joinGame: failed to seat ${uid} in ${code}: ${err instanceof Error ? err.stack ?? err.message : err}`);
    return res.json({ success: false, message: "Could not join that game." }, 500);
  }
  return res.json({ success: true, code });
}

import { Client, Permission, Role, Models } from "node-appwrite";

export const DATABASE_ID = process.env.APPWRITE_DATABASE_ID ?? "chess";
export const GAMES_TABLE_ID = process.env.APPWRITE_COLLECTION_GAMES_ID ?? "games";

export interface GameRow extends Models.Row {
  whiteUid?: string;
  blackUid?: string;
  whiteName?: string;
  blackName?: string;
  moves?: string; // space-separated UCI moves
  status?: "waiting" | "active" | "finished";
}

export function serverClient(headers: Record<string, string>): Client {
  return new Client()
    .setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT ?? "")
    .setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID ?? "")
    .setKey(headers["x-appwrite-key"] ?? "");
}

/** Both players can read and update the game (the app appends moves directly); only the
 *  creator can delete it. Rebuilt wholesale because per-row permissions are replaced on update. */
export function gamePermissions(creatorUid: string, otherUid?: string): string[] {
  const perms = [
    Permission.read(Role.user(creatorUid)),
    Permission.update(Role.user(creatorUid)),
    Permission.delete(Role.user(creatorUid)),
  ];
  if (otherUid && otherUid !== creatorUid) {
    perms.push(Permission.read(Role.user(otherUid)), Permission.update(Role.user(otherUid)));
  }
  return perms;
}

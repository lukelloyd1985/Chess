import { deleteAccount } from "./deleteAccount";
import { googleSignIn } from "./googleSignIn";
import { joinGame } from "./joinGame";
import type { FunctionContext } from "./context";

/** Single Appwrite Function serving three HTTP routes (kept as one function to fit the
 *  Appwrite Cloud free-tier limit of functions per project):
 *    /google-sign-in  - Credential Manager ID token -> Appwrite custom-token session
 *    /join-game       - seats the signed-in caller in an open friend game
 *    (anything else)  - deletes the signed-in caller's account and games
 *
 *  appwrite.json sets execute permission to "any" because /google-sign-in must be reachable
 *  before a session exists. The other routes independently require the x-appwrite-user-id
 *  header, which Appwrite only injects for an authenticated caller. */
export default async (context: FunctionContext) => {
  const trigger = context.req.headers["x-appwrite-trigger"];
  if (trigger !== "http") {
    context.error(`maintenance: unexpected trigger "${trigger}"`);
    return context.res.json({ success: false, message: `Unexpected trigger "${trigger}"` }, 400);
  }
  switch (context.req.path) {
    case "/google-sign-in":
      return googleSignIn(context);
    case "/join-game":
      return joinGame(context);
    default:
      return deleteAccount(context);
  }
};

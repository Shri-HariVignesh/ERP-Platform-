/**
 * express-session's automatic save-on-finish only fires reliably when the response write
 * happens after the store write completes. A redirect response is small enough that it can
 * reach the client and the connection can be reused for the next request before the session
 * store's async write lands — so a flash/error set immediately before res.redirect() can be
 * silently lost. Forcing an explicit save() closes that race.
 */
export function redirectAfterSave(req, res, url) {
  req.session.save((err) => {
    if (err) console.error('session save failed:', err);
    res.redirect(url);
  });
}

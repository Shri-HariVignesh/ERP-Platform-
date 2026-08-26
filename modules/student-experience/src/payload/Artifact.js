/** A side-effect made visible: verify id, serial number, a link to a generated doc. */
export function artifact(kind, label, value, href = null) {
  return { kind, label, value, href };
}

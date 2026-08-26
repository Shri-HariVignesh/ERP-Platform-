export class IllegalTransitionException extends Error {
  constructor(message) {
    super(message);
    this.name = 'IllegalTransitionException';
  }
}

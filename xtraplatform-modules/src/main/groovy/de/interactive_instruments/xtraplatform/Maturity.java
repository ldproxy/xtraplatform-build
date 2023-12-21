package de.interactive_instruments.xtraplatform;

public enum Maturity {
  PROPOSAL,
  CANDIDATE,
  MATURE;

  public Badge toBadge() {
    switch (this) {
      case MATURE:
        return Badge.TIP;
      case CANDIDATE:
        return Badge.INFO;
      case PROPOSAL:
      default:
        return Badge.WARNING;
    }
  }
}

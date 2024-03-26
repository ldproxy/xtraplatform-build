package de.interactive_instruments.xtraplatform;

public enum Maintenance {
  NONE,
  LOW,
  FULL;

  public Badge toBadge() {
    switch (this) {
      case FULL:
        return Badge.TIP;
      case LOW:
        return Badge.INFO;
      case NONE:
      default:
        return Badge.WARNING;
    }
  }

  public boolean isLowerThan(Maintenance other) {
    return this.ordinal() < other.ordinal();
  }
}

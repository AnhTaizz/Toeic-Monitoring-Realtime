package vn.edu.toeic.protocol.admin;

/** Only the create response may carry the independently generated Proctor credential. */
public record CreatedAccount(AccountView account, String initialPassword) {
    @Override public String toString() { return "CreatedAccount[account=" + account + ", initialPassword=<redacted>]"; }
}

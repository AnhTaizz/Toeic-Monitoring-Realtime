package vn.edu.toeic.protocol.exam.rich;

public record CreatedPinRoom(CreateRichSessionResponse session, String pin) {
    @Override public String toString() { return "CreatedPinRoom[session="+session+", pin=<redacted>]"; }
}

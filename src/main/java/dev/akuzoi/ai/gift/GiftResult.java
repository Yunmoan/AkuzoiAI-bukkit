package dev.akuzoi.ai.gift;

public record GiftResult(boolean given, String description, String failureMessage) {
    public static GiftResult none() {
        return new GiftResult(false, "", "");
    }

    public static GiftResult given(String description) {
        return new GiftResult(true, description, "");
    }

    public static GiftResult failed(String failureMessage) {
        return new GiftResult(false, "", failureMessage);
    }
}

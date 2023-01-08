package com.android.resources;

public enum GrammaticalGender implements ResourceEnum {
    NEUTER("neuter", "Neuter", "Neuter"),
    FEMININE("feminine", "Feminine", "Feminine"),
    MASCULINE("masculine", "Masculine", "Masculine");

    private final String mValue;
    private final String mShortDisplayValue;
    private final String mLongDisplayValue;

    GrammaticalGender(String value, String shortDisplayValue, String longDisplayValue) {
        mValue = value;
        mShortDisplayValue = shortDisplayValue;
        mLongDisplayValue = longDisplayValue;
    }

    /**
     * Returns the enum for matching the provided qualifier value.
     *
     * @param value The qualifier value.
     * @return the enum for the qualifier value or null if no matching was found.
     */
    public static GrammaticalGender getEnum(String value) {
        for (GrammaticalGender state : values()) {
            if (state.mValue.equals(value)) {
                return state;
            }
        }

        return null;
    }

    @Override
    public String getResourceValue() {
        return mValue;
    }

    @Override
    public String getShortDisplayValue() {
        return mShortDisplayValue;
    }

    @Override
    public String getLongDisplayValue() {
        return mLongDisplayValue;
    }

    public static int getIndex(GrammaticalGender value) {
        return value == null ? -1 : value.ordinal();
    }

    public static GrammaticalGender getByIndex(int index) {
        GrammaticalGender[] values = values();
        if (index >= 0 && index < values.length) {
            return values[index];
        }
        return null;
    }

    @Override
    public boolean isFakeValue() {
        return false;
    }

    @Override
    public boolean isValidValueForDevice() {
        return true;
    }
}
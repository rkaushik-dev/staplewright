package io.github.rkaushikdev.staplewright.keys;

/**
 * What the code machine is doing wrong today, in thousandths of an inch.
 *
 * <p>A carriage out of calibration walks every cut along the blade by the same amount, and a cutter
 * worn small makes every cut shallow by the same amount. Both are constant offsets, which is why
 * measuring one key catches them.
 *
 * @param spaceOffsetMils how far along the blade every cut lands from where it should
 * @param depthOffsetMils how far from the chart dimension every cut comes down
 */
public record Calibration(int spaceOffsetMils, int depthOffsetMils) {

    /** A machine cutting exactly to the chart. */
    public static final Calibration TRUE = new Calibration();

    /** A machine cutting exactly to the chart. */
    public Calibration() {
        this(0, 0);
    }

    public Calibration withSpaceOffsetMils(int value) {
        return new Calibration(value, depthOffsetMils);
    }

    public Calibration withDepthOffsetMils(int value) {
        return new Calibration(spaceOffsetMils, value);
    }

    /**
     * The offsets that cancel what a test key cut under these ones measured.
     *
     * <p>A key whose cuts landed two mils toward the tip calls for the carriage to be set two mils
     * back, on top of whatever it was set to already.
     *
     * @param spaceErrorMils how far along the blade the test cuts landed from their chart spaces
     * @param depthErrorMils how far from the chart dimension the test cuts came down
     */
    public Calibration correctedBy(int spaceErrorMils, int depthErrorMils) {
        return new Calibration(spaceOffsetMils - spaceErrorMils, depthOffsetMils - depthErrorMils);
    }

    /** Whether the machine has nothing wrong with it. */
    public boolean isTrue() {
        return spaceOffsetMils == 0 && depthOffsetMils == 0;
    }
}

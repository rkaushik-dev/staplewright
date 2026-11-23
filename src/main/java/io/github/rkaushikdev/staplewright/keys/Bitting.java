package io.github.rkaushikdev.staplewright.keys;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Literal;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * An ordered run of cut depths, counted from the bow toward the tip.
 *
 * <p>The shop writes one down as a run of digits, so 342156 is a six chamber key whose first cut
 * is a three. That works until a chart goes past nine depths, and two of the three manufacturers on
 * campus do, so the parser also reads a dotted form: 10.4.12.3 is four cuts, the first ten deep.
 *
 * <p>Nothing here knows whether a bitting is legal. Legality belongs to the chart it is cut
 * against. A bitting is only the number, held so it cannot be edited by accident and so two of
 * them can be compared without arguing about white space.
 *
 * <p>Chambers are numbered from one because that is how the pinning chart on the bench is
 * numbered, and an off by one between the chart and the screen costs a cylinder.
 */
public final class Bitting implements Comparable<Bitting> {

    /** Stands for any depth in a pattern. The shop writes it lower case. */
    public static final String WILDCARD = "x";

    private final List<Integer> depths;

    /**
     * A bitting of these depths, bow first.
     *
     * @throws InputError for no cuts, a cut that is not a whole number, or a negative cut
     */
    public Bitting(Collection<Integer> depths) {
        if (depths.isEmpty()) {
            throw new InputError("a bitting needs at least one cut");
        }
        int position = 1;
        for (Integer depth : depths) {
            if (depth == null) {
                throw new InputError("a cut depth is a whole number", Facts.of("chamber", position, "got", null));
            }
            if (depth < 0) {
                throw new InputError("a cut depth cannot be negative", Facts.of("chamber", position, "depth", depth));
            }
            position++;
        }
        this.depths = List.copyOf(depths);
    }

    /** A bitting of these depths, bow first. */
    public static Bitting of(int... depths) {
        return new Bitting(Arrays.stream(depths).boxed().toList());
    }

    /**
     * Read either the packed digit form or the dotted form. Spaces are dropped and dashes read as
     * dots.
     */
    public static Bitting parse(String text) {
        if (text == null) {
            throw new InputError("a bitting is written as text", Facts.of("got", "NoneType"));
        }
        String cleaned = Text.strip(text).replace(" ", "").replace("-", ".");
        if (cleaned.isEmpty()) {
            throw new InputError("a bitting needs at least one cut");
        }
        if (cleaned.contains(".")) {
            String[] parts = cleaned.split("\\.", -1);
            if (Arrays.stream(parts).anyMatch(String::isEmpty)) {
                throw new InputError("the dotted form has an empty cut in it", Facts.of("text", text));
            }
            if (!Arrays.stream(parts).allMatch(Text::isDigits)) {
                throw new InputError("a cut depth is a whole number", Facts.of("text", text));
            }
            return new Bitting(Arrays.stream(parts).map(Bitting::whole).toList());
        }
        if (!Text.isDigits(cleaned)) {
            throw new InputError("the packed form takes digits only", Facts.of("text", text));
        }
        return new Bitting(
                cleaned.chars().map(Character::getNumericValue).boxed().toList());
    }

    private static int whole(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException tooLong) {
            throw new InputError("a cut depth is a whole number", Facts.of("text", digits));
        }
    }

    /** The depths, bow first. */
    public List<Integer> depths() {
        return depths;
    }

    /** How many cuts there are. */
    public int chambers() {
        return depths.size();
    }

    /** The depth in one chamber, counted from one. */
    public int at(int chamber) {
        if (chamber < 1 || chamber > depths.size()) {
            throw new InputError(
                    "no such chamber on this key", Facts.of("chamber", chamber, "chambers", depths.size()));
        }
        return depths.get(chamber - 1);
    }

    /** The same key with one cut changed. Returns a new bitting. */
    public Bitting withDepth(int chamber, int depth) {
        at(chamber);
        List<Integer> cuts = new ArrayList<>(depths);
        cuts.set(chamber - 1, depth);
        return new Bitting(cuts);
    }

    /**
     * Every key carrying one of the given depths in each chamber, bow first.
     *
     * <p>The last chamber turns over fastest and the first slowest, which is the order a printed
     * array and a cylinder's operating key list both read in. The keys are made as the stream is
     * read, so a long list is never held whole.
     *
     * @param depths the depths allowed in each chamber, one run a chamber, bow first
     * @throws InputError for no chambers, or a chamber allowing nothing
     */
    public static Stream<Bitting> everyCombination(List<List<Integer>> depths) {
        if (depths.isEmpty() || depths.stream().anyMatch(List::isEmpty)) {
            throw new InputError("every chamber needs at least one depth", Facts.of("chambers", depths.size()));
        }
        List<List<Integer>> runs = depths.stream().map(List::copyOf).toList();
        int total = runs.stream().mapToInt(List::size).reduce(1, Math::multiplyExact);
        return IntStream.range(0, total).mapToObj(index -> {
            Integer[] cuts = new Integer[runs.size()];
            int rest = index;
            for (int at = runs.size() - 1; at >= 0; at--) {
                List<Integer> run = runs.get(at);
                cuts[at] = run.get(rest % run.size());
                rest /= run.size();
            }
            return new Bitting(Arrays.asList(cuts));
        });
    }

    /** Which chambers the two keys disagree in, counted from one. */
    public List<Integer> differsFrom(Bitting other) {
        if (chambers() != other.chambers()) {
            throw new InputError(
                    "two keys of different lengths cannot be compared cut for cut",
                    Facts.of("left", chambers(), "right", other.chambers()));
        }
        return IntStream.rangeClosed(1, chambers())
                .filter(position -> !depths.get(position - 1).equals(other.depths.get(position - 1)))
                .boxed()
                .toList();
    }

    /** The absolute step between each neighbouring pair of cuts. */
    public List<Integer> adjacentDifferences() {
        return IntStream.range(1, depths.size())
                .map(index -> Math.abs(depths.get(index) - depths.get(index - 1)))
                .boxed()
                .toList();
    }

    public int deepest() {
        return depths.stream().mapToInt(Integer::intValue).max().orElseThrow();
    }

    public int shallowest() {
        return depths.stream().mapToInt(Integer::intValue).min().orElseThrow();
    }

    /** Write it back out the way the shop writes it: packed, or dotted once a cut passes nine. */
    public String render() {
        return render(false);
    }

    /** Write it back out, dotted when asked to or when a cut passes nine. */
    public String render(boolean dotted) {
        String separator = dotted || depths.stream().anyMatch(depth -> depth > 9) ? "." : "";
        return depths.stream().map(String::valueOf).collect(Collectors.joining(separator));
    }

    /** The depth at a position counted from nought, as a list index; negative counts from the tip. */
    public int get(int index) {
        return depths.get(index < 0 ? depths.size() + index : index);
    }

    /** How many cuts there are, the same as {@link #chambers()}. */
    public int size() {
        return depths.size();
    }

    /** Cut by cut from the bow, the first difference deciding; a key that runs out first is less. */
    @Override
    public int compareTo(Bitting other) {
        for (int index = 0; index < Math.min(chambers(), other.chambers()); index++) {
            int compared = Integer.compare(depths.get(index), other.depths.get(index));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(chambers(), other.chambers());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Bitting that && depths.equals(that.depths);
    }

    @Override
    public int hashCode() {
        return depths.hashCode();
    }

    @Override
    public String toString() {
        return "Bitting(" + Literal.quoted(render()) + ")";
    }

    /**
     * Read a bitting with holes in it, the way a keying schedule writes one.
     *
     * <p>A master cut that has not been chosen yet is written as an x. The result has one entry per
     * chamber, and an empty one where the x was.
     */
    public static List<Optional<Integer>> parsePattern(String text, int chambers) {
        String cleaned = Text.strip(text).replace(" ", "").replace("-", ".");
        List<String> parts = cleaned.contains(".")
                ? Arrays.asList(cleaned.split("\\.", -1))
                : cleaned.chars().mapToObj(Character::toString).toList();
        if (parts.size() != chambers) {
            throw new InputError(
                    "the pattern is not as long as the system is",
                    Facts.of("pattern", text, "chambers", chambers, "got", parts.size()));
        }
        List<Optional<Integer>> out = new ArrayList<>();
        int position = 1;
        for (String part : parts) {
            if (Text.lower(part).equals(WILDCARD)) {
                out.add(Optional.empty());
            } else if (Text.isDigits(part)) {
                out.add(Optional.of(whole(part)));
            } else {
                throw new InputError("a pattern takes depths or an x", Facts.of("chamber", position, "got", part));
            }
            position++;
        }
        return List.copyOf(out);
    }

    /** Whether a key fits a pattern, ignoring the chambers left open. */
    public static boolean matchesPattern(Bitting bitting, List<Optional<Integer>> pattern) {
        if (bitting.chambers() != pattern.size()) {
            return false;
        }
        for (int index = 0; index < pattern.size(); index++) {
            Optional<Integer> wanted = pattern.get(index);
            if (wanted.isPresent() && !wanted.get().equals(bitting.depths.get(index))) {
                return false;
            }
        }
        return true;
    }
}

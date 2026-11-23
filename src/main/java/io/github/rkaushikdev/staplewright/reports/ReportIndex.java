package io.github.rkaushikdev.staplewright.reports;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.util.Records;
import io.github.rkaushikdev.staplewright.util.Text;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Every page the shop prints, listed once and checked against itself.
 *
 * <p>There are about fifty reports across nine report modules and nobody could name half of them.
 * The shop asks for a page by describing it, somebody writes a new one because they cannot find the
 * old one, and a year later there are two answers to the same question that round differently.
 *
 * <p>So this is the index, and it is not documentation. Every row names a real report method, there
 * is a check that says so, and there is a second check that says every report method is on a row. A
 * page that is not on this list either gets added to it or gets deleted, and both of those are
 * better than a page nobody can find.
 *
 * <p>A page is printed under its name in words joined by underscores, {@code survey_report}, and the
 * report method that prints it is the same words run together, {@code SurveyPages.surveyReport}.
 */
public final class ReportIndex {

    /** The report modules, which is where a page is allowed to live, in name order. */
    public static final List<String> MODULES =
            List.of("annual", "control", "escrow", "pinning", "rekey", "schedule", "statistics", "stock", "survey");

    /** Public report methods that are not pages, by their printed name. Kept short on purpose. */
    public static final Set<String> NOT_A_REPORT = Collections.unmodifiableSet(new TreeSet<>(List.of(
            // Measurements and shapes that other reports print, rather than pages.
            "reconcile",
            "take",
            "is_master",
            "masters_out",
            "unaccounted_keys",
            "concentration",
            "worst_buildings",
            "blocking_deficiencies",
            "state_counts",
            "campus_numbers",
            "exposure",
            "keys_by_building",
            "expiry_of",
            // Single lines that a page above them assembles.
            "key_line",
            "chart_body",
            "key_lines",
            "stack_row",
            "schedule_line")));

    private static final Map<String, Class<?>> CLASSES = classes();

    /** Every page, in module order then in the order somebody reads them. */
    public static final List<Page> PAGES = List.of(
            new Page("annual", "annual_return", "risk", "the whole key control return"),
            new Page("annual", "master_table", "risk", "every master out and who has it"),
            new Page("annual", "unaccounted_table", "risk", "keys nobody can produce"),
            new Page("annual", "returns_table", "risk", "several years side by side"),
            new Page("annual", "movement", "risk", "what changed against last year"),
            new Page("control", "keyholder_statement", "counter", "the sheet a keyholder signs"),
            new Page("control", "deposit_statement", "counter", "deposits against one person"),
            new Page("control", "department_summary", "counter", "who in a department holds what"),
            new Page("control", "overdue_report", "counter", "the chase list"),
            new Page("control", "notice_run", "counter", "what the notices run would send"),
            new Page("escrow", "coverage_sheet", "fire service", "where the boxes are"),
            new Page("escrow", "exposure_sheet", "shop", "what is open and for how long"),
            new Page("escrow", "walk_sheet", "shop", "the list to carry round the campus"),
            new Page("escrow", "seal_sheet", "shop", "every seal one box has carried"),
            new Page("escrow", "overdue_sheet", "shop", "boxes past their reseal window"),
            new Page("escrow", "shop_report", "shop", "the whole box position"),
            new Page("escrow", "key_found", "shop", "which boxes hold a symbol"),
            new Page("pinning", "cylinder_chart", "bench", "the sheet propped against the vice"),
            new Page("pinning", "parts_summary", "bench", "what to draw out of the cabinet"),
            new Page("pinning", "core_chart", "bench", "a core, with its control shear line"),
            new Page("rekey", "incident_sheet", "shop", "one lost key and what it opens"),
            new Page("rekey", "scope_sheet", "shop", "how far a rekey has to reach"),
            new Page("rekey", "cost_sheet", "budget", "what a rekey costs"),
            new Page("rekey", "order_packet", "technician", "the paper that goes out with a crew"),
            new Page("rekey", "line_sheet", "technician", "one work order line by line"),
            new Page("rekey", "packet", "technician", "an order and its lines together"),
            new Page("schedule", "schedule_report", "shop", "the whole keying schedule"),
            new Page("schedule", "bitting_list", "bench", "every symbol and its cut"),
            new Page("schedule", "tree_report", "shop", "the schedule as a tree"),
            new Page("schedule", "cross_reference", "shop", "which doors a symbol turns"),
            new Page("schedule", "open_symbols_report", "shop", "symbols drawn and not cut"),
            new Page("statistics", "summary", "management", "the campus in eight numbers"),
            new Page("statistics", "keys_by_department", "management", "who holds what, by department"),
            new Page("statistics", "incidents_by_year", "management", "lost keys year by year"),
            new Page("statistics", "lockout_pressure", "management", "which doors keep locking people out"),
            new Page("stock", "reconciliation_sheet", "factory", "one part, opening to closing"),
            new Page("stock", "movement_log", "factory", "every movement of one part"),
            new Page("stock", "shrinkage", "factory", "parts that do not account for themselves"),
            new Page("stock", "reorder_sheet", "shop", "what to put on the next order"),
            new Page("stock", "outstanding_orders", "shop", "what is still coming"),
            new Page("stock", "restricted_position", "shop", "restricted brass in the building"),
            new Page("stock", "stock_report", "factory", "the whole blank stock pack"),
            new Page("survey", "condition_table", "management", "how many doors are in each state"),
            new Page("survey", "deficiency_table", "management", "what a walk round found"),
            new Page("survey", "life_safety", "management", "doors that need a key to get out"),
            new Page("survey", "blocking_work", "shop", "doors that need work before a rekey"),
            new Page("survey", "unwalked", "shop", "doors nobody looked at"),
            new Page("survey", "inventory", "shop", "every door on a survey"),
            new Page("survey", "survey_report", "management", "the whole survey"));

    private static final Map<String, Page> BY_NAME = byName();

    private ReportIndex() {}

    private static Map<String, Class<?>> classes() {
        Map<String, Class<?>> found = new LinkedHashMap<>();
        found.put("annual", AnnualPages.class);
        found.put("control", ControlPages.class);
        found.put("escrow", EscrowPages.class);
        found.put("pinning", PinningPages.class);
        found.put("rekey", RekeyPages.class);
        found.put("schedule", SchedulePages.class);
        found.put("statistics", StatisticsPages.class);
        found.put("stock", StockPages.class);
        found.put("survey", SurveyPages.class);
        return Collections.unmodifiableMap(found);
    }

    private static Map<String, Page> byName() {
        Map<String, Page> found = new LinkedHashMap<>();
        for (Page item : PAGES) {
            found.put(item.name(), item);
        }
        return Collections.unmodifiableMap(found);
    }

    /**
     * The page printed under {@code name}.
     *
     * @throws NotFoundError when nothing is printed under that name
     */
    public static Page page(String name) {
        Page found = BY_NAME.get(name);
        if (found == null) {
            throw new NotFoundError("nothing is printed under that name", Facts.of("report", name));
        }
        return found;
    }

    /** Every page's name, in name order. */
    public static List<String> names() {
        return BY_NAME.keySet().stream().sorted().toList();
    }

    /** The pages one module prints, in index order. */
    public static List<Page> forModule(String module) {
        return PAGES.stream().filter(item -> item.module().equals(module)).toList();
    }

    /** Every page one kind of person asks for, which is how they arrive. */
    public static List<Page> forReader(String askedBy) {
        return PAGES.stream().filter(item -> item.askedBy().equals(askedBy)).toList();
    }

    /** Everybody who asks for a page, each once, in name order. */
    public static List<String> readers() {
        return PAGES.stream().map(Page::askedBy).distinct().sorted().toList();
    }

    /** The report modules there are. */
    public static List<String> everyModule() {
        return MODULES;
    }

    /** Public report methods in a module that are not on the index, by printed name, in name order. */
    public static List<String> undocumented(String module) {
        return undocumented(module, forModule(module).stream().map(Page::name).toList());
    }

    /** Public report methods in a module that are not among {@code listed}, by printed name. */
    static List<String> undocumented(String module, Collection<String> listed) {
        return reportMethods(module).stream()
                .map(method -> snake(method.getName()))
                .filter(name -> !listed.contains(name) && !NOT_A_REPORT.contains(name))
                .collect(Collectors.toCollection(TreeSet::new))
                .stream()
                .toList();
    }

    /** Every public static method a report module declares. */
    static List<Method> reportMethods(String module) {
        Class<?> found = CLASSES.get(module);
        if (found == null) {
            throw new NotFoundError("no report module goes by that name", Facts.of("module", module));
        }
        return Arrays.stream(found.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())
                        && Modifier.isStatic(method.getModifiers())
                        && !method.isSynthetic())
                .sorted(Comparator.comparing(Method::getName))
                .toList();
    }

    static Method resolve(Page page) {
        String wanted = Records.camel(page.name());
        return reportMethods(page.module()).stream()
                .filter(method -> method.getName().equals(wanted))
                .max(Comparator.comparingInt(Method::getParameterCount))
                .orElseThrow(() -> new NotFoundError(
                        "no report method prints that page", Facts.of("module", page.module(), "report", page.name())));
    }

    /** {@code surveyReport} as a printed name, {@code survey_report}. */
    static String snake(String camel) {
        StringBuilder out = new StringBuilder();
        for (char at : camel.toCharArray()) {
            if (Character.isUpperCase(at)) {
                out.append('_').append(Character.toLowerCase(at));
            } else {
                out.append(at);
            }
        }
        return out.toString();
    }

    /** The whole index, grouped by module. */
    public static List<String> indexSheet() {
        return indexSheet("");
    }

    /** The index, or the part of it one module prints, which is the page this class exists to print. */
    public static List<String> indexSheet(String module) {
        List<Page> wanted = module.isEmpty() ? PAGES : forModule(module);
        if (wanted.isEmpty()) {
            return List.of("nothing is printed out of " + module);
        }
        List<String> lines = new ArrayList<>();
        lines.add(wanted.size() + " reports");
        String seen = "";
        for (Page item : wanted) {
            if (!item.module().equals(seen)) {
                lines.add(item.module());
                seen = item.module();
            }
            lines.add("  " + item.describe());
        }
        return List.copyOf(lines);
    }

    /** Every page one kind of person asks for, with what each answers. */
    public static List<String> readerSheet(String askedBy) {
        List<Page> wanted = forReader(askedBy);
        if (wanted.isEmpty()) {
            return List.of("nobody described as " + askedBy + " asks for anything");
        }
        List<String> lines = new ArrayList<>();
        lines.add(wanted.size() + " reports for " + askedBy);
        for (Page item : wanted) {
            lines.add("  " + Text.left(item.name(), 24) + item.answers());
        }
        return List.copyOf(lines);
    }
}

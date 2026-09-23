package com.opencrm.common.event;

import org.apache.kafka.clients.CommonClientConfigs;

import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Command-line triage of dead-letter topics (ADR-018 D7), run as an admin process inside any service image that
 * consumes Kafka, so it needs no new HTTP endpoint:
 *
 * <pre>
 * java -Dloader.main=com.opencrm.common.event.DeadLetterTool -cp app.jar \
 *      org.springframework.boot.loader.launch.PropertiesLauncher depth cpq.quote.status.accepted
 * </pre>
 *
 * Commands take a topic or its DLT ({@code .DLT} is added when missing):
 * <ul>
 *   <li>{@code depth <topic>}: untriaged records per partition;</li>
 *   <li>{@code peek <topic> [--max N]}: the next untriaged records, changing nothing;</li>
 *   <li>{@code replay <topic> [--max N] [--dry-run]}: re-publish them to their original topic and mark them triaged;</li>
 *   <li>{@code skip <topic> [--max N]}: mark them triaged without re-publishing.</li>
 * </ul>
 * Brokers come from {@code --bootstrap-servers}, else {@code KAFKA_BOOTSTRAP_SERVERS}, else
 * {@code SPRING_KAFKA_BOOTSTRAP_SERVERS}, else {@code localhost:9092}. See {@link DeadLetterTriage} for the model and
 * {@code docs/runbooks/event-operations.md} for the procedure.
 */
public final class DeadLetterTool {

    static final int DEFAULT_MAX = 20;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private DeadLetterTool() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.getenv(), System.out, System.err));
    }

    /** Parsed command line. */
    record Command(String action, String dltTopic, int max, boolean dryRun, String bootstrapServers) {
    }

    /** Runs one command and returns the process exit code: 0 done, 1 failed, 2 usage error. Public for tests. */
    public static int run(String[] args, Map<String, String> env, PrintStream out, PrintStream err) {
        Command command;
        try {
            command = parse(args, env);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            err.println(usage());
            return 2;
        }
        DeadLetterTriage triage = new DeadLetterTriage(
                Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, command.bootstrapServers()), TIMEOUT);
        try {
            switch (command.action()) {
                case "depth" -> {
                    Map<Integer, Long> depth = triage.depth(command.dltTopic());
                    depth.forEach((partition, count) ->
                            out.printf("%s-%d untriaged=%d%n", command.dltTopic(), partition, count));
                    out.printf("%s depth=%d%n", command.dltTopic(),
                            depth.values().stream().mapToLong(Long::longValue).sum());
                }
                case "peek" -> print(out, triage.peek(command.dltTopic(), command.max()));
                case "replay" -> {
                    DeadLetterTriage.Outcome outcome =
                            triage.replay(command.dltTopic(), command.max(), command.dryRun());
                    print(out, outcome.records());
                    out.printf("%s %d record(s) to their original topic; %d untriaged remain on %s%n",
                            command.dryRun() ? "DRY RUN: would replay" : "replayed", outcome.records().size(),
                            outcome.remaining(), command.dltTopic());
                }
                case "skip" -> {
                    DeadLetterTriage.Outcome outcome = triage.skip(command.dltTopic(), command.max());
                    print(out, outcome.records());
                    out.printf("skipped %d record(s); %d untriaged remain on %s%n",
                            outcome.records().size(), outcome.remaining(), command.dltTopic());
                }
                default -> throw new IllegalStateException(command.action());
            }
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println("interrupted");
            return 1;
        } catch (Exception e) {
            err.println(command.action() + " failed: " + e);
            return 1;
        }
    }

    static Command parse(String[] args, Map<String, String> env) {
        if (args.length < 2) {
            throw new IllegalArgumentException("a command and a topic are required");
        }
        String action = args[0];
        if (!List.of("depth", "peek", "replay", "skip").contains(action)) {
            throw new IllegalArgumentException("unknown command: " + action);
        }
        int max = DEFAULT_MAX;
        boolean dryRun = false;
        String bootstrap = firstNonBlank(env.get("KAFKA_BOOTSTRAP_SERVERS"), env.get("SPRING_KAFKA_BOOTSTRAP_SERVERS"),
                "localhost:9092");
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--max" -> max = positive(value(args, ++i, "--max"));
                case "--dry-run" -> dryRun = true;
                case "--bootstrap-servers" -> bootstrap = value(args, ++i, "--bootstrap-servers");
                default -> throw new IllegalArgumentException("unknown option: " + args[i]);
            }
        }
        if (dryRun && !"replay".equals(action)) {
            throw new IllegalArgumentException("--dry-run applies to replay only");
        }
        return new Command(action, DeadLetterTriage.deadLetterTopic(args[1]), max, dryRun, bootstrap);
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[index];
    }

    private static int positive(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw new IllegalArgumentException("--max must be a positive integer: " + value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static void print(PrintStream out, List<DeadLetterTriage.DeadLetter> letters) {
        for (DeadLetterTriage.DeadLetter letter : letters) {
            out.printf("%d@%d key=%s from=%s group=%s eventId=%s%n    %s: %s%n    value=%s%n",
                    letter.partition(), letter.offset(), letter.key(), letter.originalTopic(),
                    letter.originalGroup(), letter.eventId(), letter.exception(), letter.exceptionMessage(),
                    letter.valuePreview());
        }
    }

    static String usage() {
        return """
                usage: DeadLetterTool <depth|peek|replay|skip> <topic> [--max N] [--dry-run] [--bootstrap-servers H:P]
                  depth   untriaged records per partition of <topic>.DLT
                  peek    show the next untriaged records (default --max %d); changes nothing
                  replay  re-publish them to their original topic, then mark them triaged (--dry-run: report only)
                  skip    mark them triaged without re-publishing
                """.formatted(DEFAULT_MAX);
    }
}

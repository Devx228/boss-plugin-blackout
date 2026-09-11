import java.util.*;

/** Local rules prototype. No network, authentication, UI, or BOSS integration yet. */
public final class Blackout {
    public enum Team { BLUE, ORANGE; Team opponent() { return this == BLUE ? ORANGE : BLUE; } }
    public enum Role { HUMAN, AGENT }
    public enum Action { REPAIR, SHIELD, ATTACK, CAPTURE }
    public record HumanOrder(Action action, int circuit) {
        public HumanOrder { Objects.requireNonNull(action); }
    }
    public record AgentOrder(int diagnosticCode) {}
    public record Station(int hull, int shield, int energy, int relay) {}
    public record Event(int round, Team team, String detail) {}
    public record RoundRecord(int round, HumanOrder blueHuman, AgentOrder blueAgent,
                              HumanOrder orangeHuman, AgentOrder orangeAgent) {}
    public record View(int round, Role role, Station own, Station opponent,
                       Integer circuit, List<Integer> manual, boolean humanReady,
                       boolean agentReady, boolean finished) {}

    private final long seed;
    private final EnumMap<Team, Station> stations = new EnumMap<>(Team.class);
    private final EnumMap<Team, HumanOrder> humans = new EnumMap<>(Team.class);
    private final EnumMap<Team, AgentOrder> agents = new EnumMap<>(Team.class);
    private final List<Event> events = new ArrayList<>();
    private final List<RoundRecord> history = new ArrayList<>();
    private int round = 1;
    private boolean finished;

    public Blackout(long seed) {
        this.seed = seed;
        for (Team team : Team.values()) stations.put(team, new Station(10, 0, 5, 0));
    }

    // Both crews face equivalent puzzles; seeded permutations vary between rounds.
    private List<Integer> manual() {
        List<Integer> codes = new ArrayList<>(List.of(11, 23, 37, 49));
        Collections.shuffle(codes, new Random(seed ^ (round * 7919L)));
        return List.copyOf(codes);
    }
    private int circuit() { return new Random(seed ^ (round * 104729L)).nextInt(4); }

    public synchronized View view(Team team, Role role) {
        Objects.requireNonNull(team); Objects.requireNonNull(role);
        return new View(round, role, stations.get(team), stations.get(team.opponent()),
                role == Role.HUMAN ? circuit() : null,
                role == Role.AGENT ? manual() : List.of(),
                humans.containsKey(team), agents.containsKey(team), finished);
    }

    public synchronized void human(Team team, int expectedRound, HumanOrder order) {
        validate(team, expectedRound); Objects.requireNonNull(order);
        if (humans.containsKey(team)) throw new IllegalStateException("Human already committed");
        if (order.circuit() < 0 || order.circuit() > 3) throw new IllegalArgumentException("Circuit 0..3");
        if (stations.get(team).energy() < cost(order.action())) throw new IllegalArgumentException("Insufficient energy");
        humans.put(team, order);
        resolveIfReady();
    }

    public synchronized void agent(Team team, int expectedRound, AgentOrder order) {
        validate(team, expectedRound); Objects.requireNonNull(order);
        if (agents.containsKey(team)) throw new IllegalStateException("Agent already committed");
        if (!List.of(11, 23, 37, 49).contains(order.diagnosticCode())) throw new IllegalArgumentException("Unknown code");
        agents.put(team, order);
        resolveIfReady();
    }

    private void validate(Team team, int expectedRound) {
        Objects.requireNonNull(team);
        if (finished) throw new IllegalStateException("Match finished");
        if (round != expectedRound) throw new IllegalStateException("Stale round");
    }
    private static int cost(Action action) { return action == Action.ATTACK ? 3 : 2; }

    private void resolveIfReady() {
        if (humans.size() != 2 || agents.size() != 2) return;
        history.add(new RoundRecord(round, humans.get(Team.BLUE), agents.get(Team.BLUE),
                humans.get(Team.ORANGE), agents.get(Team.ORANGE)));
        EnumMap<Team, Station> next = new EnumMap<>(stations);
        EnumMap<Team, Integer> damage = new EnumMap<>(Team.class);
        EnumSet<Team> captures = EnumSet.noneOf(Team.class);
        for (Team team : Team.values()) {
            Station s = stations.get(team);
            HumanOrder h = humans.get(team);
            int energy = s.energy() - cost(h.action());
            boolean matched = h.circuit() == circuit()
                    && agents.get(team).diagnosticCode() == manual().get(circuit());
            int hull = s.hull(), shield = s.shield();
            if (matched) {
                switch (h.action()) {
                    case REPAIR -> hull = Math.min(10, hull + 3);
                    case SHIELD -> shield = Math.min(6, shield + 3);
                    case ATTACK -> damage.put(team.opponent(), 4);
                    case CAPTURE -> captures.add(team);
                }
            }
            events.add(new Event(round, team, h.action() + (matched ? " coordinated" : " failed diagnosis")));
            next.put(team, new Station(hull, shield, Math.min(8, energy + 3), s.relay()));
        }
        for (Team team : Team.values()) {
            Station s = next.get(team);
            int hit = damage.getOrDefault(team, 0), absorbed = Math.min(s.shield(), hit);
            int points = captures.size() == 1 && captures.contains(team) ? 1 : 0;
            next.put(team, new Station(Math.max(0, s.hull() - (hit - absorbed)),
                    s.shield() - absorbed, s.energy(), s.relay() + points));
        }
        stations.putAll(next);
        finished = round == 8 || stations.values().stream().anyMatch(s -> s.hull() == 0 || s.relay() >= 3);
        humans.clear(); agents.clear();
        round++;
    }

    public synchronized String outcome() {
        if (!finished) return "IN_PROGRESS";
        Station b = stations.get(Team.BLUE), o = stations.get(Team.ORANGE);
        if (b.hull() == 0 || o.hull() == 0) return b.hull() == o.hull() ? "DRAW" : b.hull() == 0 ? "ORANGE" : "BLUE";
        int comparison = Integer.compare(b.relay(), o.relay());
        if (comparison == 0) comparison = Integer.compare(b.hull(), o.hull());
        return comparison == 0 ? "DRAW" : comparison > 0 ? "BLUE" : "ORANGE";
    }
    // Full replay is intentionally unavailable until the match ends.
    public synchronized List<RoundRecord> replay() {
        if (!finished) throw new IllegalStateException("Replay available after match");
        return List.copyOf(history);
    }
    public synchronized List<Event> events() { return List.copyOf(events); }
}

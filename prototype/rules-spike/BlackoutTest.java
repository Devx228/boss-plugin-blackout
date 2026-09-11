import java.util.*;
import static java.lang.System.out;

public final class BlackoutTest {
    private static int checks;
    private static void check(boolean condition) {
        checks++; if (!condition) throw new AssertionError("Check " + checks);
    }
    private static void rejects(Runnable work) {
        boolean rejected = false;
        try { work.run(); } catch (IllegalArgumentException | IllegalStateException e) { rejected = true; }
        check(rejected);
    }
    private static void commit(Blackout game, Blackout.Team team, Blackout.Action action) {
        var human = game.view(team, Blackout.Role.HUMAN);
        var agent = game.view(team, Blackout.Role.AGENT);
        game.human(team, human.round(), new Blackout.HumanOrder(action, human.circuit()));
        game.agent(team, human.round(), new Blackout.AgentOrder(agent.manual().get(human.circuit())));
    }
    public static void main(String[] args) {
        var blue = Blackout.Team.BLUE; var orange = Blackout.Team.ORANGE;
        var game = new Blackout(42);
        check(game.view(blue, Blackout.Role.HUMAN).manual().isEmpty());
        check(game.view(blue, Blackout.Role.AGENT).circuit() == null);
        check(game.view(blue, Blackout.Role.HUMAN).own().equals(game.view(orange, Blackout.Role.HUMAN).own()));
        rejects(game::replay);
        rejects(() -> game.human(blue, 0, new Blackout.HumanOrder(Blackout.Action.REPAIR, 0)));
        rejects(() -> game.human(blue, 1, new Blackout.HumanOrder(Blackout.Action.REPAIR, 4)));
        rejects(() -> game.agent(blue, 1, new Blackout.AgentOrder(999)));
        commit(game, blue, Blackout.Action.ATTACK);
        check(game.view(blue, Blackout.Role.HUMAN).round() == 1);
        rejects(() -> game.agent(blue, 1, new Blackout.AgentOrder(11)));
        commit(game, orange, Blackout.Action.SHIELD);
        check(game.view(orange, Blackout.Role.HUMAN).own().hull() == 9);
        check(game.view(orange, Blackout.Role.HUMAN).own().shield() == 0);
        check(game.view(blue, Blackout.Role.HUMAN).round() == 2);
        for (int i = 0; i < 3; i++) {
            commit(game, blue, Blackout.Action.CAPTURE);
            commit(game, orange, Blackout.Action.REPAIR);
        }
        check(game.outcome().equals("BLUE"));
        rejects(() -> game.agent(blue, 5, new Blackout.AgentOrder(11)));
        var replay = new Blackout(42);
        for (var r : game.replay()) {
            // Reverse submission order: resolution must remain identical.
            replay.agent(orange, r.round(), r.orangeAgent());
            replay.human(orange, r.round(), r.orangeHuman());
            replay.agent(blue, r.round(), r.blueAgent());
            replay.human(blue, r.round(), r.blueHuman());
        }
        check(replay.outcome().equals(game.outcome()));
        check(replay.events().equals(game.events()));
        check(replay.view(blue, Blackout.Role.HUMAN).equals(game.view(blue, Blackout.Role.HUMAN)));
        var draw = new Blackout(99);
        for (int i = 0; i < 8; i++) {
            commit(draw, blue, Blackout.Action.CAPTURE);
            commit(draw, orange, Blackout.Action.CAPTURE);
        }
        check(draw.outcome().equals("DRAW"));
        check(draw.view(blue, Blackout.Role.HUMAN).own().relay() == 0);
        var destruction = new Blackout(42);
        for (int i = 0; i < 3; i++) {
            commit(destruction, blue, Blackout.Action.ATTACK);
            commit(destruction, orange, Blackout.Action.ATTACK);
        }
        check(destruction.outcome().equals("DRAW"));
        var failed = new Blackout(42);
        var h = failed.view(blue, Blackout.Role.HUMAN);
        failed.human(blue, 1, new Blackout.HumanOrder(Blackout.Action.ATTACK, (h.circuit() + 1) % 4));
        failed.agent(blue, 1, new Blackout.AgentOrder(11));
        commit(failed, orange, Blackout.Action.REPAIR);
        check(failed.view(orange, Blackout.Role.HUMAN).own().hull() == 10);
        check(failed.events().get(0).detail().contains("failed diagnosis"));
        out.println("PASS: " + checks + " assertions; local deterministic engine only.");
    }
}

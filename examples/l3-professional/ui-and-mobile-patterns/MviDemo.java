import java.util.List;

/** MVI: intents go into a pure reducer that returns a new immutable state; the view renders the state. */
public class MviDemo {

    record SearchState(String query, boolean loading, List<String> results, String error) {     // (1)!
        static SearchState initial() { return new SearchState("", false, List.of(), null); }
    }

    sealed interface Intent {}                                           // (2)!
    record QueryChanged(String text) implements Intent {}
    record ResultsLoaded(String forQuery, List<String> items) implements Intent {}
    record LoadFailed(String forQuery, String message) implements Intent {}

    static SearchState reduce(SearchState s, Intent intent) {           // (3)!
        return switch (intent) {
            case QueryChanged q -> new SearchState(q.text(), !q.text().isBlank(), List.of(), null);
            case ResultsLoaded r -> r.forQuery().equals(s.query())
                    ? new SearchState(s.query(), false, r.items(), null)
                    : s;                                                 // stale response for an old query: ignore
            case LoadFailed f -> f.forQuery().equals(s.query())
                    ? new SearchState(s.query(), false, List.of(), f.message())
                    : s;
        };
    }

    static String render(SearchState s) {
        if (s.loading()) return "[spinner] searching '" + s.query() + "'";
        if (s.error() != null) return "[error] " + s.error();
        return "[list] " + s.results();
    }

    public static void main(String[] args) {
        List<Intent> intents = List.of(
                new QueryChanged("pan"),
                new QueryChanged("paneer"),
                new ResultsLoaded("pan", List.of("Pan masala", "Pancake mix")),   // late answer for the old query
                new ResultsLoaded("paneer", List.of("Paneer 200g", "Paneer tikka kit")),
                new QueryChanged("xyz"),
                new LoadFailed("xyz", "network timeout"));
        SearchState state = SearchState.initial();
        for (Intent i : intents) {
            state = reduce(state, i);
            System.out.println(i.getClass().getSimpleName() + " -> " + render(state));
        }
    }
}

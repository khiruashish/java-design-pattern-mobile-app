import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** A transition table: the whole state machine as data. Easy to print, review, validate and test. */
public class TransitionTableDemo {

    enum S { PLACED, PACKED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED, RETURNED, REFUNDED }   // REFUNDED: someone forgot its transition
    enum E { PACK, DISPATCH, DELIVER, CANCEL, RETURN }

    static final Map<S, Map<E, S>> TABLE = new EnumMap<>(S.class);                              // (1)!
    static void on(S from, E event, S to) { TABLE.computeIfAbsent(from, k -> new EnumMap<>(E.class)).put(event, to); }
    static {
        on(S.PLACED, E.PACK, S.PACKED);
        on(S.PLACED, E.CANCEL, S.CANCELLED);
        on(S.PACKED, E.DISPATCH, S.OUT_FOR_DELIVERY);
        on(S.PACKED, E.CANCEL, S.CANCELLED);
        on(S.OUT_FOR_DELIVERY, E.DELIVER, S.DELIVERED);
        on(S.DELIVERED, E.RETURN, S.RETURNED);
    }

    static S fire(S state, E event) {
        S next = TABLE.getOrDefault(state, Map.of()).get(event);                                 // (2)!
        if (next == null) throw new IllegalStateException(event + " not allowed in " + state);
        return next;
    }

    static Set<S> reachableFrom(S start) {                                                       // (3)!
        Set<S> seen = EnumSet.of(start); ArrayDeque<S> todo = new ArrayDeque<>(Set.of(start));
        while (!todo.isEmpty()) for (S n : TABLE.getOrDefault(todo.pop(), Map.of()).values()) if (seen.add(n)) todo.push(n);
        return seen;
    }

    public static void main(String[] args) {
        TABLE.forEach((from, row) -> row.forEach((ev, to) -> System.out.println(String.format("%-17s --%-8s--> %s", from, ev, to))));
        System.out.println("fire PLACED+PACK = " + fire(S.PLACED, E.PACK));
        try { fire(S.DELIVERED, E.CANCEL); } catch (IllegalStateException e) { System.out.println(e.getMessage()); }
        System.out.println("validation, unreachable states: " + EnumSet.complementOf(EnumSet.copyOf(reachableFrom(S.PLACED))));
    }
}

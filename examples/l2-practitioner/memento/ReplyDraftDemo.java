/** Minimal Memento: a support agent's reply box can save a draft and restore it after a mistaken edit. */
public class ReplyDraftDemo {

    static final class ReplyBox {
        private String text = ""; private int cursor;
        record Draft(String text, int cursor) { }                                                   // (1)!
        void type(String s) { text = text.substring(0, cursor) + s + text.substring(cursor); cursor += s.length(); }
        void selectAllAndDelete() { text = ""; cursor = 0; }
        Draft save() { return new Draft(text, cursor); }
        void restore(Draft d) { text = d.text(); cursor = d.cursor(); }                              // (2)!
        public String toString() { return "'" + text + "' (cursor " + cursor + ")"; }
    }

    public static void main(String[] args) {
        ReplyBox box = new ReplyBox();
        box.type("Sorry about the late delivery. ");
        ReplyBox.Draft draft = box.save();
        box.selectAllAndDelete();
        System.out.println("after accident: " + box);
        box.restore(draft);
        box.type("We've refunded the delivery fee.");
        System.out.println("restored:       " + box);
    }
}

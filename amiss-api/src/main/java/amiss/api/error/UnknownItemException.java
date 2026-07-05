package amiss.api.error;

/** Thrown when an eat/groceries/clothes request names an item outside its catalog enum (→ 400). */
public class UnknownItemException extends RuntimeException {

    public UnknownItemException(String item) {
        super("Unknown item: '" + item + "'");
    }
}

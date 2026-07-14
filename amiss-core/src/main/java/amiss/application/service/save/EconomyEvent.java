package amiss.application.service.save;

/**
 * What the economy did at one rollover (KAN-48, wiki Market Crash / Economic Boom
 * pages). {@code NONE} for the ordinary drift-only week. Crash severities are the
 * wiki's, uniform: MINOR (prices -5%), MODERATE (prices -10%, 50% fired else pay cut
 * to 80%), MAJOR (prices -15%, fired, bank wiped). Happiness loss 1/2/3 by severity.
 * A boom is a single +10% jolt. The wiki's stock effects wait for KAN-49.
 */
public record EconomyEvent(Type type, Severity severity, boolean fired,
        Integer wageCutTo, boolean bankWiped, int happinessLost) {

    public enum Type { NONE, BOOM, CRASH }

    public enum Severity { MINOR, MODERATE, MAJOR }

    public static EconomyEvent none() {
        return new EconomyEvent(Type.NONE, null, false, null, false, 0);
    }
}

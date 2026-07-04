package amiss.api.web.dto;

/**
 * The player's current job. For {@code "Unemployed"} the wage/location have no
 * {@code tbljobs} row, so both are reported as {@code null} rather than the
 * adapter's raw {@code -1}/{@code null} sentinels.
 */
public record JobDto(String name, Integer hourlyWage, String location) {
}

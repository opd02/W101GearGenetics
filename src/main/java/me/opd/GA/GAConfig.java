package me.opd.GA;
/** Per-run settings. Defaults retain the existing local population/mutation/tournament choices. */
public class GAConfig {
    public long seed = 1;
    public int populationSize = 250, generations = 100, tournamentSize = 7, elites = 2;
    public double mutationRate = 0.5, crossoverRate = 0.7;
    public enum Method { GA, EXACT }
    public Method method = Method.GA;
    public long exactLimit = 100000;
    public void validate() {
        if (populationSize < 2 || generations < 0 || tournamentSize < 1 || elites < 1 || elites >= populationSize
                || !Double.isFinite(mutationRate) || mutationRate < 0 || mutationRate > 1
                || !Double.isFinite(crossoverRate) || crossoverRate < 0 || crossoverRate > 1 || exactLimit < 1 || method == null)
            throw new IllegalArgumentException("Invalid search settings: population >=2, 1<=elites<population, rates 0..1");
    }
}

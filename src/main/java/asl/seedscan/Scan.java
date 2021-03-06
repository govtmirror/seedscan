package asl.seedscan;

import java.util.ArrayList;

import asl.seedscan.metrics.MetricWrapper;

public class Scan {
	private String scanName = null;
	private String pathPattern;
	private String datalessDir;
	private String eventsDir;
	private String plotsDir;
	private int startDay;
	private int daysToScan;
	private int startDate;
	private ArrayList<MetricWrapper> metrics;

	private ScanFilter networks = null;
	private ScanFilter stations = null;
	private ScanFilter locations = null;
	private ScanFilter channels = null;

	public Scan(String scanName) {
		this.scanName = scanName;
		metrics = new ArrayList<MetricWrapper>();
	}

	public String getName() {
		return scanName;
	}

	// (seed file) path pattern
	public void setPathPattern(String pathPattern) {
		this.pathPattern = pathPattern;
	}

	public String getPathPattern() {
		return pathPattern;
	}

	// dataless seed dir
	public void setDatalessDir(String datalessDir) {
		this.datalessDir = datalessDir;
	}

	public String getDatalessDir() {
		return datalessDir;
	}

	public void setPlotsDir(String plotsDir) {
		this.plotsDir = plotsDir;
	}

	public String getPlotsDir() {
		return plotsDir;
	}

	public void setEventsDir(String eventsDir) {
		this.eventsDir = eventsDir;
	}

	public String getEventsDir() {
		return eventsDir;
	}

	// metrics
	public void addMetric(MetricWrapper metric) {
		metrics.add(metric);
	}

	public MetricWrapper getMetric(int index) throws IndexOutOfBoundsException {
		return metrics.get(index);
	}

	public ArrayList<MetricWrapper> getMetrics() {
		return metrics;
	}

	public boolean removeMetric(MetricWrapper metric) {
		return metrics.remove(metric);
	}

	public MetricWrapper removeMetric(int index)
			throws IndexOutOfBoundsException {
		return metrics.remove(index);
	}

	public void clearMetrics() {
		metrics.clear();
	}

	public void setStartDate(int startDate) {
		this.startDate = startDate;
	}

	// start depth
	public void setStartDay(int startDay) {
		this.startDay = startDay;
	}

	public int getStartDay() {
		return startDay;
	}

	public int getStartDate() {
		return startDate;
	}

	// scan depth
	public void setDaysToScan(int daysToScan) {
		this.daysToScan = daysToScan;
	}

	public int getDaysToScan() {
		return daysToScan;
	}

	// network filter
	public void setNetworks(ScanFilter networks) {
		this.networks = networks;
	}

	public ScanFilter getNetworks() {
		return networks;
	}

	// station filter
	public void setStations(ScanFilter stations) {
		this.stations = stations;
	}

	public ScanFilter getStations() {
		return stations;
	}

	// location filter
	public void setLocations(ScanFilter locations) {
		this.locations = locations;
	}

	public ScanFilter getLocations() {
		return locations;
	}

	// channel filter
	public void setChannels(ScanFilter channels) {
		this.channels = channels;
	}

	public ScanFilter getChannels() {
		return channels;
	}
}

/*
 * Copyright 2011, United States Geological Survey or
 * third-party contributors as indicated by the @author tags.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/  >.
 *
 */

package asl.metadata;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Channel {
	private static final Logger logger = LoggerFactory
			.getLogger(asl.metadata.Channel.class);

	private String location = null;
	private String channel = null;

	/**
	 * Instantiates a new channel.
	 *
	 * @param location the location
	 * @param channel the channel
	 */
	public Channel(String location, String channel) {
		setLocation(location);
		try {
			setChannel(channel);
		} catch (ChannelException e) {
			logger.error("ChannelException:", e);
		}
	}

	/**
	 * Static methods to validate channel naming and location. Individuaally
	 * validate Band, Instrument and Orientation Codes as per SEEDManual v2.4
	 * Appendix A.
	 * 
	 * These will only be useful to validate original, SEISMIC channels (e.g.,
	 * "VHZ") but will trip over derived channels (e.g., "00-10, LHND-LHND") and
	 * non-seismic channels (e.g., LDF)
	 */
	public static boolean validLocationCode(String location) {
		if (location.length() != 2) {
			return false;
		}
		// Allow locations = {"00", "10", "20", ..., "99" and "--"}
		Pattern pattern = Pattern.compile("^[0-9][0-9]$");
		Matcher matcher = pattern.matcher(location);
		if (!matcher.matches() && !location.equals("--")) {
			return false;
		}
		return true;
	}

	public static boolean validBandCode(String band) {
		if (band.length() != 1) {
			return false;
		}
		Pattern pattern = Pattern
				.compile("[F,G,D,C,E,S,H,B,M,L,V,U,R,P,T,Q,A,O]");
		Matcher matcher = pattern.matcher(band);
		if (!matcher.matches()) {
			return false;
		}
		return true;
	}

	public static boolean validInstrumentCode(String instrument) {
		if (instrument.length() != 1) {
			return false;
		}
		Pattern pattern = Pattern.compile("[H,L,G,M,N,D,F,I,K,R,W,C,E]");
		Matcher matcher = pattern.matcher(instrument);
		if (!matcher.matches()) {
			return false;
		}
		return true;
	}
	
	/**
	 * Returns true if the channelFlags indicate a continuous channel
	 * @param channelFlags
	 * @return true if continuous, false otherwise
	 */
	public static boolean continousChannel(String channelFlags){
		return channelFlags.substring(0, 1).equals("C")
				|| channelFlags.equals("G") || channelFlags.equals("H");
	}

	public static boolean validOrientationCode(String orientation) {
		if (orientation.length() != 1) {
			return false;
		}
		Pattern pattern = Pattern.compile("[1,2,3,N,E,Z,U,V,W]");
		Matcher matcher = pattern.matcher(orientation);
		if (!matcher.matches()) {
			return false;
		}
		return true;
	}

	// channel setter method(s)

	private void setLocation(String location) {
		if (location != null) {
			// Not sure how we want to validate since CoherencePBM for instance,
			// calls
			// Metric.createIdentifier --> MetricResult.createChannel --> new
			// Channel ("00-10", ...)
			// if (!validLocationCode(location)) {
			// throw new
			// RuntimeException("Channel.setLocation: ERROR INVALID LOCATION CODE="
			// + location);
			// }
			this.location = location;
		} else {
			this.location = "--"; // If no location given, set location = "--"
									// [Default]
		}
	}

	public void setChannel(String channel) throws ChannelException {
		if (channel == null) {
			throw new ChannelException("channel cannot be null");
		}
		// Most channels should be exactly 3-chars long (e.g., LH1), however,
		// derived
		// channels (e.g., LHND) will be 4-chars and maybe/probably there will
		// be others
		// e.g., MetricResult.createChannel ( new Channel("00-10" , "LHND-LHND")
		// ) ...
		if (channel.length() < 3) {
			throw new ChannelException(
					"channel name MUST be at least 3-chars long");
		}
		this.channel = channel;
	}

	@Override
	public String toString() {
		return getLocation() + "-" + getChannel();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((channel == null) ? 0 : channel.hashCode());
		result = prime * result + ((location == null) ? 0 : location.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Channel other = (Channel) obj;
		if (channel == null) {
			if (other.channel != null)
				return false;
		} else if (!channel.equals(other.channel))
			return false;
		if (location == null) {
			if (other.location != null)
				return false;
		} else if (!location.equals(other.location))
			return false;
		return true;
	}

	public String getLocation() {
		return location;
	}

	public String getChannel() {
		return channel;
	}
}

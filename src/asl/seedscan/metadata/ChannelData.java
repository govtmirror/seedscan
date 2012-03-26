/*
 * Copyright 2012, United States Geological Survey or
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
package asl.seedscan.metadata;

import java.util.Calendar;
import java.util.Hashtable;

public class ChannelData
{
    private Hashtable<Calendar, Blockette> comments;
    private Hashtable<Calendar, EpochData> epochs;

    public ChannelData()
    {
        comments = new Hashtable<Calendar, Blockette>();
        epochs = new Hashtable<Calendar, EpochData>();
    }

    public void addComment(Calendar timestamp, Blockette blockette)
    {
        comments.put(timestamp, blockette);
    }

    public void addEpoch(Calendar timestamp, EpochData data)
    {
        epochs.put(timestamp, data);
    }
}


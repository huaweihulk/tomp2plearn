/*
 * Copyright 2011 Thomas Bocek
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package trunk.social.p2p.connection;

import java.util.Random;

/**
 * Stores port information.
 * 
 * @author Thomas Bocek
 */
public class Ports {
   
    // The maximal port number, 2^16.
    public static final int MAX_PORT = 65535;
    //IANA recommends to use ports higher or equal 49152.
    public static final int MIN_DYN_PORT = 49152;
    // The default port of TomP2P.
    public static final int DEFAULT_PORT = 7700;

    private static final int RANGE = MAX_PORT - MIN_DYN_PORT;
    private static final Random RND = new Random();

    // provide this information if you know your mapping beforehand
    // i.e., manual port-forwarding
    private final int tcpPort;
    private final int udpPort;
    private final int udtPort;
    private final boolean randomPorts;

    /**
     * Creates random ports for TCP and UDP. The random ports start from port 49152
     */
    public Ports() {
    	this.tcpPort = RND.nextInt(RANGE) + MIN_DYN_PORT;
        this.udpPort = RND.nextInt(RANGE) + MIN_DYN_PORT;
        this.udtPort = RND.nextInt(RANGE) + MIN_DYN_PORT;
        this.randomPorts = true;
    }

    /**
     * Creates a Ports class that stores port information.
     * @param tcpPort The external TCP port, how other peers will see us. If the provided port is < 0, a random port will be used.
     * @param udpPort The external UDP port, how other peers will see us. If the provided port is < 0, a random port will be used.
     */
    public Ports(final int tcpPort, final int udpPort, final int udtPort) {
    	if(tcpPort < 1 || udpPort < 1 || udtPort < 1) {
    		throw new IllegalArgumentException("manual ports need to be > 1. TCP: "+tcpPort+", UDP:"+udpPort+", UDT:"+udtPort);
    	}
    	this.tcpPort = tcpPort;
        this.udpPort = udpPort;
        this.udtPort = udtPort;
        this.randomPorts = false;
    }

    /**
     * @return The external TCP port, how other peers see us.
     */
    public int tcpPort() {
        return tcpPort;
    }

    /**
     * @return The external UDP port, how other peers see us.
     */
    public int udpPort() {
        return udpPort;
    }
    
    /**
     * @return The external UDP port, how other peers see us.
     */
    public int udtPort() {
        return udtPort;
    }

    /**
     * @return True, if the user specified both ports in advance. This tells us
     *         that the user knows about the ports and did a manual
     *         port-forwarding.
     */
    public boolean isManualPort() {
        // set setExternalPortsManually to true if the user specified both ports
        // in advance. This tells us that the user knows about the ports and did
        // a manual port-forwarding.
        return !randomPorts;
    }
    
    @Override
    public String toString() {
    	final StringBuilder sb = new StringBuilder("ports(udp:");
    	sb.append(udpPort).append(",tcp:").append(tcpPort).append(",udt:").append(udtPort).append(")");
    	return sb.toString();
    }
}

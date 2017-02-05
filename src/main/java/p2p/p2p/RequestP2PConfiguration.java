/*
 * Copyright 2009 Thomas Bocek
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
package p2p.p2p;


/**
 * This name was chosen over P2PConfiguration, as it already exists.
 * 
 * @author Thomas Bocek
 */
public class RequestP2PConfiguration implements RequestConfiguration {
    final private int minimumResults;

    final private int maxFailure;

    final private int parallelDiff;

    // set to force either UDP or TCP
    final private boolean forceUDP;

    final private boolean forceTCP;

    public RequestP2PConfiguration(int minimumResults, int maxFailure, int parallelDiff) {
        this(minimumResults, maxFailure, parallelDiff, false, false);
    }

    /**
     * Sets the P2P/DHT configuration and its stop conditions. Based on the message size, either UDP or TCP is used.
     * 
     * @param minimumResults
     *            Stops the direct calls if m peers have been contacted
     * @param maxFailure
     *            Stops the direct calls if f peers have failed
     * @param parallelDiff
     *            Use parallelDiff + minimumResults parallel connections for the P2P/DHT operation
     * @param forceUDP
     *            Flag to indicate that routing should be done with UDP instead of TCP
     * @param forceTCP
     *            Flag to indicate that routing should be done with TCP instead of UDP
     */
    public RequestP2PConfiguration(final int minimumResults, final int maxFailure, final int parallelDiff,
            final boolean forceUDP, final boolean forceTCP) {
        if (minimumResults < 0 || maxFailure < 0 || parallelDiff < 0) {
            throw new IllegalArgumentException("Some values need to be larger or equal to zero.");
        }
        this.minimumResults = minimumResults;
        this.maxFailure = maxFailure;
        this.parallelDiff = parallelDiff;
        this.forceUDP = forceUDP;
        this.forceTCP = forceTCP;
    }

    public RequestP2PConfiguration adjustMinimumResult(int minimumResultsLow) {
        return new RequestP2PConfiguration(Math.min(minimumResultsLow, minimumResults), maxFailure, parallelDiff,
                forceUDP, forceTCP);
    }

    public int minimumResults() {
        return minimumResults;
    }

    public int maxFailure() {
        return maxFailure;
    }

    public int parallelDiff() {
        return parallelDiff;
    }

    /* (non-Javadoc)
	 * @see net.tomp2p.p2p.RequestConfiguration#parallel()
	 */
    @Override
    public int parallel() {
        return minimumResults + parallelDiff;
    }

    /* (non-Javadoc)
	 * @see net.tomp2p.p2p.RequestConfiguration#isForceUPD()
	 */
    @Override
    public boolean isForceUPD() {
        return forceUDP;
    }

    /* (non-Javadoc)
	 * @see net.tomp2p.p2p.RequestConfiguration#isForceTCP()
	 */
    @Override
    public boolean isForceTCP() {
        return forceTCP;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("minRes=");
        sb.append(minimumResults);
        sb.append("maxFail=");
        sb.append(maxFailure);
        sb.append("pDiff=");
        sb.append(parallelDiff);
        return sb.toString();
    }
}

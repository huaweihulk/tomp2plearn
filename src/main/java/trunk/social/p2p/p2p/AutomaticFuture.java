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
package trunk.social.p2p.p2p;

import trunk.social.p2p.futures.BaseFuture;

/**
 * Use this interface to notify if a future has been generated from a maintenance task.
 * 
 * @author Thomas Bocek
 * 
 */
public interface AutomaticFuture {

    /**
     * Call this method when a future has been created without any user interaction.
     * 
     * @param future
     *            The future that was created by TomP2P
     */
    void futureCreated(BaseFuture future);
}

/*
 * Copyright 2012 Thomas Bocek
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

package trunk.social.p2p.futures;

import trunk.social.p2p.message.Message;

/**
 * Evaluates if a future is a success. Depending if it is a routing request or p2p requests, different success is
 * expected.
 * 
 * @author Thomas Bocek
 * 
 */
public interface FutureSuccessEvaluator {
    /**
     * Evaluates if a request is a success.
     * 
     * @param requestMessage
     *            The request message
     * @param responseMessage
     *            The response message
     * @return The future type if the request was successful
     */
    BaseFuture.FutureType evaluate(Message requestMessage, Message responseMessage);
}
